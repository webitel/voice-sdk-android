package com.webitel.voice.sdk

import android.view.Surface


/**
 * Represents a phone call and its current state.
 */
interface Call {

    /**
     * Unique identifier for this call.
     *
     * Remains constant for the entire lifetime of the call. Use this to correlate
     * [CallEvent] payloads with a specific Call instance
     */
    val id: String

    /** Current state of the call (e.g., ringing, active, ended). */
    val state: CallState

    /** Indicates whether the local microphone is muted. */
    val isMuted: Boolean

    /** True if call audio is currently routed to the built-in speaker via [setSpeakerphoneOn]. */
    val isSpeakerphoneOn: Boolean

    /** Indicates whether the call is currently on hold. */
    val isOnHold: Boolean

    /** Timestamp in milliseconds when the call was answered. */
    val answeredAt: Long

    /** True if the call was initiated by the local user (outgoing). */
    val isOutgoing: Boolean

    /** The media type the call was initiated with. See [videoState] for the live video activity. */
    val type: CallType

    /**
     * The current video activity state of the call.
     *
     * This reflects the live state of video streams regardless of how the call was initiated.
     * A call started as [CallType.AUDIO] can have [VideoState.ACTIVE] after [enableVideo] is called.
     *
     * Changes are also reported via [CallListener.onVideoStateChanged].
     */
    val videoState: VideoState

    /**
     * True while local video is paused via [setLocalVideoPaused] — the video session stays
     * fully negotiated with the remote party (no re-INVITE), but no new locally-captured
     * frames are being sent. Independent of [videoState].
     */
    val isLocalVideoPaused: Boolean


    /**
     * Attempts to mute or unmute the ongoing call.
     *
     * @param mute true to mute the call, false to unmute.
     * @return Result.success(Unit) if the operation was successful, or Result.failure with the error.
     */
    fun mute(mute: Boolean): Result<Unit>


    /**
     * Routes call audio to the built-in speaker, or back to the default device (earpiece /
     * already-connected wired or Bluetooth device).
     *
     * @param enabled true to route to the speaker, false to return to the default device.
     * @return Result.success(Unit) if applied, or Result.failure if the call isn't Ongoing, or
     *         if enabling the speaker is ignored because a wired/Bluetooth device is connected.
     */
    fun setSpeakerphoneOn(enabled: Boolean): Result<Unit>


    /**
     * Attempts to place the ongoing call on hold or resume it.
     *
     * @param hold true to hold the call, false to resume.
     * @return Result.success(Unit) if the operation was successful, or Result.failure with the error.
     */
    fun hold(hold: Boolean): Result<Unit>


    /**
     * Sends DTMF (Dual-Tone Multi-Frequency) digits during an active call.
     *
     * @param digits a string of digits (e.g., "123#*").
     * @return Result.success(Unit) if the digits were sent successfully, or Result.failure with the error.
     */
    fun sendDTMF(digits: String): Result<Unit>


    /**
     * Attaches both video rendering surfaces for an active video call.
     *
     * Use this when both local and remote surfaces are available simultaneously — for example,
     * after a configuration change or activity recreation. For cases where only one surface
     * changes, prefer [attachLocalVideoSurface] or [attachRemoteVideoSurface].
     *
     * @param localSurface The [Surface] for rendering the local camera preview.
     * @param remoteSurface The [Surface] for displaying the remote video stream.
     *
     * @return `Result.success(Unit)` if surfaces were successfully attached,
     *         or `Result.failure(Throwable)` if the call has no active video or surfaces are invalid.
     */
    fun attachVideoSurfaces(localSurface: Surface, remoteSurface: Surface): Result<Unit>


    /**
     * Attaches or replaces the local camera preview surface.
     *
     * Use when only the local surface changes — e.g. after a `SurfaceView` for the local
     * preview is recreated while the remote view remains valid. Restarts the camera preview
     * pipeline without touching the remote video stream.
     *
     * @param surface The new [Surface] for the local camera preview.
     *
     * @return `Result.success(Unit)` if the surface was successfully attached,
     *         or `Result.failure(Throwable)` if the call has no active video or the surface is invalid.
     */
    fun attachLocalVideoSurface(surface: Surface): Result<Unit>


    /**
     * Attaches or replaces the remote video rendering surface.
     *
     * Use when only the remote surface changes — e.g. after a `SurfaceView` for the remote
     * stream is recreated while the local preview remains valid.
     *
     * @param surface The new [Surface] for the remote video stream.
     *
     * @return `Result.success(Unit)` if the surface was successfully attached,
     *         or `Result.failure(Throwable)` if the call has no active video or the surface is invalid.
     */
    fun attachRemoteVideoSurface(surface: Surface): Result<Unit>


    /**
     * Switches the active video camera (front <-> back) during an active video call.
     *
     * @return [Result.success] if the camera was successfully switched,
     * or [Result.failure] if switching failed (e.g., no secondary camera available).
     */
    fun switchCamera(): Result<Unit>


    /**
     * Upgrades an audio-only call to a video call.
     *
     * This is an asynchronous operation — it renegotiates the call with the remote party.
     * When the remote party accepts, [CallListener.onVideoStateChanged] fires with the new
     * [VideoState].
     *
     * After receiving the [VideoState.ACTIVE] (or [VideoState.LOCAL_ONLY]) event, attach
     * rendering surfaces via [attachVideoSurfaces].
     *
     * Calling this on a call that already has video active returns [Result.success] immediately.
     *
     * @return [Result.success] if the renegotiation request was sent.
     *         [Result.failure] if the call is not [CallState.Ongoing] or the operation failed.
     */
    fun enableVideo(): Result<Unit>


    /**
     * Downgrades a video call to audio-only.
     *
     * Stops local camera capture and detaches rendering surfaces. Once the renegotiation
     * with the remote party completes, [CallListener.onVideoStateChanged] fires with
     * [VideoState.INACTIVE].
     *
     * Calling this on a call that is already audio-only returns [Result.success] immediately.
     *
     * @return [Result.success] if the renegotiation request was sent or was unnecessary.
     *         [Result.failure] if the call is not [CallState.Ongoing].
     */
    fun disableVideo(): Result<Unit>


    /**
     * Pauses or resumes sending local camera video to the remote party without renegotiating
     * the call (no SIP re-INVITE) and without tearing down the camera/preview pipeline the way
     * [disableVideo] does.
     *
     * While paused: the local camera keeps capturing and the local self-view preview surface
     * keeps rendering the live feed; only the outgoing network stream stops. The remote
     * party's own video session, and what they're sending to us, are unaffected. [videoState]
     * does not change — observe [isLocalVideoPaused] and
     * [CallListener.onLocalVideoPausedChanged] / [CallEvent.LocalVideoPausedChanged] instead.
     *
     * No-op returning [Result.success] if [paused] already matches [isLocalVideoPaused].
     *
     * @param paused true to stop transmitting local video, false to resume transmitting.
     * @return [Result.failure] if the call is not [CallState.Ongoing] or there is no active
     *         local video stream to pause/resume (i.e. [videoState] is [VideoState.INACTIVE]
     *         or [VideoState.REMOTE_ONLY]).
     */
    fun setLocalVideoPaused(paused: Boolean): Result<Unit>


    /**
     * Updates the video encoding orientation for an active call.
     *
     * Call this when the device display orientation changes — e.g. inside a
     * `DisplayManager.DisplayListener` callback. The SDK adjusts the camera capture
     * orientation and encoder dimensions without renegotiating the call.
     *
     * - [VideoOrientation.PORTRAIT] (default): 9:16 encoding, phone held upright.
     * - [VideoOrientation.LANDSCAPE]: 16:9 encoding, phone rotated sideways.
     *
     * @param orientation the new device orientation
     * @return [Result.success] if applied, [Result.failure] if the call is not [CallState.Ongoing].
     */
    fun setVideoOrientation(orientation: VideoOrientation): Result<Unit>


    /**
     * Attempts to disconnect (hang up) the current call.
     *
     * @return Result.success(Unit) if the call was disconnected successfully, or Result.failure with the error.
     */
    fun disconnect(): Result<Unit>


    /**
     * Adds a listener to receive updates about the call's state.
     *
     * @param listener the listener to add
     */
    fun addListener(listener: CallListener)


    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    fun removeListener(listener: CallListener)


    /**
     * Removes all registered listeners.
     */
    fun removeAllListeners()


    /**
     * Checks whether this call is eligible for rating.
     *
     * Only calls created with a `meetingId` (see `CallOptions.meetingId`) can be rated.
     * If no `meetingId` was set, [callback] is invoked immediately with `Result.success(false)`
     * and no network request is made. Otherwise this performs a network request to the backend.
     *
     * @param callback invoked with the result once the check completes.
     */
    fun isRatable(callback: (Result<Boolean>) -> Unit)


    /**
     * Submits a rating for this call.
     *
     * @param satisfaction the rating value, passed through to the backend as-is (no validation
     *        or constraint on its contents is applied by the SDK).
     * @param callback invoked with the result once the submission completes.
     *
     * @return via [callback] `Result.failure` if the call has no `meetingId` (see [isRatable]),
     *         or the outcome of the network request otherwise.
     */
    fun rate(satisfaction: String, callback: (Result<Unit>) -> Unit)
}