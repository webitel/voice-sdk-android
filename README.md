# **Webitel Voice SDK – Android**


## Overview

Webitel Voice SDK provides a simple way to integrate voice and video calling functionality into your Android applications.  

It offers built-in support for:  
  • User authentication  
  • Call control (mute, hold, digits, speakerphone, etc.)  
  • Real-time audio and video streaming  
  • Upgrading / downgrading video during a call  
  • Call state, media and video event tracking  
  • Call rating


## Installation

1.	Add `JitPack` to your root `build.gradle` (if not already added):
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

2. Add the SDK dependency to your module `build.gradle`:
```groovy
dependencies {
    implementation 'com.github.webitel:voice-sdk-android:<latest-version>'
}
```
> Replace <latest-version/> with the latest release.


## 🚀 Getting Started


### Initialize the SDK

Before making calls, initialize the SDK by building a `VoiceClient` instance:
```kotlin
val voiceClient = VoiceClient.Builder(
    application = application,
    address = "https://demo.webitel.com",
    token = "PORTAL_CLIENT_TOKEN"
)
    .logLevel(LogLevel.DEBUG) // Optional
    .build()
```
> Optional parameters: `user`, `deviceId`, `logLevel`, `callSettings`


### Authentication

You can authenticate using one of the two supported methods:

#### Option 1 – via User Object

Pass a structured User object to the client:
```kotlin
val user = User.Builder(
    iss = "https://demo.webitel.com/portal",
    sub = "user-123",
    name = "John Smith"
).build()

voiceClient.setUser(user)
```

#### Option 2 – via JWT Token:

You can authenticate with a raw JWT string either before or during the call.
```kotlin
// Set JWT globally
voiceClient.setUserJWT("your-jwt-token")
```
or
```kotlin
// Provide JWT directly when starting the call
voiceClient.makeCall(jwt = "your-jwt-token", listener = listener)
```
> Both options will authorize the user before initiating the call.


### Make a Call

```kotlin
val call = voiceClient.makeCall(listener = listener)
```

### Video Call

Start a call with video, or upgrade an ongoing audio call:
```kotlin
val call = voiceClient.makeCall(
    options = CallOptions(type = CallType.VIDEO),
    listener = listener
)

call.attachVideoSurfaces(localSurface, remoteSurface)

call.enableVideo()   // audio → video
call.disableVideo()  // video → audio
call.switchCamera()  // front ↔ back
```
> The SDK does not declare the `CAMERA` permission — add it to your app's manifest and request it at runtime before using video.

See [Video](docs/video.md) for surfaces, orientation, quality presets and local video pause.

### Call Events

```kotlin
val listener = CallEventListener { event ->
    when (event) {
        is ConnectionEvent.StateChanged -> { /* ringing, ongoing, disconnected... */ }
        is LocalMediaEvent -> { /* mute, hold, speakerphone, video pause */ }
        is VideoEvent -> { /* video state, frame size */ }
        is RemoteMediaEvent -> { /* remote mute, hold, video pause */ }
    }
}
```
> See [Events](docs/events.md) for the full event reference.

### Call Controls

The SDK provides methods to manage active calls.
Each method returns a `Result<Unit>`, allowing you to handle success or failure via `onSuccess` / `onFailure`.

#### Sending DTMF Tones

```kotlin
call.sendDTMF(value)
    .onSuccess { Log.d(TAG, "DTMF sent: $value") }
    .onFailure { Log.e(TAG, "DTMF error: ${it.message}", it) }
```

#### Mute / Unmute Microphone

```kotlin
call.mute(true)
    .onSuccess { Log.d(TAG, "Microphone muted") }
    .onFailure { Log.e(TAG, "Mute error: ${it.message}", it) }
```

#### Hold / Resume Call

```kotlin
call.hold(true)
    .onSuccess { Log.d(TAG, "Call held") }
    .onFailure { Log.e(TAG, "Hold error: ${it.message}", it) }
```

#### Speakerphone

```kotlin
call.setSpeakerphoneOn(true)
    .onSuccess { Log.d(TAG, "Speakerphone on") }
    .onFailure { Log.e(TAG, "Speakerphone error: ${it.message}", it) }
```

#### Disconnect Call

```kotlin
call.disconnect()
    .onSuccess { Log.d(TAG, "Call ended") }
    .onFailure { Log.e(TAG, "Disconnect error: ${it.message}", it) }
```


#### Rate Call

Available only for calls created with `CallOptions.meetingId`:
```kotlin
call.isRatable { result ->
    if (result.getOrDefault(false)) call.rate("5") { /* Result<Unit> */ }
}
```


## Documentation

- [Initialization](docs/initialization.md)
- [Authentication](docs/authentication.md)
- [Calls](docs/calls.md)
- [Video](docs/video.md)
- [Call State](docs/call-state.md)
- [Events](docs/events.md)
- [Session](docs/session.md)
