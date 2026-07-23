# Events

To receive call events, register a `CallEventListener` on a `Call` instance:

```kotlin
call.addEventListener(this)
```

`CallEventListener` is a single-method (SAM) interface, so a lambda works too:

```kotlin
call.addEventListener { event ->
    when (event) {
        is ConnectionEvent.StateChanged -> { }
        is LocalMediaEvent -> { }
        is VideoEvent -> { }
        is RemoteMediaEvent -> { }
    }
}
```


## Interface

```kotlin
fun interface CallEventListener {
    /** Called for every call event. */
    fun onEvent(event: CallEvent)
}
```

For Flutter `EventChannel` bridging, `event` serializes directly to a `Map` — that's
the reason this stays one unified callback instead of a method per event type.


## Event model

Every event carries `callId`, so it can be correlated with a specific `Call` instance
without matching on the concrete subtype first. Events are grouped by the nature of
the change:

```kotlin
sealed interface CallEvent {
    val callId: String
}

/** The call's connection lifecycle. */
sealed class ConnectionEvent : CallEvent {
    /** The call moved to a new [CallState] (e.g. ringing, connecting, ongoing, disconnected). */
    data class StateChanged(override val callId: String, val state: CallState) : ConnectionEvent()
}

/**
 * Local-side call controls, changed as a result of calling methods on this [Call]
 * ([Call.hold], [Call.mute], [Call.setSpeakerphoneOn], [Call.setLocalVideoPaused]).
 */
sealed class LocalMediaEvent : CallEvent {
    /** The hold status changed. */
    data class HoldChanged(override val callId: String, val isOnHold: Boolean) : LocalMediaEvent()

    /** The local microphone mute state changed. */
    data class MuteChanged(override val callId: String, val isMuted: Boolean) : LocalMediaEvent()

    /** Call audio routing to/from the built-in speaker changed. */
    data class SpeakerphoneChanged(override val callId: String, val isSpeakerphoneOn: Boolean) : LocalMediaEvent()

    /** Local video transmission was paused or resumed via [Call.setLocalVideoPaused]. */
    data class VideoPausedChanged(override val callId: String, val isPaused: Boolean) : LocalMediaEvent()
}

/** Video stream activity and geometry — combined for both local capture and remote decode. */
sealed class VideoEvent : CallEvent {
    /** The combined video activity state changed. */
    data class StateChanged(override val callId: String, val state: VideoState) : VideoEvent()

    /** The real pixel dimensions of a video stream changed (local capture or remote decode). */
    data class SizeChanged(override val callId: String, val isLocal: Boolean, val width: Int, val height: Int) : VideoEvent()
}

/**
 * The remote party's media state, reported via a SIP INFO packet (not a renegotiation).
 * Mirrors [LocalMediaEvent] for the other side — there is no remote equivalent of
 * `SpeakerphoneChanged`, a purely local audio-routing decision.
 */
sealed class RemoteMediaEvent : CallEvent {
    /** The remote party's hold status changed. */
    data class HoldChanged(override val callId: String, val isOnHold: Boolean) : RemoteMediaEvent()

    /** The remote party's microphone mute state changed. */
    data class MuteChanged(override val callId: String, val isMuted: Boolean) : RemoteMediaEvent()

    /** The remote party paused or resumed sending video. */
    data class VideoPausedChanged(override val callId: String, val isPaused: Boolean) : RemoteMediaEvent()
}
```


## Handling events

```kotlin
override fun onEvent(event: CallEvent) {
    when (event) {
        is ConnectionEvent.StateChanged -> { }
        is LocalMediaEvent.HoldChanged -> { }
        is LocalMediaEvent.MuteChanged -> { }
        is LocalMediaEvent.SpeakerphoneChanged -> { }
        is LocalMediaEvent.VideoPausedChanged -> { }
        is VideoEvent.StateChanged -> { }
        is VideoEvent.SizeChanged -> { }
        is RemoteMediaEvent.HoldChanged -> { }
        is RemoteMediaEvent.MuteChanged -> { }
        is RemoteMediaEvent.VideoPausedChanged -> { }
    }
}
```

`ConnectionEvent.StateChanged` is the most important event — make sure to handle it
even in a non-exhaustive `when`.
