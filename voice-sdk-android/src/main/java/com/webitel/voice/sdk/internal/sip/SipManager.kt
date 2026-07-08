package com.webitel.voice.sdk.internal.sip

import android.util.Log
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.CallSettings.TransportUse
import com.webitel.voice.sdk.LogLevel
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.VideoQuality
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import org.pjsip.PjCameraInfo2
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.CodecFmtp
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.RtcpFbCap
import org.pjsip.pjsua2.SipHeader
import org.pjsip.pjsua2.SipHeaderVector
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pj_log_decoration
import org.pjsip.pjsua2.pj_qos_type
import org.pjsip.pjsua2.pjmedia_rtcp_fb_type.PJMEDIA_RTCP_FB_NACK
import org.pjsip.pjsua2.pjsip_transport_type_e


internal class SipManager {

    private var endpoint: PJEndpoint? = null
    private var account: PJAccount? = null
    private var isDestroying: Boolean = false
    private var logWriter = SipLogWriter()

    private var callSettings = CallSettings()

    companion object {
        @Volatile
        private var isLibraryLoaded = false
    }


    @Synchronized
    fun configure(settings: CallSettings) {
        callSettings = settings
    }


    @Synchronized
    fun makeCall(callOptions: CallOptions, sipConfig: SipConfig, callbacks: SipCallCallbacks): PJCall {
        loadNativeLibraries()
        checkThread()
        if (isDestroying) {
            forceRelease()
        }

        val transport = parseTransport(sipConfig.proxy?: "")
            ?: callSettings.transport

        val e = endpoint ?: createEndpoint(
            transport,
            quality = callOptions.videoQuality,
            orientation = callOptions.videoOrientation
        )

        val a = account ?: createAccount(callSettings, sipConfig)

        val pjCall = PJCall(a, e, callbacks)

        val callOpParam = CallOpParam()
        var shv: SipHeaderVector? = null
        var sh: SipHeader? = null
        try {
            if (!callOptions.meetingId.isNullOrEmpty()) {
                shv = SipHeaderVector()
                sh = SipHeader()
                sh.hName = "X-Webitel-Meeting"
                sh.hValue = callOptions.meetingId
                shv.add(sh)
                callOpParam.getTxOption().headers = shv
            }

            callOpParam.opt.videoCount = callOptions.type.code
            callOpParam.opt.audioCount = 1

            val remoteUri = a.config.getSipUri(callOptions.toNumber, callOptions.toName)

            try {
                pjCall.makeCall(remoteUri, callOpParam)
            } catch (e: Exception) {
                try { pjCall.delete() } catch (_: Exception) {}
                throw e
            }

        } finally {
            try { sh?.delete() } catch (_: Exception) {}
            try { shv?.delete() } catch (_: Exception) {}
            try { callOpParam.delete() } catch (_: Exception) {}
        }

        return pjCall
    }


    @Synchronized
    fun shutdownStack(fullShutdown: Boolean = true) {
        if (isDestroying) return
        isDestroying = true

        try {
            checkThread()
            val acc = account
            val ep = endpoint

            ep?.hangupAllCalls()

            acc?.shutdown()
            acc?.delete()

            if (fullShutdown) {
                ep?.libDestroy()
                ep?.delete()
            }

        } catch (ex: Exception) {
            logger.error("SipManager",
                "shutdownStack: (full=$fullShutdown) failed: ${ex.message}"
            )
        } finally {
            if (fullShutdown) {
                endpoint = null
            }
            account = null
            isDestroying = false
        }
    }


    /**
     * Updates the video encoding orientation mid-call without a SIP re-INVITE.
     * Called by VoiceManager when [Call.setVideoOrientation] is invoked.
     */
    fun updateVideoOrientation(quality: VideoQuality, orientation: VideoOrientation, isBackCamera: Boolean) {
        val ep = endpoint ?: return
        checkThread()
        val codecId = findH264CodecId(ep) ?: return

        applyVideoQualitySafe(ep, codecId, quality, orientation)
        applyCameraOrientations(ep, orientation, isBackCamera)
        // setCaptureOrient(-1, …, keep=true) is intentionally NOT called here.
        // On Camera2 devices that return PJ_ENOTFOUND for PJMEDIA_VID_DEV_CAP_ORIENTATION,
        // the call triggers a native async callback in DefaultDispatch that races against
        // PJSIP's internal state → SIGSEGV. Camera2 devices handle sensor rotation
        // automatically at the framework level, so the encoder format update above is enough.
    }


    private fun createEndpoint(
        transport: TransportUse,
        quality: VideoQuality = VideoQuality.DEFAULT,
        orientation: VideoOrientation = VideoOrientation.PORTRAIT
    ): PJEndpoint {
        val point = PJEndpoint()
        point.libCreate()

        val epConfig = getEndpointConfig()
        try {
            point.libInit(epConfig)
        } finally {
            try { epConfig.delete() } catch (_: Exception) {}
        }
        point.libRegisterThread(Thread.currentThread().name)
        setTransportConfig(transport, point)

        point.libStart()
        endpoint = point
        applyCameraOrientations(point, orientation, activeIsBack = false) // account defaults to the front camera

        val codecId = findH264CodecId(point) ?: "H264/"

        applyVideoQualitySafe(point, codecId, quality, orientation)

        point.audDevManager()?.setOutputVolume(100, true)
        point.audDevManager()?.setInputVolume(100, true)
        return point
    }


    private fun findH264CodecId(ep: PJEndpoint): String? {
        val codecs = ep.videoCodecEnum2()
        return try {
            codecs.firstOrNull { it.codecId.startsWith("H264/") }?.codecId
        } finally {
            try { codecs.delete() } catch (_: Exception) {}
        }
    }


    /**
     * Sets capture orientation for each enumerable camera device individually.
     *
     * Deliberately avoids `setCaptureOrient(-1, …)` (the global/all-devices form).
     * On some Camera2 devices that return PJ_ENOTFOUND for PJMEDIA_VID_DEV_CAP_ORIENTATION,
     * the global call triggers a native async callback in DefaultDispatch that races against
     * PJSIP internal state → SIGSEGV. Per-device calls with individual try/catch are safe.
     *
     * Camera2 handles sensor rotation automatically at the framework level, so this function
     * is a best-effort helper: if a device rejects the call, the framework fallback takes over.
     */
    private fun applyCameraOrientations(ep: PJEndpoint, orientation: VideoOrientation, activeIsBack: Boolean) {
        // Pushes the current device rotation to PjCamera2 for the Java-side compensation
        // (local preview transform + safe 180deg encode-frame flip for the back camera).
        // Kept alongside setCaptureOrient below, which is a no-op on real Camera2 devices
        // but harmless to leave in place.
        //PjCamera2.SetDeviceRotationDegrees(orientation.rotation * 90)

        val vidMgr = ep.vidDevManager()
        val devs = try {
            vidMgr.enumDev2()
        } catch (e: Exception) {
            logger.error("SipManager", "applyCameraOrientations: ${e.message}")
            return
        }
        try {
            // enumDev2() also returns non-camera pjproject devices ("OpenGL renderer",
            // "Colorbar generator", ...) whose own name-derived isBack is meaningless.
            // `activeIsBack` is passed in by the caller (WebitelCall tracks the real active
            // camera id itself — VidDevManager.isCaptureActive() turned out unreliable for
            // this, it always reported the front camera as active).
            val orient = CameraOrientation.forDeviceRotation(orientation.rotation, activeIsBack)

            devs.forEach { dev ->

                try {
                    // DIAGNOSTIC: temporarily disabled to isolate a shrink+wrong-rotation bug
                    // seen only for the back camera in landscape, now that this call finally
                    // reaches the real "Back camera" device (post cross-talk fix) instead of
                    // silently missing it. If disabling this makes the bug disappear, the
                    // native Camera2 backend is doing a broken crop/scale for non-NATURAL
                    // pjmedia_orient values on this device — see plan file for next steps.
                     vidMgr.setCaptureOrient(dev.id, orient, true)
                    Log.d("SipManager", "applyCameraOrientations: id=${dev.id} name=${dev.name} activeIsBack=$activeIsBack orient=$orient")
                } catch (e: Exception) {
                    Log.e("SipManager", "applyCameraOrientations: FAILED id=${dev.id} name=${dev.name} activeIsBack=$activeIsBack orient=$orient error=${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("SipManager", "applyCameraOrientations: ${e.message}")
        } finally {
            try { devs.delete() } catch (_: Exception) {}
        }
    }


    fun applyVideoQualitySafe(
        point: PJEndpoint,
        codecId: String,
        quality: VideoQuality,
        orientation: VideoOrientation = VideoOrientation.PORTRAIT
    ): Result<Unit> {
        // Portrait / portrait-reversed: height > width (9:16).
        // Landscape left/right: swap → width > height (16:9).
        val encW = if (orientation.isLandscape) quality.height else quality.width
        val encH = if (orientation.isLandscape) quality.width else quality.height

        return try {
            val param = point.getVideoCodecParam(codecId)
            try {
                param.encFmt.width = encW
                param.encFmt.height = encH
                param.encFmt.fpsNum = quality.fps
                param.encFmt.fpsDenum = 1
                param.encFmt.avgBps = quality.avgBitrate
                param.encFmt.maxBps = quality.maxBitrate

                // Decoder buffer must fit ANY remote resolution regardless of orientation.
                // Using the square max of quality dimensions prevents "not enough buffer" crashes
                // when the remote sends a different orientation (e.g., landscape from web browser).
                val decMax = maxOf(quality.width, quality.height)
                param.decFmt.width = decMax
                param.decFmt.height = decMax
                param.decFmt.fpsNum = quality.fps
                param.decFmt.fpsDenum = 1

                if (codecId.contains("H264", ignoreCase = true)) {
                    param.decFmtp.clear()
                    param.encFmtp.clear()
                    listOf(
                        "profile-level-id" to "42e01f",
                        "packetization-mode" to "1"
                    ).forEach { (n, v) ->
                        param.decFmtp.add(CodecFmtp().apply { name = n; `val` = v })
                        param.encFmtp.add(CodecFmtp().apply { name = n; `val` = v })
                    }
                }

                point.setVideoCodecParam(codecId, param)
            } finally {
                try { param.delete() } catch (_: Exception) {}
            }

            Log.d("SipManager", "Video quality applied for $codecId: ${encW}x${encH} " +
                    "@ ${quality.fps}fps (${quality.avgBitrate/1000}kbps avg, $orientation)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SipManager", "applyVideoQualitySafe error: ${e.localizedMessage}")
            Result.failure(e)
        }
    }


    private fun createAccount(settings: CallSettings, config: SipConfig): PJAccount {
        val accountConfig = getAccountConfig(config, settings)
         enableRtcpFeedback(accountConfig)
        val acc = PJAccount(config)

        try {
            acc.create(accountConfig, true)
        } finally {
            try { accountConfig.delete() } catch (_: Exception) {}
        }
        account = acc
        return acc
    }


    private fun enableRtcpFeedback(accCfg: AccountConfig) {
        try {
            accCfg.mediaConfig.rtcpFbConfig.apply {
                dontUseAvpf = false
                caps.add(RtcpFbCap().apply {
                    codecId = "H264"
                    type = PJMEDIA_RTCP_FB_NACK   // generic NACK
                })
                caps.add(RtcpFbCap().apply {
                    codecId = "H264"
                    type = PJMEDIA_RTCP_FB_NACK
                    param = "pli"                 // Picture Loss Indication
                })
            }
            Log.d("SipManager", "RTCP feedback (NACK+PLI) enabled for H264")
        } catch (e: Exception) {
            Log.e("SipManager", "enableRtcpFeedback error: ${e.localizedMessage}")
        }
    }


    private fun getAccountConfig(config: SipConfig, settings: CallSettings): AccountConfig {
        val idUri = "sip:" + config.extension +
                "@" + config.domain

        val accountConfig = AccountConfig()
        accountConfig.priority = 100
        accountConfig.idUri = idUri

        accountConfig.sipConfig.proxies.clear()
        accountConfig.sipConfig.proxies.add(config.getProxy())

        accountConfig.regConfig.proxyUse = 1
        accountConfig.regConfig.registerOnAdd = false

        accountConfig.natConfig.mediaStunUse = settings.natMediaStunUse.value
        accountConfig.natConfig.sipStunUse = settings.natSipStunUse.value
        accountConfig.natConfig.iceEnabled = settings.natIceEnabled
        accountConfig.natConfig.sdpNatRewriteUse = if(settings.natSdpNatRewriteUse) 1 else 0
        accountConfig.natConfig.contactRewriteUse = if(settings.natContactRewriteUse) 1 else 0
        accountConfig.natConfig.viaRewriteUse = if(settings.natViaRewriteUse) 1 else 0

        accountConfig.videoConfig.autoShowIncoming = true
        accountConfig.videoConfig.autoTransmitOutgoing = true

        accountConfig.mediaConfig.transportConfig.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
        accountConfig.mediaConfig.srtpUse = settings.srtpUse.value
        accountConfig.mediaConfig.srtpSecureSignaling = 0

        val cred = AuthCredInfo(
            "digest",
            "*",
            config.auth,
            0,
            config.password
        )
        try {
            accountConfig.sipConfig.authCreds.clear()
            accountConfig.sipConfig.authCreds.add(cred)
        } finally {
            try { cred.delete() } catch (_: Exception) {}
        }

        return accountConfig
    }


    private fun getEndpointConfig(): EpConfig {
        val epConfig = EpConfig()
        epConfig.uaConfig.maxCalls = 32
        epConfig.uaConfig.userAgent = "webitel for android"
        epConfig.medConfig.hasIoqueue = true
        epConfig.medConfig.clockRate = 16000
        epConfig.medConfig.quality = 10
        // threadCnt = 1: with 2+ media worker threads, PJSIP can process queued vid_conf
        // port-removal jobs for the same call's port graph on two threads concurrently
        // during teardown (observed as SIGSEGV/SIGBUS in vid_conf.c/vid_port.c ~150-200ms
        // after a call disconnects, on whichever worker thread picks up the racing job).
        // A single worker thread serializes all queued media/conference teardown, removing
        // that race at the source instead of trying to out-time it from the Kotlin side.
        epConfig.medConfig.threadCnt = 1
        epConfig.medConfig.ecOptions = 0
        epConfig.medConfig.ecTailLen = 0
        epConfig.medConfig.noVad = true
        epConfig.medConfig.audioFramePtime = 40

        setLogConfig(epConfig)

        return epConfig
    }


    private fun parseTransport(proxy: String): TransportUse? {
        val transport = Regex("""transport=([a-zA-Z]+)""")
            .find(proxy)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()

        return when (transport) {
            "UDP" -> TransportUse.UDP
            "TCP" -> TransportUse.TCP
            "TLS" -> TransportUse.TLS
            else -> null
        }
    }


    private fun setTransportConfig(transport: TransportUse, point: PJEndpoint) {
        when (transport) {
            TransportUse.UDP -> createTransportUDP(point)
            TransportUse.TCP -> createTransportTCP(point)
            TransportUse.TLS -> createTransportTLS(point)
            TransportUse.TCP_UDP -> {
                createTransportUDP(point)
                createTransportTCP(point)
            }
        }
    }


    private fun createTransportUDP(point: PJEndpoint) {
        val cfg = TransportConfig()
        try {
            cfg.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
            point.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, cfg)
        } finally {
            try { cfg.delete() } catch (_: Exception) {}
        }
    }


    private fun createTransportTCP(point: PJEndpoint) {
        val cfg = TransportConfig()
        try {
            cfg.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
            point.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, cfg)
        } finally {
            try { cfg.delete() } catch (_: Exception) {}
        }
    }


    private fun createTransportTLS(point: PJEndpoint) {
        val cfg = TransportConfig()
        try {
            cfg.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
            point.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, cfg)
        } finally {
            try { cfg.delete() } catch (_: Exception) {}
        }
    }


    private fun forceRelease() {
        logger.debug("SipManager", "forceRelease called")
        endpoint = null
        account = null
        isDestroying = false
    }


    private fun loadNativeLibraries() {
        if (isLibraryLoaded) {
            logger.debug("SipManager", "loadNativeLibraries: Libraries already loaded")
            return
        }
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("pjsua2")
            isLibraryLoaded = true
            logger.debug("SipManager",
                "loadNativeLibraries: c++_shared and pjsua2 loaded"
            )
        } catch (error: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            logger.error("SipManager",
                "loadNativeLibraries: Error loading libraries - ${error}"
            )
        }
    }


    private fun setLogConfig(epConfig: EpConfig) {
        try { logWriter.delete() } catch (_: Exception) {}
        logWriter = SipLogWriter()
        epConfig.logConfig.level = if (logger.level == LogLevel.DEBUG) 4 else 5
        epConfig.logConfig.consoleLevel = if (logger.level == LogLevel.DEBUG) 4 else 5

        val logCfg = epConfig.logConfig
        logCfg.writer = logWriter
        logCfg.decor =
            logCfg.decor and (
                    pj_log_decoration.PJ_LOG_HAS_CR.toLong()
                            or pj_log_decoration.PJ_LOG_HAS_NEWLINE.toLong()
                    )
                .inv()
    }


    @Synchronized
    private fun checkThread() {
        try {
            if (endpoint != null && endpoint?.libIsThreadRegistered() == false) {
                endpoint?.libRegisterThread(
                    Thread.currentThread().name
                )
            }
        } catch (e: Exception) {
            logger.error("SipManager", "checkThread:  ${e.message}")
        }
    }
}