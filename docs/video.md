# Video

A call's media type (`CallOptions.type`) is set at creation and doesn't
change. The live video activity is tracked separately via `Call.videoState`,
since a call started as `CallType.AUDIO` can be upgraded to active video via
`enableVideo()`.


## VideoState

```kotlin
enum class VideoState {
    /** No video streams are active in either direction. */
    INACTIVE,

    /** Only the local camera is transmitting; no remote video is being received. */
    LOCAL_ONLY,

    /** Remote video is being received; local camera is not transmitting. */
    REMOTE_ONLY,

    /** Both local and remote video streams are active. */
    ACTIVE
}
```

Changes are reported via `CallEventListener.onEvent` with a `VideoEvent.StateChanged`
event, see [Events](events.md).


## Enable / disable video

```kotlin
call.enableVideo()
    .onSuccess { }
    .onFailure { error -> }
```

```kotlin
call.disableVideo()
    .onSuccess { }
    .onFailure { error -> }
```

Behavior

- both are asynchronous — they renegotiate the call with the remote party
- a `VideoEvent.StateChanged` event fires once the change completes
- calling either method when the call is already in that state returns success immediately
- both require the call to be in `CallState.Ongoing`


## Rendering surfaces

```kotlin
call.attachVideoSurfaces(localSurface, remoteSurface)
call.attachLocalVideoSurface(localSurface)
call.attachRemoteVideoSurface(remoteSurface)
```

- `attachVideoSurfaces` — use when both surfaces are available at once, e.g. after a configuration change or activity recreation
- `attachLocalVideoSurface` / `attachRemoteVideoSurface` — use when only one surface changes, e.g. a single `SurfaceView` is recreated while the other remains valid


## Switch camera

```kotlin
call.switchCamera()
```

Switches the active camera (front ↔ back) during an active video call.


## Orientation

```kotlin
call.setVideoOrientation(VideoOrientation.LANDSCAPE_RIGHT)
```

Call this when the device display orientation changes, e.g. inside a
`DisplayManager.DisplayListener` callback. Adjusts camera capture orientation
and encoder dimensions without renegotiating the call.

```kotlin
enum class VideoOrientation(val rotation: Int) {
    /** Portrait, device natural position. Default for mobile. */
    PORTRAIT(Surface.ROTATION_0),

    /** Landscape, home button on the right. */
    LANDSCAPE_RIGHT(Surface.ROTATION_90),

    /** Reverse portrait, device upside down. */
    PORTRAIT_REVERSED(Surface.ROTATION_180),

    /** Landscape, home button on the left. */
    LANDSCAPE_LEFT(Surface.ROTATION_270);

    /** True for [LANDSCAPE_RIGHT] and [LANDSCAPE_LEFT]. */
    val isLandscape: Boolean
}
```

Use `VideoOrientation.fromRotation(surfaceRotation)` to convert the current
display rotation at runtime.


## Video quality presets

```kotlin
enum class VideoQuality(
    val width: Long,
    val height: Long,
    val fps: Int,
    val avgBitrate: Long, // in bits per second
    val maxBitrate: Long  // in bits per second
) {
    LOW(180, 320, 15, 150_000, 300_000),
    MEDIUM(360, 640, 20, 300_000, 600_000),
    STANDARD(540, 960, 30, 512_000, 1_024_000),
    HIGH(720, 1280, 30, 1_200_000, 2_048_000),
    FULL_HD(1080, 1920, 30, 3_000_000, 4_000_000)
}
```

- dimensions represent the **portrait** orientation (width < height); the SDK
  swaps them automatically in landscape based on `VideoOrientation`
- `VideoQuality.DEFAULT` — `MEDIUM`, the recommended preset for most calls
- `VideoQuality.fromName(name)` — reconstructs a preset from its string name
  (useful for Flutter `MethodChannel` serialization), falls back to `DEFAULT`
  if unrecognized
