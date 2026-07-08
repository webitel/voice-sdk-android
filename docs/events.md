# Events

To receive call state and property updates, register a `CallListener` on a
`Call` instance:

```kotlin
call.addListener(this)
```


## Interface

```kotlin
interface CallListener {
    /** Called when the call connection state changes. The only required method. */
    fun onCallStateChanged(call: Call, state: CallState)

    /** Unified event stream entry point. Fires for every event before the corresponding individual callback. */
    fun onCallEvent(event: CallEvent) {}

    /** Called when the hold status of the call changes. */
    fun onHoldChanged(call: Call, isOnHold: Boolean) {}

    /** Called when the local microphone mute state changes. */
    fun onMuteChanged(call: Call, isMuted: Boolean) {}

    /** Called when the combined video activity state of the call changes. */
    fun onVideoStateChanged(call: Call, state: VideoState) {}
}
```

All methods except `onCallStateChanged` have default empty implementations —
override only what you need.

For Flutter `EventChannel` bridging, implement only `onCallEvent` — it
receives every event as a sealed `CallEvent` that can be serialized to a
`Map`. For native Android usage, the individual callback methods provide a
more idiomatic API.


## Event model

```kotlin
sealed class CallEvent {
    /** The call connection state changed. */
    data class StateChanged(val callId: String, val state: CallState) : CallEvent()

    /** The hold status changed. */
    data class HoldChanged(val callId: String, val isOnHold: Boolean) : CallEvent()

    /** The local microphone mute state changed. */
    data class MuteChanged(val callId: String, val isMuted: Boolean) : CallEvent()

    /** The combined video activity state changed. */
    data class VideoStateChanged(val callId: String, val state: VideoState) : CallEvent()
}
```


## Handling events

```kotlin
override fun onCallEvent(event: CallEvent) {
    when (event) {
        is CallEvent.StateChanged -> { }
        is CallEvent.HoldChanged -> { }
        is CallEvent.MuteChanged -> { }
        is CallEvent.VideoStateChanged -> { }
    }
}
```
