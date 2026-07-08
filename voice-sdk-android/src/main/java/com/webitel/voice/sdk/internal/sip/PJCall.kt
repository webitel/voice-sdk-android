package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import org.pjsip.pjsua2.AudDevManager
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallMediaInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.OnCallMediaEventParam
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjmedia_dir
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsua2
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
        withInfo { ci -> event.onCallStatePJSIP(ci.state, ci.lastStatusCode, ci.lastReason) }
    }


    override fun onCallMediaState(prm: OnCallMediaStateParam?) {
        val ci = try {
            info
        } catch (e: Exception) {
            logger.error("PJCall", "onCallMediaState: ${e.message}")
            return
        }

        try {
            logger.debug("PJCall", "media count = ${ci.media.size}")

            val audDevManager = try {
                endpoint.audDevManager()
            } catch (e: Exception) {
                logger.error("PJCall", "audDevManager error: ${e.message}")
                return
            }

            ci.media.forEachIndexed { index, media ->
                logger.debug(
                    "PJCall",
                    "media[$index] type=${media.type} status=${media.status} " +
                            "dir=${media.dir} incomingWindow=${media.videoIncomingWindowId}"
                )
                when (media.type) {
                    pjmedia_type.PJMEDIA_TYPE_AUDIO -> handleAudioMedia(
                        media = media,
                        mediaIndex = index,
                        audDevManager = audDevManager
                    )
                    pjmedia_type.PJMEDIA_TYPE_VIDEO -> handleVideoMedia(media)
                    else -> Unit
                }
            }

            if (isOnMute) {
                try {
                    setMute(true)
                } catch (e: Exception) {
                    logger.error("PJCall", "mute restore error: ${e.stackTraceToString()}")
                }
            }
        } finally {
            try { ci.delete() } catch (_: Exception) {}
        }
    }

    private fun handleAudioMedia(
        media: CallMediaInfo,
        mediaIndex: Int,
        audDevManager: AudDevManager
    ) {
        when (media.status) {
            pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD -> {
                isHoldInProgress = false
                event.onHoldCallPJSIP(true)
                return
            }

            pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE -> {
                isHoldInProgress = false
                event.onHoldCallPJSIP(false)
            }
            else -> Unit
        }

        if (media.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE &&
            media.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD
        ) {
            return
        }

        try {
            val audioMedia = AudioMedia.typecastFromMedia(
                getMedia(mediaIndex.toLong())
            )
            audDevManager.captureDevMedia.startTransmit(audioMedia)
            audioMedia.startTransmit(audDevManager.playbackDevMedia)
            audioMedia.adjustRxLevel(1.3f)
            audioMedia.adjustTxLevel(1.3f)
            logger.debug(
                "PJCall",
                "audio media connected [$mediaIndex]"
            )
        } catch (e: Exception) {
            logger.error(
                "PJCall",
                "audio media error: ${e.stackTraceToString()}"
            )
        }
    }

    private fun handleVideoMedia(media: CallMediaInfo) {
        when (media.status) {
            pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE -> {
                val isEncoding = (media.dir and pjmedia_dir.PJMEDIA_DIR_ENCODING) != 0
                val isDecoding = (media.dir and pjmedia_dir.PJMEDIA_DIR_DECODING) != 0

                if (isEncoding) {
                    logger.debug("PJCall", "onCallMediaState: SHOW_LOCAL_VIDEO")
                    event.onShowLocalVideo(media)
                } else {
                    event.onLocalVideoStopped()
                }

                if (isDecoding && media.videoIncomingWindowId != pjsua2.INVALID_ID) {
                    logger.debug(
                        "PJCall",
                        "onCallMediaState: SHOW_REMOTE_VIDEO incomingWindow=${media.videoIncomingWindowId}"
                    )
                    event.onShowRemoteVideo(media)
                } else {
                    event.onRemoteVideoStopped()
                }
            }

            pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD,
            pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD -> {
                // Hold only flips the SDP media direction to sendonly/inactive — the local
                // camera capture/preview is independent of that and keeps running untouched.
                // Only the remote render is hidden, since frames genuinely stop arriving.
                event.onRemoteVideoStopped()
            }

            else -> {
                // Media genuinely gone (removed from SDP, error, etc.) — tear down both.
                event.onLocalVideoStopped()
                event.onRemoteVideoStopped()
            }
        }
    }


    override fun onCallMediaEvent(prm: OnCallMediaEventParam) {
        logger.debug("PJCall", "onCallMediaEvent type=${prm.ev.type} medIdx=${prm.medIdx}")
        // Only respond to PJMEDIA_EVENT_FMT_CHANGED (1212370246).
        // KEYFRAME_FOUND (1297237577) and stream-state events fire BEFORE PJSIP resizes
        // its decode buffer — calling vw.setWindow() then causes a premature reinit.
        // FMT_CHANGED fires AFTER buffer resize; vw.setWindow() triggers a renderer
        // restart that causes PJSIP to request a new keyframe (via RTCP PLI / SIP INFO),
        // which resumes video at the new resolution after hold/unhold.
        if (prm.ev.type != 1212370246) return
        val ci = try { info } catch (e: Exception) { return }
        try {
            val medIdx = prm.medIdx.toInt()
            if (medIdx < 0 || medIdx >= ci.media.size) return
            val cmi = ci.media[medIdx]
            if (cmi.type != pjmedia_type.PJMEDIA_TYPE_VIDEO ||
                cmi.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                cmi.videoIncomingWindowId == pjsua2.INVALID_ID) return
            event.onRemoteVideoFormatChanged()
        } finally {
            try { ci.delete() } catch (_: Exception) {}
        }
    }


    @Throws(IllegalStateException::class)
    fun setMute(mute: Boolean) {
        checkThread()
        val ci: CallInfo = info
        var processed = false
        try {
            for (i in ci.media.indices) {
                val media = getMedia(i.toLong())
                val mediaInfo = ci.media[i]

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
        } finally {
            try { ci.delete() } catch (_: Exception) {}
        }

        if (!processed) {
            throw IllegalStateException("No active audio media to mute/unmute")
        }
    }


    @Throws(Exception::class)
    fun setHold(videoCount: Long) {
        if (isHoldInProgress) {
            throw java.lang.Exception("Hold/unhold operation already in progress.")
        }
        checkThread()
        val callState = withInfo { it.state }
        if (callState != pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
            throw java.lang.Exception("invalid state - $callState")
        }
        isHoldInProgress = true

        val param = CallOpParam(true)
        try {
            param.opt.audioCount = 1
            param.opt.videoCount = videoCount
            setHold(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    @Throws(Exception::class)
    fun setUnHold(videoCount: Long) {
        if (isHoldInProgress) {
            throw java.lang.Exception("Hold/unhold operation already in progress.")
        }
        checkThread()
        val callState = withInfo { it.state }
        if (callState != pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
            throw java.lang.Exception("invalid state - $callState")
        }
        isHoldInProgress = true

        val param = CallOpParam(true)
        try {
            param.opt.audioCount = 1
            param.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
            param.opt.videoCount = videoCount
            reinvite(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    /**
     * Sends a SIP re-INVITE to add or remove the video stream mid-call.
     *
     * @param videoCount 1 to add video, 0 to remove it.
     */
    @Throws(Exception::class)
    fun reinviteWithVideoCount(videoCount: Long) {
        checkThread()
        val callState = withInfo { it.state }
        if (callState != pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
            throw java.lang.Exception("reinviteWithVideoCount failed: invalid state - $callState")
        }
        val param = CallOpParam(true)
        try {
            param.opt.audioCount = 1
            param.opt.videoCount = videoCount
            reinvite(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    @Throws(Exception::class)
    fun hangup(code: Int) {
        checkThread()
        val param = CallOpParam(true)
        try {
            param.statusCode = code
            hangup(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
        // Do NOT delete() this SWIG wrapper here — the async BYE triggers PJSIP's own
        // PJSIP_INV_STATE_DISCONNECTED callback shortly after, and deleting the wrapper
        // before that callback races PJSIP's internal call-slot reuse (SIGSEGV). The
        // DISCONNECTED handler drops the Kotlin-side reference and lets it be garbage
        // collected once the whole native stack is torn down.
    }


    @Throws(Exception::class)
    fun answer() {
        val param = CallOpParam(true)
        try {
            param.statusCode = pjsip_status_code.PJSIP_SC_OK
            param.opt.audioCount = 1
            param.opt.videoCount = 0
            answer(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    @Throws(Exception::class)
    private fun transferTo(destination: String) {
        val param = CallOpParam(true)
        try {
            xfer(destination, param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    @Throws(Exception::class)
    fun sendDtmf(value: String) {
        checkThread()
        dialDtmf(value)
    }


    /**
     * Executes [block] with a fresh [CallInfo] snapshot and deletes it on exit.
     * Prevents SWIG-owned CallInfo objects from being finalized by FinalizerDaemon,
     * which would call pj_thread_this() on an unregistered thread → SIGABRT.
     */
    private inline fun <T> withInfo(block: (CallInfo) -> T): T {
        val ci = info
        return try {
            block(ci)
        } finally {
            try { ci.delete() } catch (_: Exception) {}
        }
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