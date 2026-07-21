package com.webitel.voice.sdk


/**
 * A unified event type emitted by [CallListener.onCallEvent].
 *
 * Designed so a single listener method receives all events.
 */
sealed class CallEvent {

    /** The call connection state changed. */
    data class StateChanged(
        val callId: String,
        val state: CallState
    ) : CallEvent()

    /** The hold status changed. */
    data class HoldChanged(
        val callId: String,
        val isOnHold: Boolean
    ) : CallEvent()

    /** The local microphone mute state changed. */
    data class MuteChanged(
        val callId: String,
        val isMuted: Boolean
    ) : CallEvent()

    /** Call audio routing to/from the built-in speaker changed. */
    data class SpeakerphoneChanged(
        val callId: String,
        val isSpeakerphoneOn: Boolean
    ) : CallEvent()

    /** The combined video activity state changed. */
    data class VideoStateChanged(
        val callId: String,
        val state: VideoState
    ) : CallEvent()
}
