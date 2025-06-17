package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.CallConfig
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pj_log_decoration
import org.pjsip.pjsua2.pj_qos_type
import org.pjsip.pjsua2.pjsip_transport_type_e
import com.webitel.voice.sdk.CallConfig.TransportUse
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger


internal class SipManager {

    private var endpoint: PJEndpoint? = null
    private var account: PJAccount? = null
    private var isDestroying: Boolean = false
    private var logWriter = SipLogWriter()

    private var callConfig = CallConfig()

    companion object {
        @Volatile
        private var isLibraryLoaded = false
    }


    @Synchronized
    fun makeAudioCall(sipConfig: SipConfig, callbacks: SipCallCallbacks): PJCall {
        loadNativeLibraries()
        checkThread()
        if (isDestroying) {
            forceRelease()
        }
        val e = endpoint ?: createEndpoint(callConfig).also { endpoint = it }
        val a = account ?: createAccount(callConfig, sipConfig).also { account = it }

        val pjCall = PJCall(a, e, callbacks)

        val callOpParam = CallOpParam()
        callOpParam.opt.videoCount = 0
        callOpParam.opt.audioCount = 1

        val suffix = getTransportSuffixForUri(a.transportUse)
        val remoteUri = a.config.getSipUri("service", "service", suffix)

        pjCall.makeCall(remoteUri, callOpParam)

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
            logger.error("SipManager", "shutdownStack: (full=$fullShutdown) failed: ${ex.message}")
        } finally {
            if (fullShutdown) {
                endpoint = null
            }
            account = null
            isDestroying = false
        }
    }


    private fun createEndpoint(settings: CallConfig): PJEndpoint {
        val point = PJEndpoint()
        point.libCreate()

        val epConfig = getEndpointConfig()

        point.libInit(epConfig)
        point.libRegisterThread(Thread.currentThread().name)

        setTransportConfig(settings.transport, point)

        setCodecPriority(settings.codecPriority)

        point.libStart()
        point.libRegisterThread(Thread.currentThread().name)

        point.audDevManager()?.setOutputVolume(100, true)
        point.audDevManager()?.setInputVolume(100, true)
        return point
    }


    private fun createAccount(settings: CallConfig, config: SipConfig): PJAccount {
        val accountConfig = getAccountConfig(
            config, settings
        )
        val acc = PJAccount(settings.transport, config)
        acc.create(accountConfig, true)

        return acc
    }


    private fun getAccountConfig(config: SipConfig, settings: CallConfig): AccountConfig {
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

        accountConfig.sipConfig.authCreds.clear()
        accountConfig.sipConfig.authCreds.add(cred)

        return accountConfig
    }


    private fun getEndpointConfig(): EpConfig {
        val epConfig = EpConfig()
        epConfig.uaConfig.maxCalls = 32
        epConfig.uaConfig.userAgent = "webitel for android"
        epConfig.medConfig.hasIoqueue = true
        epConfig.medConfig.clockRate = 16000
        epConfig.medConfig.quality = 10
        epConfig.medConfig.ecOptions = 1
        epConfig.medConfig.ecTailLen = 200
        epConfig.medConfig.threadCnt = 2

        setLogConfig(epConfig)

        return epConfig
    }


    private fun setCodecPriority(codecPriority: MutableMap<String, Short>) {
        try {
            codecPriority.forEach { entry ->
                endpoint?.codecSetPriority(entry.key, entry.value)
            }
            endpoint?.videoCodecSetPriority("H264/97", 0)
//            val vidCodecParam: VidCodecParam = endpoint!!.getVideoCodecParam("H264/97")
//            val codecFmtpVector = vidCodecParam.decFmtp
//            val mediaFormatVideo = vidCodecParam.encFmt
//            mediaFormatVideo.width = 640
//            mediaFormatVideo.height = 360
//            vidCodecParam.encFmt = mediaFormatVideo
//
//            for (i in codecFmtpVector.indices) {
//                if ("42e01f".equals(codecFmtpVector[i].name)) {
//                    codecFmtpVector[i].setVal("42e01f")
//                    break
//                }
//            }
//            vidCodecParam.decFmtp = codecFmtpVector
//            endpoint.setVideoCodecParam("H264/97", vidCodecParam)
//            printCodecs()
        }catch (e: Exception){
            logger.error("SipManager", "setCodecPriority:  ${e.message}")
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


    private fun getTransportSuffixForUri(transport: TransportUse): String {
        return when (transport) {
            TransportUse.TCP -> ";transport=tcp"
            TransportUse.TLS -> ";transport=tls"
            else -> ""
        }
    }


    private fun createTransportUDP(point: PJEndpoint) {
        val udpTransport = TransportConfig()
        udpTransport.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
        point.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, udpTransport)
    }


    private fun createTransportTCP(point: PJEndpoint) {
        val tcpTransport = TransportConfig()
        tcpTransport.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
        point.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, tcpTransport)
    }


    private fun createTransportTLS(point: PJEndpoint) {
        val tlsTransport = TransportConfig()
        tlsTransport.qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
        point.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tlsTransport)
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
            logger.debug("SipManager", "loadNativeLibraries: c++_shared and pjsua2 loaded")
        } catch (error: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            logger.error("SipManager", "loadNativeLibraries: Error loading libraries - ${error}")
        }
    }


    private fun setLogConfig(epConfig: EpConfig) {
        logWriter = SipLogWriter()
        epConfig.logConfig.level = 4
        epConfig.logConfig.consoleLevel = 4

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