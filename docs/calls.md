# Calls

## Making a call

```kotlin
val call = voiceClient.makeCall(options = CallOptions(), listener = callListener)
```

or with an inline JWT token, see [Authentication](authentication.md):

```kotlin
val call = voiceClient.makeCall(jwt = "your-jwt-token", options = CallOptions(), listener = callListener)
```


## CallOptions

```kotlin
data class CallOptions(
    val type: CallType = CallType.AUDIO,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    val videoOrientation: VideoOrientation = VideoOrientation.PORTRAIT,
    val meetingId: String? = null,
    val toNumber: String = "service",
    val toName: String = "Service"
)

enum class CallType {
    AUDIO, VIDEO
}
```

- `type` — media type to start the call with, see [Video](video.md)
- `videoQuality` / `videoOrientation` — initial video settings, used when `type` is `CallType.VIDEO`
- `meetingId` — identifier of the meeting/room to join, if applicable
- `toNumber` / `toName` — destination address and display name


## Call model

```kotlin
interface Call {

    /** Unique identifier for this call, constant for its lifetime. */
    val id: String

    /** Current state of the call. */
    val state: CallState

    /** Whether the local microphone is muted. */
    val isMuted: Boolean

    /** Whether the call is currently on hold. */
    val isOnHold: Boolean

    /** Timestamp in milliseconds when the call was answered. */
    val answeredAt: Long

    /** True if the call was initiated by the local user (outgoing). */
    val isOutgoing: Boolean

    /** The media type the call was initiated with. */
    val type: CallType
}
```

See [Call State](call-state.md) for `CallState`, and [Video](video.md) for
video-specific properties (`videoState`) and methods.


## Call controls

Each method returns `Result<Unit>`, handled via `onSuccess` / `onFailure`.

### Mute / Unmute

```kotlin
call.mute(true)
    .onSuccess { }
    .onFailure { error -> }
```

### Hold / Resume

```kotlin
call.hold(true)
    .onSuccess { }
    .onFailure { error -> }
```

### Send DTMF

```kotlin
call.sendDTMF("123#")
    .onSuccess { }
    .onFailure { error -> }
```

### Disconnect

```kotlin
call.disconnect()
    .onSuccess { }
    .onFailure { error -> }
```


## Listeners

```kotlin
call.addListener(listener)
call.removeListener(listener)
call.removeAllListeners()
```

See [Events](events.md) for the full `CallListener` / `CallEvent` reference.
