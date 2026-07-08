# **Webitel Voice SDK – Android**


## Overview

Webitel Voice SDK provides a simple way to integrate voice calling functionality into your Android applications.  

It offers built-in support for:  
  • User authentication  
  • Call control (mute, hold, digits, etc.)  
  • Real-time audio streaming  
  • Call state and event tracking


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
> Optional parameters: deviceId, appName, appVersion, user


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
voiceClient.makeAudioCall("your-jwt-token", callListener)
```
> Both options will authorize the user before initiating the call.


### Make a Call

```kotlin
val call = voiceClient.makeAudioCall(callListener)
```

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

#### Disconnect Call

```kotlin
call.disconnect()
    .onSuccess { Log.d(TAG, "Call ended") }
    .onFailure { Log.e(TAG, "Disconnect error: ${it.message}", it) }
```


## Documentation

- [Initialization](docs/initialization.md)
- [Authentication](docs/authentication.md)
- [Calls](docs/calls.md)
- [Video](docs/video.md)
- [Call State](docs/call-state.md)
- [Events](docs/events.md)
- [Session](docs/session.md)
