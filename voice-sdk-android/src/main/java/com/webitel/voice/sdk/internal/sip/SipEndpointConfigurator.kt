package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.CallSettings.TransportUse
import com.webitel.voice.sdk.LogLevel
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.VideoQuality
import com.webitel.voice.sdk.internal.support.SdkLogger
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.CodecFmtp
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.RtcpFbCap
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pj_log_decoration
import org.pjsip.pjsua2.pj_qos_type
import org.pjsip.pjsua2.pjmedia_rtcp_fb_type.PJMEDIA_RTCP_FB_NACK
import org.pjsip.pjsua2.pjsip_transport_type_e


/** Builds the pjsua2 endpoint, transport, account and codec configuration. */
internal class SipEndpointConfigurator(
    private val settings: CallSettings,
    private val userAgent: String
) {

    fun makeEndpointConfig(logWriter: SipLogWriter): EpConfig {
        val epConfig = EpConfig()
        epConfig.uaConfig.maxCalls = MAX_SIP_CALLS
        epConfig.uaConfig.userAgent = userAgent
        epConfig.medConfig.hasIoqueue = true
        epConfig.medConfig.clockRate = 16000
        epConfig.medConfig.quality = 10
        // A single media worker thread: with 2+ of them PJSIP can process queued vid_conf
        // port-removal jobs for the same call concurrently during teardown (SIGSEGV/SIGBUS
        // in vid_conf.c/vid_port.c right after a call disconnects).
        epConfig.medConfig.threadCnt = 1
        epConfig.medConfig.ecOptions = 0
        epConfig.medConfig.ecTailLen = 0
        epConfig.medConfig.noVad = true
        epConfig.medConfig.audioFramePtime = 40

        val logCfg = epConfig.logConfig
        // pjsip levels: 0 none, 1 error, 2 warning, 3 info, 4 debug, 5 trace. Every logged
        // line crosses JNI into SipLogWriter, so keep pjsip quiet unless SDK debug logs are on.
        val level = when (SdkLogger.level) {
            LogLevel.DEBUG -> 4L
            LogLevel.INFO -> 3L
            LogLevel.WARN -> 2L
            LogLevel.ERROR -> 1L
            LogLevel.OFF -> 0L
        }
        logCfg.level = level
        logCfg.consoleLevel = level
        logCfg.writer = logWriter
        logCfg.decor = logCfg.decor and (
            pj_log_decoration.PJ_LOG_HAS_CR.toLong() or pj_log_decoration.PJ_LOG_HAS_NEWLINE.toLong()
        ).inv()

        return epConfig
    }


    /** The transport from the proxy's `transport=` parameter, else [CallSettings.transport]. */
    fun createTransports(endpoint: PJEndpoint, proxy: String) {
        when (parseTransport(proxy) ?: settings.transport) {
            TransportUse.UDP -> createTransport(endpoint, pjsip_transport_type_e.PJSIP_TRANSPORT_UDP)
            TransportUse.TCP -> createTransport(endpoint, pjsip_transport_type_e.PJSIP_TRANSPORT_TCP)
            TransportUse.TLS -> createTransport(endpoint, pjsip_transport_type_e.PJSIP_TRANSPORT_TLS)
            TransportUse.TCP_UDP -> {
                createTransport(endpoint, pjsip_transport_type_e.PJSIP_TRANSPORT_UDP)
                createTransport(endpoint, pjsip_transport_type_e.PJSIP_TRANSPORT_TCP)
            }
        }
    }


    /**
     * Registration is only needed for incoming calls and is triggered explicitly by
     * `SipManager.register`; outgoing calls never REGISTER.
     */
    fun makeAccountConfig(config: SipConfig): AccountConfig {
        val accountConfig = AccountConfig()
        accountConfig.priority = 100
        accountConfig.idUri = config.getIdUri()

        accountConfig.sipConfig.proxies.clear()
        accountConfig.sipConfig.proxies.add(config.proxyUri())

        accountConfig.regConfig.registrarUri = config.proxyUri()
        accountConfig.regConfig.registerOnAdd = false
        accountConfig.regConfig.proxyUse = 1

        accountConfig.natConfig.mediaStunUse = settings.natMediaStunUse.value
        accountConfig.natConfig.sipStunUse = settings.natSipStunUse.value
        accountConfig.natConfig.iceEnabled = settings.natIceEnabled
        accountConfig.natConfig.sdpNatRewriteUse = if (settings.natSdpNatRewriteUse) 1 else 0
        accountConfig.natConfig.contactRewriteUse = if (settings.natContactRewriteUse) 1 else 0
        accountConfig.natConfig.viaRewriteUse = if (settings.natViaRewriteUse) 1 else 0

        accountConfig.videoConfig.autoShowIncoming = true
        accountConfig.videoConfig.autoTransmitOutgoing = true

        accountConfig.mediaConfig.transportConfig.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
        accountConfig.mediaConfig.srtpUse = settings.srtpUse.value
        accountConfig.mediaConfig.srtpSecureSignaling = settings.srtpSecureSignaling

        val cred = AuthCredInfo("digest", "*", config.auth, 0, config.password)
        try {
            accountConfig.sipConfig.authCreds.clear()
            accountConfig.sipConfig.authCreds.add(cred)
        } finally {
            try { cred.delete() } catch (_: Exception) {}
        }

        enableRtcpFeedback(accountConfig)
        return accountConfig
    }


    fun findH264CodecId(endpoint: PJEndpoint): String? {
        val codecs = endpoint.videoCodecEnum2()
        return try {
            codecs.firstOrNull { it.codecId.startsWith("H264/") }?.codecId
        } finally {
            try { codecs.delete() } catch (_: Exception) {}
        }
    }


    fun applyVideoQuality(
        endpoint: PJEndpoint,
        codecId: String,
        quality: VideoQuality,
        orientation: VideoOrientation
    ) {
        // Portrait: height > width (9:16). Landscape: width > height (16:9).
        val encW = if (orientation.isLandscape) quality.longSide else quality.shortSide
        val encH = if (orientation.isLandscape) quality.shortSide else quality.longSide

        try {
            val param = endpoint.getVideoCodecParam(codecId)
            try {
                param.encFmt.width = encW
                param.encFmt.height = encH
                param.encFmt.fpsNum = quality.fps
                param.encFmt.fpsDenum = 1
                param.encFmt.avgBps = quality.avgBitrate
                param.encFmt.maxBps = quality.maxBitrate

                // The decoder buffer must fit any remote resolution regardless of
                // orientation or of our own encoding preset: a square of the largest
                // supported dimension prevents "not enough buffer" crashes when the remote
                // sends another orientation or a larger size (e.g. a 720p landscape browser).
                val decMax = VideoQuality.MAX_DECODE_DIMENSION
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

                endpoint.setVideoCodecParam(codecId, param)
            } finally {
                try { param.delete() } catch (_: Exception) {}
            }
            SdkLogger.debug(TAG, "Video quality applied for $codecId: ${encW}x${encH} " +
                    "@ ${quality.fps}fps (${quality.avgBitrate / 1000}kbps avg, $orientation)")
        } catch (e: Exception) {
            SdkLogger.error(TAG, "applyVideoQuality: ${e.message}")
        }
    }


    /**
     * Sets the capture orientation of each enumerable camera device individually.
     *
     * Deliberately avoids the global `setCaptureOrient(-1, …)`: on Camera2 devices that
     * return PJ_ENOTFOUND for PJMEDIA_VID_DEV_CAP_ORIENTATION it triggers a native async
     * callback that races PJSIP's internal state (SIGSEGV). Per-device calls are safe, and
     * Camera2 handles sensor rotation itself when a device rejects the call.
     */
    fun applyCameraOrientations(endpoint: PJEndpoint, orientation: VideoOrientation) {
        val vidMgr = endpoint.vidDevManager()
        val devs = try {
            vidMgr.enumDev2()
        } catch (e: Exception) {
            SdkLogger.error(TAG, "applyCameraOrientations: ${e.message}")
            return
        }
        try {
            val orient = CameraOrientation.forDeviceRotation(orientation.rotation)
            devs.forEach { dev ->
                try {
                    vidMgr.setCaptureOrient(dev.id, orient, true)
                    SdkLogger.debug(TAG, "applyCameraOrientations: id=${dev.id} name=${dev.name} orient=$orient")
                } catch (e: Exception) {
                    SdkLogger.debug(TAG, "applyCameraOrientations: skipped id=${dev.id} name=${dev.name}: ${e.message}")
                }
            }
        } finally {
            try { devs.delete() } catch (_: Exception) {}
        }
    }


    private fun createTransport(endpoint: PJEndpoint, type: Int) {
        val cfg = TransportConfig()
        try {
            cfg.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
            endpoint.transportCreate(type, cfg)
        } finally {
            try { cfg.delete() } catch (_: Exception) {}
        }
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
        } catch (e: Exception) {
            SdkLogger.error(TAG, "enableRtcpFeedback: ${e.message}")
        }
    }


    companion object {
        private const val TAG = "SipConfigurator"

        /**
         * PJSIP's own call limit. Higher than the SDK's `CallRegistry.MAX_CALLS`, since
         * an INVITE that doesn't match any pushed call must still reach `onIncomingCall`
         * to be declined with 603.
         */
        const val MAX_SIP_CALLS = 4L

        fun parseTransport(proxy: String): TransportUse? {
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
    }
}
