package com.webitel.voice.sdk.internal.call

import android.view.Surface
import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.CallEvent
import com.webitel.voice.sdk.CallEventListener
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.CallType
import com.webitel.voice.sdk.ConnectionEvent
import com.webitel.voice.sdk.LocalMediaEvent
import com.webitel.voice.sdk.ConnectionQuality
import com.webitel.voice.sdk.RemoteMediaEvent
import com.webitel.voice.sdk.VideoEvent
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.VideoState
import com.webitel.voice.sdk.internal.sip.SipStreamStat
import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import com.webitel.voice.sdk.isSameAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference


/**
 * Threading: public methods validate against the current state synchronously (returning
 * `CallError.InvalidState`) and perform the actual work on the SIP thread, so they never
 * block the caller. State visible through [Call] is readable from any thread and written
 * on the SIP thread. Everything else is confined to the SIP thread.
 */
internal class CallImpl(
    private val delegate: CallControlDelegate,
    private val ratingService: CallRatingProvider,
    override val id: String = UUID.randomUUID().toString(),
    override val isOutgoing: Boolean = true,
    override val remoteName: String? = null,
    override val remoteNumber: String? = null,
    val meetingId: String? = null,
    override val type: CallType = CallType.AUDIO,
    initialOrientation: VideoOrientation = VideoOrientation.PORTRAIT,
    initialState: CallState = CallState.Idle
) : Call {

    private val listeners = CallListenerHub()
    private val apiJob = AtomicReference<Job?>(null)
    private val stateLock = Any()

    @Volatile
    override var state: CallState = initialState
        private set

    @Volatile
    var sipId: Int = NO_SIP_ID

    /** The hold state a hold/unhold re-INVITE is in flight for; `null` when none is. */
    @Volatile
    var holdTarget: Boolean? = null

    @Volatile
    override var isMuted: Boolean = false
        private set

    @Volatile
    override var isOnHold: Boolean = false
        private set

    @Volatile
    override var answeredAt: Long = 0
        private set

    @Volatile
    override var isLocalVideoPaused: Boolean = false
        private set

    @Volatile
    override var isRemoteMuted: Boolean = false
        private set

    @Volatile
    override var isRemoteOnHold: Boolean = false
        private set

    @Volatile
    override var isRemoteVideoPaused: Boolean = false
        private set

    @Volatile
    override var connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN
        private set

    private val qualityMonitor = ConnectionQualityMonitor(object : ConnectionQualityMonitor.Host {
        override val isEnded: Boolean get() = state is CallState.Disconnected
        override val isPaused: Boolean get() = isOnHold || isRemoteOnHold
        override val expectsIncomingMedia: Boolean get() = !isRemoteMuted
        override fun readStat(): SipStreamStat? = if (sipId < 0) null else delegate.audioStreamStat(sipId)
        override fun onQualityChanged(quality: ConnectionQuality) {
            connectionQuality = quality
            notify(ConnectionEvent.QualityChanged(id, quality))
        }
    })

    /** `answer()` was called before the incoming INVITE arrived. SIP thread only. */
    private var pendingAnswer = false
    private var incomingTimeout: ScheduledFuture<*>? = null

    /** Video count to restore on unhold, captured when the hold started. SIP thread only. */
    private var holdVideoCount = 0L

    val video = CallVideoController(
        host = object : CallVideoController.Host {
            override val sipId: Int get() = this@CallImpl.sipId
            override val isLocalVideoPaused: Boolean get() = this@CallImpl.isLocalVideoPaused
            override fun onVideoStateChanged() = notify(VideoEvent.StateChanged(id, videoState))
            override fun onVideoSizeChanged(isLocal: Boolean, width: Int, height: Int) =
                notify(VideoEvent.SizeChanged(id, isLocal, width, height))
        },
        delegate = delegate,
        initialOrientation = initialOrientation
    )

    val isHoldInProgress: Boolean
        get() = holdTarget != null

    val isAnswered: Boolean
        get() = answeredAt > 0

    /** The call started as video or has video now. */
    val hasVideo: Boolean
        get() = type == CallType.VIDEO || videoState != VideoState.INACTIVE

    override val isSpeakerphoneOn: Boolean
        get() = delegate.isSpeakerActive

    override val isFrontCamera: Boolean
        get() = video.isFrontCamera

    override val videoState: VideoState
        get() {
            val local = video.isLocalVideoActive
            val remote = video.isRemoteVideoActive
            return when {
                local && remote -> VideoState.ACTIVE
                local -> VideoState.LOCAL_ONLY
                remote -> VideoState.REMOTE_ONLY
                else -> VideoState.INACTIVE
            }
        }


    // region Routed actions

    override fun mute(mute: Boolean): Result<Unit> = submit(CallAction.Mute(mute))


    override fun hold(hold: Boolean): Result<Unit> {
        if (state == CallState.Ongoing && isHoldSatisfied(hold)) return Result.success(Unit)
        return submit(CallAction.Hold(hold))
    }


    override fun sendDTMF(digits: String): Result<Unit> {
        if (digits.isEmpty()) return Result.failure(CallError.InvalidState("DTMF digits must not be empty"))
        return submit(CallAction.Dtmf(digits))
    }


    override fun answer(): Result<Unit> = submit(CallAction.Answer)


    override fun disconnect(): Result<Unit> {
        // Stop an in-flight SIP config fetch or registration right away.
        if (state !is CallState.Disconnected && sipId < 0) cancelTask()
        return submit(CallAction.End)
    }


    /** Validates synchronously, then performs the action on the SIP thread. */
    private fun submit(action: CallAction): Result<Unit> {
        try {
            validate(action)
        } catch (e: CallError) {
            return Result.failure(e)
        }
        SdkThreads.postSip {
            try {
                performAction(action)
            } catch (e: Exception) {
                SdkLogger.error(TAG, "$action: ${e.message}")
            }
        }
        return Result.success(Unit)
    }


    /**
     * Performs a validated action. Re-validates first, since the state may have changed
     * since it was submitted. SIP thread only.
     */
    private fun performAction(action: CallAction) {
        SdkThreads.assertOnSip()
        validate(action)
        when (action) {
            CallAction.Answer -> performAnswer()
            CallAction.End -> internalDisconnect(fromCode(0), cancelTask = true)
            is CallAction.Mute -> performMute(action.muted)
            is CallAction.Hold -> performHold(action.onHold)
            is CallAction.Dtmf -> delegate.sendDtmf(sipId, action.digits)
        }
    }


    private fun validate(action: CallAction) {
        when (action) {
            CallAction.Answer -> {
                if (isOutgoing) throw CallError.InvalidState("Only incoming calls can be answered")
                if (state != CallState.Ringing) {
                    throw CallError.InvalidState("Call is not ringing. Current state: $state")
                }
            }
            CallAction.End -> {
                if (state is CallState.Disconnected) throw CallError.InvalidState("Call is already disconnected")
            }
            is CallAction.Mute -> ensureMediaControlAllowed()
            is CallAction.Hold -> {
                if (state == CallState.Ongoing && isHoldSatisfied(action.onHold)) return
                ensureHoldAllowed()
            }
            is CallAction.Dtmf -> {
                ensureCallOngoing()
                ensureSipIdValid()
            }
        }
    }


    private fun performAnswer() {
        if (sipId < 0) {
            SdkLogger.debug(TAG, "answer: INVITE not received yet, the answer is pending")
            pendingAnswer = true
        } else {
            try {
                delegate.answerCall(sipId)
            } catch (e: Exception) {
                disconnectWithReason(fromCode(-1, e.message))
                throw e
            }
        }
        updateState(CallState.Connecting)
    }


    private fun performMute(mute: Boolean) {
        if (isMuted == mute) return
        if (sipId >= 0) {
            try {
                delegate.muteCall(sipId, mute)
            } catch (e: IllegalStateException) {
                // No active audio media yet: the desired state is applied by
                // VoiceManager.onCallMediaState as soon as audio media activates.
                SdkLogger.debug(TAG, "mute $mute (pending): ${e.message}")
            }
        }
        isMuted = mute
        notify(LocalMediaEvent.MuteChanged(id, mute))
        sendMediaStateInfo()
    }


    /**
     * Also used by `VoiceManager` to hold this call when another one becomes active.
     * Idempotent. SIP thread only.
     */
    fun performHold(hold: Boolean) {
        SdkThreads.assertOnSip()
        if (state == CallState.Ongoing && isHoldSatisfied(hold)) return
        ensureHoldAllowed()

        if (hold) {
            holdVideoCount = if (videoState != VideoState.INACTIVE) 1L else 0L
            if (holdVideoCount == 1L) video.prepareForHold()
        }
        delegate.holdCall(sipId, hold, withVideo = holdVideoCount == 1L)
        holdTarget = hold
    }


    /** The call is in, or already on its way to, the requested hold state. */
    private fun isHoldSatisfied(hold: Boolean): Boolean = (holdTarget ?: isOnHold) == hold

    // endregion


    // region Media controls

    override fun setSpeakerphoneOn(enabled: Boolean): Result<Unit> {
        return try {
            ensureCallOngoing()
            delegate.setSpeakerphoneOn(enabled).onSuccess { syncSpeakerphone(enabled) }
        } catch (e: CallError) {
            Result.failure(e)
        }
    }


    /** Reports a speaker route change; called for every call when the route changes. */
    fun syncSpeakerphone(isOn: Boolean) {
        notify(LocalMediaEvent.SpeakerphoneChanged(id, isOn))
    }


    override fun attachVideoSurfaces(localSurface: Surface, remoteSurface: Surface): Result<Unit> {
        return enqueue("attachVideoSurfaces", validate = {
            ensureHasVideo()
            ensureSurfaceValid(localSurface, "Local")
            ensureSurfaceValid(remoteSurface, "Remote")
        }) {
            video.attachSurfaces(localSurface, remoteSurface)
        }
    }


    override fun attachLocalVideoSurface(surface: Surface): Result<Unit> {
        return enqueue("attachLocalVideoSurface", validate = {
            ensureHasVideo()
            ensureSurfaceValid(surface, "Local")
        }) {
            video.attachSurfaces(local = surface, remote = null)
        }
    }


    override fun attachRemoteVideoSurface(surface: Surface): Result<Unit> {
        return enqueue("attachRemoteVideoSurface", validate = {
            ensureHasVideo()
            ensureSurfaceValid(surface, "Remote")
        }) {
            video.attachSurfaces(local = null, remote = surface)
        }
    }


    override fun switchCamera(): Result<Unit> {
        return enqueue("switchCamera", validate = {
            ensureMediaControlAllowed()
            ensureSipIdValid()
            ensureHasVideo()
        }) {
            video.switchCamera()
        }
    }


    override fun enableVideo(): Result<Unit> {
        return enqueue("enableVideo", validate = {
            ensureCallOngoing()
            ensureSipIdValid()
        }) {
            if (videoState == VideoState.INACTIVE) delegate.setVideoEnabled(sipId, enabled = true)
        }
    }


    override fun disableVideo(): Result<Unit> {
        return enqueue("disableVideo", validate = {
            ensureCallOngoing()
            ensureSipIdValid()
        }) {
            if (videoState == VideoState.INACTIVE) return@enqueue
            delegate.setVideoEnabled(sipId, enabled = false)
            video.stopPreview()
            if (isLocalVideoPaused) {
                isLocalVideoPaused = false
                notify(LocalMediaEvent.VideoPausedChanged(id, false))
            }
            notify(VideoEvent.StateChanged(id, videoState))
        }
    }


    override fun setLocalVideoPaused(paused: Boolean): Result<Unit> {
        return enqueue("setLocalVideoPaused", validate = {
            ensureMediaControlAllowed()
            ensureSipIdValid()
            ensureHasVideo()
        }) {
            if (isLocalVideoPaused == paused) return@enqueue
            try {
                delegate.setLocalVideoTransmitting(sipId, transmit = !paused)
            } catch (e: IllegalStateException) {
                // No active local video yet: CallVideoController.onShowLocalVideo re-applies
                // the pause as soon as local video activates.
                SdkLogger.debug(TAG, "setLocalVideoPaused $paused (pending): ${e.message}")
            }
            isLocalVideoPaused = paused
            notify(LocalMediaEvent.VideoPausedChanged(id, paused))
            sendMediaStateInfo()
        }
    }


    override fun setVideoOrientation(orientation: VideoOrientation): Result<Unit> {
        return enqueue("setVideoOrientation", validate = { ensureCallOngoing() }) {
            video.setOrientation(orientation)
        }
    }


    /**
     * Validates synchronously so the caller gets `InvalidState` right away, then runs
     * [action] on the SIP thread, re-validating first. Errors there are only logged.
     */
    private fun enqueue(name: String, validate: () -> Unit, action: () -> Unit): Result<Unit> {
        try {
            validate()
        } catch (e: CallError) {
            return Result.failure(e)
        }
        SdkThreads.postSip {
            try {
                validate()
                action()
            } catch (e: Exception) {
                SdkLogger.error(TAG, "$name: ${e.message}")
            }
        }
        return Result.success(Unit)
    }


    private fun ensureSurfaceValid(surface: Surface, name: String) {
        if (!surface.isValid) throw CallError.InvalidState("$name Surface is not valid")
    }

    // endregion


    // region Listeners & rating

    override fun addEventListener(listener: CallEventListener) = listeners.add(listener)

    override fun removeEventListener(listener: CallEventListener) = listeners.remove(listener)



    // Independent of apiTask: rating happens after the call ended, so it must not touch
    // the call state or disconnect on failure.
    override fun isRatable(callback: (Result<Boolean>) -> Unit) {
        val meeting = meetingId?.takeIf { it.isNotBlank() }
        if (meeting == null) {
            callback(Result.success(false))
            return
        }
        backgroundScope.launch {
            callback(runCatching { ratingService.checkRatable(meeting) })
        }
    }


    override fun rate(satisfaction: String, callback: (Result<Unit>) -> Unit) {
        val meeting = meetingId?.takeIf { it.isNotBlank() }
        if (meeting == null) {
            callback(Result.failure(CallError.InvalidState("The call has no meetingId and cannot be rated")))
            return
        }
        backgroundScope.launch {
            callback(runCatching { ratingService.submitRating(meeting, satisfaction) })
        }
    }

    // endregion


    // region State (used by VoiceManager / DefaultVoiceClient)

    /** Thread-safe. A disconnected call never changes state again. */
    fun updateState(newState: CallState) {
        synchronized(stateLock) {
            val current = state
            if (current is CallState.Disconnected || current.isSameAs(newState)) return
            state = newState
            SdkLogger.debug(TAG, "state: $current -> $newState ($id)")
            notify(ConnectionEvent.StateChanged(id, newState))
        }
    }


    fun notify(event: CallEvent) = listeners.notify(event)


    /** Reports the state the call was created in, e.g. Ringing for an incoming call. */
    fun notifyInitialState() = notify(ConnectionEvent.StateChanged(id, state))


    /**
     * Runs the call setup (credentials, registration) off the SIP thread. Cancelled by
     * [cancelTask] when the call ends first.
     */
    fun apiTask(setConnecting: Boolean = true, block: suspend () -> Unit) {
        if (setConnecting) updateState(CallState.Connecting)
        val job = backgroundScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                SdkLogger.debug(TAG, "apiTask cancelled ($id)")
            }
        }
        apiJob.set(job)
    }


    fun cancelTask() {
        apiJob.getAndSet(null)?.cancel()
    }


    /** Any thread; the disconnect itself runs on the SIP thread. */
    fun disconnectWithReason(reason: CallEndReason) {
        SdkThreads.postSip {
            try {
                internalDisconnect(reason)
            } catch (e: Exception) {
                SdkLogger.warn(TAG, "disconnectWithReason: ${e.message}")
            }
        }
    }


    /** Best-effort report of the local media state to the remote party via SIP INFO. */
    fun sendMediaStateInfo() {
        if (state != CallState.Ongoing || sipId < 0) return
        try {
            delegate.sendMediaStateInfo(sipId, isMuted, isLocalVideoPaused, isOnHold)
        } catch (e: Exception) {
            SdkLogger.error(TAG, "sendMediaStateInfo: ${e.message}")
        }
    }


    private fun internalDisconnect(reason: CallEndReason, cancelTask: Boolean = false) {
        SdkThreads.assertOnSip()
        if (state is CallState.Disconnected) throw CallError.InvalidState("Call is already disconnected")

        cancelIncomingTimeout()
        if (sipId < 0) {
            if (cancelTask) cancelTask()
            delegate.disconnectCall(DisconnectTarget.Local(id))
            updateState(CallState.Disconnected(reason))
        } else {
            delegate.disconnectCall(DisconnectTarget.Sip(sipId))
        }
    }

    // endregion


    // region SIP events (SIP thread)

    /** Ends the incoming call with 408 if its INVITE doesn't arrive within [timeoutMs]. */
    fun startIncomingTimeout(timeoutMs: Long) {
        SdkThreads.postSip {
            incomingTimeout = SdkThreads.postSipDelayed(timeoutMs) {
                if (sipId >= 0 || state is CallState.Disconnected) return@postSipDelayed
                SdkLogger.warn(TAG, "Incoming call INVITE was not received within ${timeoutMs}ms ($id)")
                cancelTask()
                internalDisconnect(fromCode(408, "Incoming call was not received in time"))
            }
        }
    }


    /** The INVITE of this incoming call arrived and [sipId] is assigned. */
    fun onIncomingInviteReceived() {
        cancelIncomingTimeout()
        if (!pendingAnswer) return
        pendingAnswer = false
        try {
            delegate.answerCall(sipId)
        } catch (e: Exception) {
            SdkLogger.error(TAG, "onIncomingInviteReceived: the pending answer failed: ${e.message}")
            disconnectWithReason(fromCode(-1, e.message))
        }
    }


    fun onConfirmed() {
        if (!isAnswered) answeredAt = System.currentTimeMillis()
        updateState(CallState.Ongoing)
        sendMediaStateInfo()
        qualityMonitor.start()
    }


    fun onHoldChanged(onHold: Boolean) {
        holdTarget = null
        if (isOnHold == onHold) return
        isOnHold = onHold
        notify(LocalMediaEvent.HoldChanged(id, onHold))
        sendMediaStateInfo()
    }


    /** The remote media state from a SIP INFO packet; `null` fields are unchanged. */
    fun onRemoteMediaStateInfo(audioMuted: Boolean?, videoMuted: Boolean?, hold: Boolean?) {
        if (audioMuted != null && audioMuted != isRemoteMuted) {
            isRemoteMuted = audioMuted
            notify(RemoteMediaEvent.MuteChanged(id, audioMuted))
        }
        if (hold != null && hold != isRemoteOnHold) {
            isRemoteOnHold = hold
            notify(RemoteMediaEvent.HoldChanged(id, hold))
        }
        if (videoMuted != null && videoMuted != isRemoteVideoPaused) {
            isRemoteVideoPaused = videoMuted
            notify(RemoteMediaEvent.VideoPausedChanged(id, videoMuted))
        }
    }


    /**
     * The SIP call ended. Returns the deferred native video cleanup — see
     * [CallVideoController.onDisconnected].
     */
    fun onSipDisconnected(reason: CallEndReason): () -> Unit {
        cancelIncomingTimeout()
        pendingAnswer = false
        holdTarget = null
        val cleanup = video.onDisconnected()
        if (isLocalVideoPaused) isLocalVideoPaused = false
        qualityMonitor.stop()
        connectionQuality = ConnectionQuality.UNKNOWN
        updateState(CallState.Disconnected(reason))
        return cleanup
    }


    private fun cancelIncomingTimeout() {
        incomingTimeout?.cancel(false)
        incomingTimeout = null
    }


    override fun toString(): String = "Call(id=$id, sipId=$sipId, outgoing=$isOutgoing, state=$state)"


    companion object {
        const val NO_SIP_ID = -1
        private const val TAG = "CallImpl"

        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
