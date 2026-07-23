package com.webitel.voice.sdk


/**
 * A unified event type emitted by [CallEventListener.onEvent].
 *
 * Every event carries [callId] so it can be correlated with a specific [Call] instance
 * without first matching on the concrete subtype — useful for logging, routing, or
 * Flutter `EventChannel` serialization.
 *
 * Events are grouped by nature of the change:
 *  - [ConnectionEvent] — the call's connection lifecycle.
 *  - [LocalMediaEvent] — local media controls toggled through [Call] methods.
 *  - [VideoEvent] — combined video activity and stream geometry.
 *  - [RemoteMediaEvent] — the remote party's media state, reported via SIP INFO.
 */
sealed interface CallEvent {
    val callId: String
}


/** The call's connection lifecycle. */
sealed class ConnectionEvent : CallEvent {

    /** The call moved to a new [CallState] (e.g. ringing, connecting, ongoing, disconnected). */
    data class StateChanged(
        override val callId: String,
        val state: CallState
    ) : ConnectionEvent()
}


/**
 * Local-side call controls, changed as a result of calling methods on this [Call]
 * ([Call.hold], [Call.mute], [Call.setSpeakerphoneOn], [Call.setLocalVideoPaused]).
 */
sealed class LocalMediaEvent : CallEvent {

    /** The hold status changed. */
    data class HoldChanged(
        override val callId: String,
        val isOnHold: Boolean
    ) : LocalMediaEvent()

    /** The local microphone mute state changed. */
    data class MuteChanged(
        override val callId: String,
        val isMuted: Boolean
    ) : LocalMediaEvent()

    /** Call audio routing to/from the built-in speaker changed. */
    data class SpeakerphoneChanged(
        override val callId: String,
        val isSpeakerphoneOn: Boolean
    ) : LocalMediaEvent()

    /** Local video transmission was paused or resumed via [Call.setLocalVideoPaused]. */
    data class VideoPausedChanged(
        override val callId: String,
        val isPaused: Boolean
    ) : LocalMediaEvent()
}


/** Video stream activity and geometry — combined for both local capture and remote decode. */
sealed class VideoEvent : CallEvent {

    /** The combined video activity state changed. */
    data class StateChanged(
        override val callId: String,
        val state: VideoState
    ) : VideoEvent()

    /** The real pixel dimensions of a video stream changed (local capture or remote decode). */
    data class SizeChanged(
        override val callId: String,
        val isLocal: Boolean,
        val width: Int,
        val height: Int
    ) : VideoEvent()
}


/**
 * The remote party's media state, reported via a SIP INFO packet (not a renegotiation).
 * Mirrors [LocalMediaEvent] for the other side — there is no remote equivalent of
 * [LocalMediaEvent.SpeakerphoneChanged], a purely local audio-routing decision.
 */
sealed class RemoteMediaEvent : CallEvent {

    /** The remote party's hold status changed. */
    data class HoldChanged(
        override val callId: String,
        val isOnHold: Boolean
    ) : RemoteMediaEvent()

    /** The remote party's microphone mute state changed. */
    data class MuteChanged(
        override val callId: String,
        val isMuted: Boolean
    ) : RemoteMediaEvent()

    /** The remote party paused or resumed sending video. */
    data class VideoPausedChanged(
        override val callId: String,
        val isPaused: Boolean
    ) : RemoteMediaEvent()
}
