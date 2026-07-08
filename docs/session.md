# Session

Shut down:

```kotlin
voiceClient.shutdown {
    // shutdown complete
}
```

Behavior

- all active calls are terminated
- the internal call session is fully reset
- if credentials remain the same, the SDK reinitializes automatically on the next `makeCall()`
