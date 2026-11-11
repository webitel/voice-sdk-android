package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import org.pjsip.pjsua2.AudDevManager
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.CallSendRequestParam
import org.pjsip.pjsua2.CallVidSetStreamParam
import org.pjsip.pjsua2.OnCallMediaEventParam
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnCallTsxStateParam
import org.pjsip.pjsua2.SipTxOption
import org.pjsip.pjsua2.pjmedia_dir
import org.pjsip.pjsua2.pjmedia_event_type
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_event_id_e
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsua_call_flag
import org.pjsip.pjsua2.pjsua_call_media_status
import org.pjsip.pjsua2.pjsua_call_vid_strm_op


/**
 * Sets the media line counts of a call operation. Since pjsip 2.17 the default call setting
 * has txt_cnt = 1 (RTT/T.140), which adds "m=text" to SDP offers — always disable it.
 */
internal fun CallOpParam.setMediaCount(videoCount: Long): CallOpParam = apply {
    opt.audioCount = 1
    opt.videoCount = videoCount
    opt.textCount = 0
}


/**
 * Cumulative RTCP counters of one media stream, copied out of a SWIG `StreamStat`.
 *
 * @property rxPkt packets received from the media server.
 * @property rxLoss packets from the media server lost on the way to us.
 * @property txPkt packets sent to the media server.
 * @property txLoss our packets the media server reported lost (RTCP receiver reports).
 * @property txReports number of RTCP receiver reports received so far (~1 per 5 s); [txLoss]
 *           and [rttMs] only change when it does.
 * @property rttMs last round-trip time in milliseconds; 0 until measured.
 */
internal data class SipStreamStat(
    val rxPkt: Long,
    val rxLoss: Long,
    val txPkt: Long,
    val txLoss: Long,
    val txReports: Long,
    val rttMs: Int
)


/** One media line of a call, copied out of a SWIG `CallMediaInfo`. */
internal data class SipMediaInfo(
    val index: Int,
    /** `pjmedia_type` */
    val type: Int,
    /** `pjsua_call_media_status` */
    val status: Int,
    /** `pjmedia_dir` bit mask */
    val dir: Int,
    val videoIncomingWindowId: Int,
    val videoCaptureDeviceId: Int
) {
    val isAudio get() = type == pjmedia_type.PJMEDIA_TYPE_AUDIO
    val isVideo get() = type == pjmedia_type.PJMEDIA_TYPE_VIDEO
    val isActive get() = status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
    val isEncoding get() = (dir and pjmedia_dir.PJMEDIA_DIR_ENCODING) != 0
    val isDecoding get() = (dir and pjmedia_dir.PJMEDIA_DIR_DECODING) != 0
}


/**
 * Thin pjsua2 `Call` wrapper. Callbacks copy their data — PJSIP may invalidate it once the
 * callback returns — and forward it to [listener] on the SIP thread. Operations are called
 * by [SipManager] on the SIP thread.
 *
 * Never call `delete()` on this object after the call is disconnected: PJSIP frees and
 * reuses the call slot on its own, and the native destructor races that cleanup (SIGSEGV).
 * Never just drop the reference either — call [retire] first.
 */
internal class PJCall : Call {
    private val listener: SipEventListener

    /** An outgoing call; the SIP id is assigned by `makeCall`. */
    constructor(acc: PJAccount, listener: SipEventListener) : super(acc) {
        this.listener = listener
    }

    /** An incoming call, created inside `Account.onIncomingCall`. */
    constructor(acc: PJAccount, callId: Int, listener: SipEventListener) : super(acc, callId) {
        this.listener = listener
    }


    override fun onCallState(prm: OnCallStateParam?) {
        super.onCallState(prm)
        // Read synchronously: after DISCONNECTED pjsua2 resets the id and the info is gone.
        val sipId = id
        val snapshot = try {
            withInfo { Triple(it.state, it.lastStatusCode, it.lastReason.orEmpty()) }
        } catch (e: Exception) {
            SdkLogger.error(TAG, "onCallState: ${e.message}")
            return
        }
        SdkThreads.postSip {
            listener.onCallState(sipId, snapshot.first, snapshot.second, snapshot.third)
        }
    }


    override fun onCallMediaState(prm: OnCallMediaStateParam?) {
        val sipId = id
        SdkThreads.postSip { listener.onCallMediaState(sipId) }
    }


    override fun onCallTsxState(prm: OnCallTsxStateParam) {
        super.onCallTsxState(prm)
        try {
            val tsxState = prm.e?.body?.tsxState ?: return
            if (tsxState.tsx?.method != "INFO") return
            if (tsxState.type != pjsip_event_id_e.PJSIP_EVENT_RX_MSG) return
            val wholeMsg = tsxState.src?.rdata?.wholeMsg ?: return
            val sipId = id
            SdkThreads.postSip { listener.onIncomingInfo(sipId, wholeMsg) }
        } catch (e: Exception) {
            SdkLogger.error(TAG, "onCallTsxState: ${e.message}")
        }
    }


    override fun onCallMediaEvent(prm: OnCallMediaEventParam) {
        // Only FMT_CHANGED: KEYFRAME_FOUND and stream-state events fire before PJSIP resizes
        // its decode buffer, and re-attaching the render window then causes a premature
        // reinit. FMT_CHANGED fires after the resize; re-attaching makes PJSIP request a new
        // keyframe, which resumes video at the new resolution (e.g. after hold/unhold).
        if (prm.ev.type != pjmedia_event_type.PJMEDIA_EVENT_FMT_CHANGED) return
        val sipId = id
        val mediaIndex = prm.medIdx.toInt()
        val fmtChanged = prm.ev.data.fmtChanged
        val width = fmtChanged?.newWidth?.toInt() ?: 0
        val height = fmtChanged?.newHeight?.toInt() ?: 0
        SdkThreads.postSip { listener.onRemoteVideoFormatChanged(sipId, mediaIndex, width, height) }
    }


    /**
     * Hands the native object over to PJSIP before the last reference is dropped, so it is
     * never destroyed. pjsua2 keeps `Call::id` valid after DISCONNECTED; finalizing this
     * wrapper would run `~Call()` → `pjsua_call_set_user_data(id)` on FinalizerDaemon
     * (SIGABRT in `pj_thread_this`), or clear the user data of a new call reusing the slot.
     * The native `Call` is leaked on purpose; nothing references it anymore.
     */
    fun retire() {
        try { swigReleaseOwnership() } catch (e: Exception) {
            SdkLogger.error(TAG, "retire: ${e.message}")
        }
    }


    // region Operations (SIP thread)

    /** `pjsip_inv_state` of the call. */
    fun inviteState(): Int = withInfo { it.state }


    fun mediaInfos(): List<SipMediaInfo> = withInfo { ci ->
        ci.media.mapIndexed { index, media ->
            SipMediaInfo(
                index = index,
                type = media.type,
                status = media.status,
                dir = media.dir,
                videoIncomingWindowId = media.videoIncomingWindowId,
                videoCaptureDeviceId = media.videoCapDev
            )
        }
    }


    /** RTCP counters of the first active audio stream, or `null` if there is none. */
    fun audioStreamStat(): SipStreamStat? {
        val index = mediaInfos().firstOrNull { it.isAudio && it.isActive }?.index ?: return null
        val stat = getStreamStat(index.toLong())
        try {
            // Nested getters return non-owning views into `stat`; only `stat` is deleted.
            val rtcp = stat.rtcp ?: return null
            val rx = rtcp.rxStat ?: return null
            val tx = rtcp.txStat ?: return null
            return SipStreamStat(
                rxPkt = rx.pkt,
                rxLoss = rx.loss,
                txPkt = tx.pkt,
                txLoss = tx.loss,
                txReports = tx.updateCount,
                rttMs = (rtcp.rttUsec?.last ?: 0) / 1000
            )
        } finally {
            try { stat.delete() } catch (_: Exception) {}
        }
    }


    /** Connects the call's audio media at [mediaIndex] to the sound device. */
    fun connectAudio(mediaIndex: Int, audDevManager: AudDevManager) {
        val audioMedia = AudioMedia.typecastFromMedia(getMedia(mediaIndex.toLong()))
        audDevManager.captureDevMedia.startTransmit(audioMedia)
        audioMedia.startTransmit(audDevManager.playbackDevMedia)
        audioMedia.adjustRxLevel(1.3f)
        audioMedia.adjustTxLevel(1.3f)
    }


    /** @throws IllegalStateException if the call has no active audio media yet. */
    fun setMute(mute: Boolean, audDevManager: AudDevManager) {
        val audioIndexes = mediaInfos().filter { it.isAudio && it.isActive }.map { it.index }
        check(audioIndexes.isNotEmpty()) { "No active audio media to mute/unmute" }
        for (index in audioIndexes) {
            val audioMedia = AudioMedia.typecastFromMedia(getMedia(index.toLong()))
            if (mute) audDevManager.captureDevMedia.stopTransmit(audioMedia)
            else audDevManager.captureDevMedia.startTransmit(audioMedia)
        }
    }


    fun setHold(hold: Boolean, videoCount: Long) {
        ensureConfirmed("setHold")
        withOpParam { param ->
            param.setMediaCount(videoCount)
            if (hold) {
                setHold(param)
            } else {
                param.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
                reinvite(param)
            }
        }
    }


    /** Sends a re-INVITE adding (1) or removing (0) the video stream. */
    fun reinviteWithVideoCount(videoCount: Long) {
        ensureConfirmed("reinviteWithVideoCount")
        withOpParam { param ->
            param.setMediaCount(videoCount)
            reinvite(param)
        }
    }


    /**
     * Starts or stops feeding local video frames into the network stream without touching
     * the negotiated SDP (no re-INVITE).
     *
     * @throws IllegalStateException if there is no active local video stream.
     */
    fun setLocalVideoTransmitting(transmit: Boolean) {
        check(mediaInfos().any { it.isVideo && it.isActive && it.isEncoding }) {
            "No active local video stream"
        }
        val param = CallVidSetStreamParam()
        try {
            vidSetStream(
                if (transmit) pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_START_TRANSMIT
                else pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_STOP_TRANSMIT,
                param
            )
            if (transmit) {
                // STOP_TRANSMIT may have left the remote decoder without a reference frame;
                // request a keyframe right away rather than waiting for the periodic one.
                try {
                    vidSetStream(pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_SEND_KEYFRAME, param)
                } catch (e: Exception) {
                    SdkLogger.error(TAG, "setLocalVideoTransmitting: keyframe request failed: ${e.message}")
                }
            }
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    /** Restarts local video transmission, e.g. after the preview was rebuilt. */
    fun startVideoTransmit() {
        val param = CallVidSetStreamParam()
        try {
            vidSetStream(pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_START_TRANSMIT, param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    fun hangup(code: Int) {
        withOpParam { param ->
            param.statusCode = code
            hangup(param)
        }
    }


    /** Answers audio-only; video can be added later with a re-INVITE. */
    fun answer(code: Int = pjsip_status_code.PJSIP_SC_OK) {
        withOpParam { param ->
            param.statusCode = code
            param.setMediaCount(videoCount = 0)
            answer(param)
        }
    }


    fun sendDtmf(digits: String) {
        dialDtmf(digits)
    }


    fun sendInfo(contentType: String, body: String) {
        val txOption = SipTxOption()
        val param = CallSendRequestParam()
        try {
            txOption.contentType = contentType
            txOption.msgBody = body
            param.method = "INFO"
            param.txOption = txOption
            sendRequest(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
            try { txOption.delete() } catch (_: Exception) {}
        }
    }

    // endregion


    private fun ensureConfirmed(operation: String) {
        val state = inviteState()
        check(state == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) { "$operation: invalid state - $state" }
    }


    private inline fun withOpParam(block: (CallOpParam) -> Unit) {
        val param = CallOpParam(true)
        try {
            block(param)
        } finally {
            try { param.delete() } catch (_: Exception) {}
        }
    }


    /**
     * Runs [block] with a fresh [CallInfo] and deletes it right after, so FinalizerDaemon
     * never finalizes it on an unregistered thread (SIGABRT in `pj_thread_this`).
     */
    private inline fun <T> withInfo(block: (CallInfo) -> T): T {
        val ci = info
        return try {
            block(ci)
        } finally {
            try { ci.delete() } catch (_: Exception) {}
        }
    }


    private companion object {
        const val TAG = "PJCall"
    }
}
