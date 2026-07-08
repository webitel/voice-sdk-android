# SDK Initialization

The entry point to the SDK is the `VoiceClient` class.

```kotlin
VoiceClient.Builder(
    application = application,
    address = "https://demo.webitel.com",
    token = "PORTAL_CLIENT_TOKEN"
)
    .logLevel(LogLevel.DEBUG)
    .appName("MyApp")
    .appVersion("1.0.0")
    .build()
```


## Required parameters

- `application` — Android `Application` instance
- `address` — server address of the voice service
- `token` — access token for authentication


## Optional parameters

- `user` — sets the authenticated user upfront, see [Authentication](authentication.md)
- `logLevel` — logging level, default `LogLevel.ERROR`
- `appName` — application display name
- `appVersion` — application version
- `deviceId` — unique device identifier
- `callSettings` — advanced network/transport configuration, see below


## Log levels

```kotlin
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, OFF
}
```

- `DEBUG` — all messages are logged
- `INFO` — informational, warning, and error messages
- `WARN` — warning and error messages
- `ERROR` — only error messages (default)
- `OFF` — disables all logs


## Advanced: Network Configuration

`CallSettings` allows overriding transport, NAT traversal, and media encryption
defaults. Only change these if your network environment requires it.

```kotlin
val settings = CallSettings().apply {
    transport = CallSettings.TransportUse.TCP
    natIceEnabled = true
    srtpUse = CallSettings.SrtpUse.OPTIONAL
}

VoiceClient.Builder(application, address, token)
    .callSettings(settings)
    .build()
```

### Transport

```kotlin
enum class TransportUse {
    UDP, TCP, TCP_UDP, TLS
}
```

- `transport` — network transport used for call signaling (default `TCP_UDP`)


### NAT traversal

```kotlin
enum class MediaStunUse {
    DEFAULT, DISABLED, RETRY_ON_FAILURE
}
```

- `natMediaStunUse` — STUN usage for media (RTP) traffic behind NAT
- `natSipStunUse` — STUN usage for call signaling traffic behind NAT
- `natIceEnabled` — enables ICE for NAT traversal (default `false`)
- `natSdpNatRewriteUse` — rewrite NAT'd addresses in the session description
- `natContactRewriteUse` — rewrite the contact address behind NAT (default `true`)
- `natViaRewriteUse` — rewrite the via address behind NAT (default `true`)


### Media encryption

```kotlin
enum class SrtpUse {
    DISABLED, OPTIONAL, MANDATORY
}
```

- `srtpUse` — SRTP usage policy, default `DISABLED`
- `srtpSecureSignaling` — required signaling security level for SRTP to apply


### Other

- `busyEverywhereUse` — report "busy everywhere" instead of "busy here" when declining a call
- `codecPriority` — codec priority overrides, keyed by codec identifier (e.g. `"opus/48000/2"`)
