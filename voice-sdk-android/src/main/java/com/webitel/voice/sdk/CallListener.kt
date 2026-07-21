package com.webitel.voice.sdk


/**
 * Listener interface for receiving updates about a call's state and properties.
 *
 * All methods except [onCallStateChanged] have default empty implementations.
 * Override only the events your application cares about.
 *
 * For Flutter EventChannel bridging, implement only [onCallEvent] — it receives every event
 * as a sealed [CallEvent] that can be serialized to a Map. For native Android usage, the
 * individual callback methods provide a more idiomatic API.
 */
interface CallListener {

    /**
     * Called when the call connection state changes.
     *
     * This is the only required method; all others have default empty implementations.
     *
     * @param call  the call whose state has changed
     * @param state the new state of the call
     */
    fun onCallStateChanged(call: Call, state: CallState)


    /**
     * Unified event stream entry point.
     *
     * Fires for every call event (state change, hold, mute, video) before the corresponding
     * individual callback.
     *
     * @param event a sealed [CallEvent] describing what changed
     */
    fun onCallEvent(event: CallEvent) {}


    /**
     * Called when the hold status of the call changes.
     *
     * @param call     the call whose hold state changed
     * @param isOnHold true if the call is now on hold, false otherwise
     */
    fun onHoldChanged(call: Call, isOnHold: Boolean) {}


    /**
     * Called when the local microphone mute state changes.
     *
     * Fires after a successful [Call.mute] invocation.
     *
     * @param call    the affected call
     * @param isMuted true if the microphone is now muted
     */
    fun onMuteChanged(call: Call, isMuted: Boolean) {}


    /**
     * Called when call audio routing to/from the built-in speaker changes.
     *
     * Fires after a successful [Call.setSpeakerphoneOn] invocation.
     *
     * @param call              the affected call
     * @param isSpeakerphoneOn true if call audio is now routed to the built-in speaker
     */
    fun onSpeakerphoneChanged(call: Call, isSpeakerphoneOn: Boolean) {}


    /**
     * Called when the combined video activity state of the call changes.
     *
     * Fires when the local camera starts or stops transmitting, or when the remote
     * video stream starts or stops arriving.
     *
     * @param call  the affected call
     * @param state the new [VideoState] describing which streams are active
     */
    fun onVideoStateChanged(call: Call, state: VideoState) {}


    /**
     * Called when the real pixel dimensions of a video stream become known or change.
     *
     * Fires for local camera capture (after preview starts, and again after [Call.switchCamera]
     * if the new camera's native resolution differs) and for remote decoded video (whenever
     * PJSIP reports PJMEDIA_EVENT_FMT_CHANGED with new dimensions).
     *
     * @param call    the affected call
     * @param isLocal true for the local camera preview, false for the remote decoded stream
     * @param width   real pixel width of the stream
     * @param height  real pixel height of the stream
     */
    fun onVideoSizeChanged(call: Call, isLocal: Boolean, width: Int, height: Int) {}
}