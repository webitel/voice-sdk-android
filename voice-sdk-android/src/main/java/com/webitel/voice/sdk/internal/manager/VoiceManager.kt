package com.webitel.voice.sdk.internal.manager

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.internal.call.CallControlDelegate
import com.webitel.voice.sdk.internal.call.CallImpl
import com.webitel.voice.sdk.internal.call.CancelCallCode
import com.webitel.voice.sdk.internal.call.DisconnectTarget
import com.webitel.voice.sdk.internal.sip.MediaStateInfo
import com.webitel.voice.sdk.internal.sip.PJCall
import com.webitel.voice.sdk.internal.sip.SipConfig
import com.webitel.voice.sdk.internal.sip.SipEventListener
import com.webitel.voice.sdk.internal.sip.SipManager
import com.webitel.voice.sdk.internal.sip.SipMediaInfo
import com.webitel.voice.sdk.internal.sip.SipStreamStat
import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import kotlinx.coroutines.suspendCancellableCoroutine
import org.pjsip.PjCameraInfo2
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsua_call_media_status


/**
 * Owns every call and the single SIP stack of the process (pjsua is process-global).
 *
 * Except for the call registry (readable from any thread), everything here runs on the SIP
 * thread: [CallControlDelegate] methods are invoked by [CallImpl] from it, and SIP events
 * are dispatched to it by `PJCall` / `PJAccount`.
 */
internal class VoiceManager private constructor(
    application: Application,
    userAgent: String
) : CallControlDelegate, SipEventListener {

    val registry = CallRegistry()

    private val sipManager = SipManager(listener = this, userAgent = userAgent)
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val audio = AudioController(audioManager, onCellularCall = ::holdActiveCall)
    private val audioRouter = CallAudioRouter(audioManager)

    /** The app's Telecom integration owns audio focus, mode and routing (`HostManaged`). */
    @Volatile
    private var externalAudio = false

    /** Decline an unanswered incoming call with 600 instead of 486. SIP thread only. */
    private var busyEverywhere = false

    init {
        val cameraManager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        PjCameraInfo2.SetCameraManager(cameraManager)
    }


    val activeCall: CallImpl?
        get() = registry.activeCall

    val calls: List<CallImpl>
        get() = registry.calls


    /** See [CallRegistry.insert]. */
    fun insertCall(
        existing: (CallRegistry.State) -> CallImpl?,
        reject: (CallRegistry.State) -> Boolean,
        make: () -> CallImpl
    ): Pair<CallImpl, CallRegistry.InsertOutcome> = registry.insert(existing, reject, make)


    fun updateCallSettings(settings: CallSettings) {
        SdkThreads.postSip {
            sipManager.updateCallSettings(settings)
            busyEverywhere = settings.busyEverywhereUse
        }
    }


    /** With a host-managed system call integration the app owns audio focus, mode and routing. */
    fun setExternalAudio(external: Boolean) {
        externalAudio = external
        SdkThreads.postSip {
            audio.setMode(if (external) AudioController.Mode.EXTERNAL else AudioController.Mode.AUTOMATIC)
        }
    }


    /**
     * Registers the SIP account so the server can deliver an incoming call.
     * Returns once the registrar answers 2xx. @throws CallError
     */
    suspend fun registerForIncoming(config: SipConfig) {
        suspendCancellableCoroutine { continuation ->
            SdkThreads.postSip {
                sipManager.register(config) { result -> continuation.resumeWith(result) }
            }
        }
    }


    /** @throws CallError */
    suspend fun makeCallFor(callId: String, config: SipConfig, options: CallOptions) {
        SdkThreads.onSip {
            // The call may have been disconnected while the SIP config was being fetched.
            if (registry.find(callId) == null) {
                SdkLogger.warn(TAG, "makeCallFor: call $callId is gone, not placing it")
                return@onSip
            }
            audio.requestAudio()
            val sipId = sipManager.makeCall(config, options)
            registry.assignSipId(sipId, callId)
        }
    }


    /** [onComplete] is called on the SDK events thread. */
    fun shutdown(onComplete: () -> Unit) {
        SdkThreads.postSip {
            // Calls that never reached SIP end here; SIP calls end through DISCONNECTED.
            for (call in registry.calls) {
                call.cancelTask()
                if (call.sipId < 0) {
                    registry.remove(call.id)
                    call.updateState(CallState.Disconnected(fromCode(0, "Client shut down")))
                }
            }
            sipManager.destroy()
            // Don't rely on PJSIP reporting DISCONNECTED for every call it hung up.
            for (call in registry.calls) {
                finishSipCall(call.sipId, call, fromCode(0, "Client shut down"))
            }
            audioRouter.reset()
            audio.reset()
            SdkThreads.postEvent(onComplete)
        }
    }


    // region CallControlDelegate

    override fun disconnectCall(target: DisconnectTarget) {
        when (target) {
            is DisconnectTarget.Local -> disconnectLocalCall(target.callId)
            is DisconnectTarget.Sip -> disconnectSipCall(target.sipId)
        }
    }


    override fun muteCall(sipId: Int, muted: Boolean) = sipManager.setMute(sipId, muted)


    override fun holdCall(sipId: Int, onHold: Boolean, withVideo: Boolean) {
        sipManager.setHold(sipId, onHold, videoCount = if (withVideo) 1L else 0L)
    }


    override fun sendDtmf(sipId: Int, digits: String) = sipManager.sendDtmf(sipId, digits)


    override fun answerCall(sipId: Int) {
        // Hold the other call before answering, not only on CONFIRMED, so both calls'
        // audio isn't bridged while the 200 OK is being acknowledged.
        registry.findBySipId(sipId)?.let { holdOtherCalls(exceptId = it.id) }
        audio.requestAudio()
        sipManager.answerCall(sipId)
    }


    override val isSpeakerActive: Boolean
        get() = !externalAudio && audioRouter.isSpeakerphoneOn


    /** Telecom routes the audio with `HostManaged`; routing it here too would fight it. */
    override fun setSpeakerphoneOn(enabled: Boolean): Result<Unit> {
        if (externalAudio) {
            return Result.failure(CallError.InvalidState(
                "Audio is routed by the app's Telecom integration (HostManaged); use Telecom call endpoints"
            ))
        }
        return audioRouter.setSpeakerphoneOn(enabled)
    }


    override fun setVideoEnabled(sipId: Int, enabled: Boolean) {
        sipManager.reinviteWithVideoCount(sipId, if (enabled) 1L else 0L)
    }


    override fun setLocalVideoTransmitting(sipId: Int, transmit: Boolean) {
        sipManager.setLocalVideoTransmitting(sipId, transmit)
    }


    override fun updateVideoOrientation(orientation: VideoOrientation) {
        sipManager.updateVideoOrientation(orientation)
    }


    override fun sendMediaStateInfo(sipId: Int, audioMuted: Boolean, videoMuted: Boolean, hold: Boolean) {
        val body = MediaStateInfo.encode(audioMuted = audioMuted, videoMuted = videoMuted, hold = hold)
        SdkLogger.debug(TAG, "sendMediaStateInfo: $body")
        sipManager.sendInfo(sipId, MediaStateInfo.CONTENT_TYPE, body)
    }


    override fun sipCall(sipId: Int): PJCall? = sipManager.call(sipId)


    override fun mediaInfos(sipId: Int): List<SipMediaInfo>? = sipManager.mediaInfos(sipId)

    override fun audioStreamStat(sipId: Int): SipStreamStat? = sipManager.audioStreamStat(sipId)

    // endregion


    // region SipEventListener

    override fun onCallState(sipId: Int, state: Int, lastStatusCode: Int, lastReason: String) {
        val call = registry.findBySipId(sipId)
        if (call == null) {
            SdkLogger.debug(TAG, "onCallState: no call for sipId $sipId, state $state")
            if (state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                sipManager.forgetCall(sipId)
                checkAndDestroySip()
            }
            return
        }

        when (state) {
            pjsip_inv_state.PJSIP_INV_STATE_NULL,
            pjsip_inv_state.PJSIP_INV_STATE_CALLING,
            pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> {
                call.updateState(CallState.Connecting)
                audio.requestAudio()
            }

            // Incoming calls are already Ringing (or Connecting if answered before the
            // INVITE arrived); our own 180 must not change that.
            pjsip_inv_state.PJSIP_INV_STATE_EARLY -> if (call.isOutgoing) call.updateState(CallState.Ringing)

            pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> if (call.state == CallState.Idle) call.updateState(CallState.Ringing)

            pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> {
                holdOtherCalls(exceptId = call.id)
                call.onConfirmed()
            }

            pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> {
                finishSipCall(sipId, call, fromCode(lastStatusCode, lastReason))
            }
        }
    }


    override fun onCallMediaState(sipId: Int) {
        val call = registry.findBySipId(sipId) ?: return
        val medias = sipManager.handleMediaState(sipId) ?: return

        for (media in medias.filter { it.isAudio }) {
            when (media.status) {
                pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD -> call.onHoldChanged(true)
                pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE -> {
                    registry.setActive(call)
                    holdOtherCalls(exceptId = call.id)
                    call.onHoldChanged(false)
                }
            }
        }

        if (call.isMuted) {
            try {
                sipManager.setMute(sipId, true)
            } catch (e: Exception) {
                SdkLogger.error(TAG, "onCallMediaState: failed to restore mute: ${e.message}")
            }
        }

        for (media in medias.filter { it.isVideo }) {
            handleVideoMedia(call, media)
        }
    }


    override fun onRemoteVideoFormatChanged(sipId: Int, mediaIndex: Int, width: Int, height: Int) {
        val call = registry.findBySipId(sipId) ?: return
        // Fresh media info: the render window from the media-thread event may already be gone.
        val media = sipManager.mediaInfos(sipId)?.getOrNull(mediaIndex) ?: return
        if (!media.isVideo || !media.isActive || media.videoIncomingWindowId < 0) return
        call.video.onRemoteVideoFormatChanged(width, height)
    }


    override fun onIncomingInfo(sipId: Int, wholeMsg: String) {
        val call = registry.findBySipId(sipId) ?: return
        val info = MediaStateInfo.parse(wholeMsg) ?: return
        SdkLogger.debug(TAG, "onIncomingInfo: $info")
        call.onRemoteMediaStateInfo(audioMuted = info.audioMuted, videoMuted = info.videoMuted, hold = info.hold)
    }


    override fun onIncomingCall(call: PJCall, callUuid: String?) {
        // The stack was destroyed after the INVITE arrived; PJSIP already dropped the call.
        if (!sipManager.isRunning) {
            call.retire()
            return
        }
        val sipId = call.id
        sipManager.adoptIncomingCall(call)

        val pending = callUuid?.let { uuid ->
            registry.calls.firstOrNull { it.id.equals(uuid, ignoreCase = true) }
        }
        if (pending == null || pending.isOutgoing || pending.sipId >= 0 || pending.state is CallState.Disconnected) {
            SdkLogger.warn(TAG, "onIncomingCall: no pending incoming call for X-Webitel-Uuid $callUuid, declining")
            try {
                sipManager.hangup(sipId, CancelCallCode.DECLINE)
            } catch (e: Exception) {
                SdkLogger.error(TAG, "onIncomingCall: failed to decline: ${e.message}")
            }
            return
        }

        SdkLogger.debug(TAG, "onIncomingCall: INVITE matched call ${pending.id}, sipId $sipId")
        registry.assignSipId(sipId, pending.id)
        sipManager.sendRinging(sipId)
        pending.onIncomingInviteReceived()
    }


    override fun onRegistrationState(code: Int, reason: String) {
        sipManager.onRegistrationState(code, reason)
    }

    // endregion


    private fun handleVideoMedia(call: CallImpl, media: SipMediaInfo) {
        val video = call.video
        when (media.status) {
            pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE -> {
                if (media.isEncoding) video.onShowLocalVideo(media.videoCaptureDeviceId) else video.onLocalVideoStopped()
                if (media.isDecoding && media.videoIncomingWindowId >= 0) {
                    video.onShowRemoteVideo(media.videoIncomingWindowId)
                } else {
                    video.onRemoteVideoStopped()
                }
            }

            // Hold only flips the SDP direction; the local camera keeps running. Only the
            // remote render is hidden, since frames genuinely stop arriving.
            pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD,
            pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD -> video.onRemoteVideoStopped()

            // The media is genuinely gone (removed from the SDP, error, ...).
            else -> {
                video.onLocalVideoStopped()
                video.onRemoteVideoStopped()
            }
        }
    }


    private fun disconnectSipCall(sipId: Int) {
        val call = registry.findBySipId(sipId) ?: throw CallError.InvalidState("Call with sipId $sipId not found")
        val code = CancelCallCode.forHangup(call.isAnswered, call.isOutgoing, busyEverywhere)
        try {
            sipManager.hangup(sipId, code)
        } catch (e: Exception) {
            // The SIP call can't be hung up (e.g. the stack is gone): end it locally,
            // or it would never leave the registry.
            SdkLogger.error(TAG, "disconnectSipCall: ${e.message}, ending the call locally")
            finishSipCall(sipId, call, fromCode(code))
        }
    }


    /**
     * The SIP call ended. The native video cleanup is posted only after the stack may have
     * been destroyed below, so it never races PJSIP's own teardown of the same media.
     */
    private fun finishSipCall(sipId: Int, call: CallImpl, reason: CallEndReason) {
        val videoCleanup = call.onSipDisconnected(reason)
        sipManager.forgetCall(sipId)
        disconnectLocalCall(call.id)
        SdkThreads.postSip(videoCleanup)
    }


    private fun disconnectLocalCall(callId: String) {
        registry.remove(callId)
        checkAndDestroySip()
    }


    /** pjsua runs only while at least one call exists. */
    private fun checkAndDestroySip() {
        if (!registry.isEmpty) return
        audioRouter.reset()
        audio.reset()
        sipManager.destroy()
    }


    /** A cellular call took the audio over (unmanaged calls only). SIP thread. */
    private fun holdActiveCall() {
        val call = registry.activeCall ?: return
        if (call.state != CallState.Ongoing || call.isOnHold || call.isHoldInProgress) return
        SdkLogger.debug(TAG, "Cellular call started, holding ${call.id}")
        try {
            call.performHold(true)
        } catch (e: Exception) {
            SdkLogger.error(TAG, "holdActiveCall: ${e.message}")
        }
    }


    /** Only ongoing calls can be held; a ringing one keeps ringing. */
    private fun holdOtherCalls(exceptId: String) {
        for (other in registry.holdableCalls(exceptId)) {
            SdkLogger.debug(TAG, "Holding other call: ${other.id}")
            try {
                other.performHold(true)
            } catch (e: Exception) {
                SdkLogger.error(TAG, "holdOtherCalls: failed to hold ${other.id}: ${e.message}")
            }
        }
    }


    companion object {
        private const val TAG = "VoiceManager"

        @Volatile
        private var instance: VoiceManager? = null

        fun shared(application: Application, userAgent: String): VoiceManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceManager(application, userAgent).also { instance = it }
            }
        }
    }
}
