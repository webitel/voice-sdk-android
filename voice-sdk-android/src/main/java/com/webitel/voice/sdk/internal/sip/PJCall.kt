package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.CallConfig
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import org.pjsip.pjsua2.AudDevManager
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsua_call_flag
import org.pjsip.pjsua2.pjsua_call_media_status


internal class PJCall: Call {
    private var event: SipCallCallbacks
    constructor(acc: PJAccount, callID: Int, endpoint: PJEndpoint, event: SipCallCallbacks) : super(acc, callID) {
        this.account = acc
        this.endpoint = endpoint
        this.event = event
    }

    constructor(acc: PJAccount, endpoint: PJEndpoint, event: SipCallCallbacks): super(acc) {
        this.account = acc
        this.endpoint = endpoint
        this.event = event
    }

    private var account: PJAccount
    private var endpoint: PJEndpoint
    private var isHoldInProgress: Boolean = false

    var isOnMute: Boolean = false
        private set


    override fun onCallState(prm: OnCallStateParam?) {
        super.onCallState(prm)
        event.onCallStatePJSIP(info.state, info.lastStatusCode, info.lastReason)
    }


    override fun onCallMediaState(prm: OnCallMediaStateParam?) {
        val ci = try {
            info
        } catch (e: java.lang.Exception) {
            return
        }

        val cmiv = ci.media

        for (i in cmiv.indices) {
            val cmi = cmiv[i]
            if (cmi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                cmi.status ==
                pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD
            ) {
                isHoldInProgress = false
                event.onHoldCallPJSIP(true)
            } else if (cmi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                cmi.status ==
                pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
            ) {
                isHoldInProgress = false
                event.onHoldCallPJSIP(false)
            }
        }
        for (i in cmiv.indices) {
            val cmi = cmiv[i]
            if (cmi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                (cmi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
                        || cmi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)
            ) {

                val m = getMedia(i.toLong())
                val am = AudioMedia.typecastFromMedia(m)

                // connect ports
                try {
                    val audDevManager: AudDevManager = endpoint.audDevManager()
                    audDevManager.captureDevMedia.startTransmit(am)
                    am.startTransmit(audDevManager.playbackDevMedia)
                } catch (e: Exception) {
                    logger.error("PJCall","onCallMediaState: ${e.message}")
                }
            }
        }

        if (isOnMute) {
            try {
                setMute(true)
            } catch (e: Exception) {
                val stackTrace = e.stackTraceToString()
                logger.error(
                    "PJCall",
                    "onCallMediaState: error while connecting audio media to sound device\n$stackTrace"
                )
            }
        }
    }


    @Throws(IllegalStateException::class)
    fun setMute(mute: Boolean) {
        checkThread()
        val info: CallInfo = info
        var processed = false

        for (i in info.media.indices) {
            val media = getMedia(i.toLong())
            val mediaInfo = info.media[i]

            if (
                mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                media != null &&
                mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
            ) {
                processed = true
                val audioMedia = AudioMedia.typecastFromMedia(media)
                val mgr = endpoint.audDevManager()

                isOnMute = if (mute) {
                    mgr.captureDevMedia.stopTransmit(audioMedia)
                    true
                } else {
                    mgr.captureDevMedia.startTransmit(audioMedia)
                    false
                }
            }
        }

        if (!processed) {
            throw IllegalStateException("No active audio media to mute/unmute")
        }
    }


    @Throws(Exception::class)
    fun transferTo(number: String, name: String) {
        val suffix = when (account.transportUse) {
            CallConfig.TransportUse.TCP -> ";transport=tcp"
            CallConfig.TransportUse.TLS -> ";transport=tls"
            else -> ""
        }
        val uri = account.config.getSipUri(number, name, suffix)
        transferTo(uri)
    }


    @Throws(Exception::class)
    fun setHold() {
        if (isHoldInProgress) {
            throw java.lang.Exception("Hold/unhold operation already in progress.")
        }
        checkThread()
        if(info.state != pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
            throw java.lang.Exception("invalid state - ${info.state}")
        }
        isHoldInProgress = true

        val param = CallOpParam(true)
        setHold(param)
    }


    @Throws(Exception::class)
    fun setUnHold() {
        if (isHoldInProgress) {
            throw java.lang.Exception("Hold/unhold operation already in progress.")
        }
        checkThread()
        if(info.state != pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
            throw java.lang.Exception("invalid state - ${info.state}")
        }
        isHoldInProgress = true

        val param = CallOpParam(true)
        param.opt.audioCount = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
        param.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()

        reinvite(param)
    }


    @Throws(Exception::class)
    fun hangup(code: Int) {
        checkThread()
        val param = CallOpParam(true)
        param.statusCode = code
        hangup(param)
        delete()
    }


    @Throws(Exception::class)
    fun answer() {
        val param = CallOpParam(true)
        param.statusCode = pjsip_status_code.PJSIP_SC_OK
        param.opt.audioCount = 1
        param.opt.videoCount = 0

        answer(param)
    }


    @Throws(Exception::class)
    private fun transferTo(destination: String) {
        val param = CallOpParam(true)
        xfer(destination, param)
    }


    @Throws(Exception::class)
    fun sendDtmf(value: String) {
        checkThread()
        dialDtmf(value)
    }


    @Synchronized
    private fun checkThread() {
        try {
            if (!endpoint.libIsThreadRegistered()) {
                endpoint.libRegisterThread(
                    Thread.currentThread().name
                )
            }
        } catch (e: Exception) {
            logger.error("SipManager", "checkThread:  ${e.message}")
        }
    }
}