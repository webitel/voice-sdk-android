# Call State

## CallState

```kotlin
sealed class CallState {
    /** No active connection or interaction. */
    object IDLE : CallState()

    /** In the process of connecting (e.g. dialing or setting up signaling). */
    object Connecting : CallState()

    /** Local device is ringing (incoming), or the remote party is being alerted (outgoing). */
    object Ringing : CallState()

    /** Active and ongoing. */
    object Ongoing : CallState()

    /** Ended or disconnected. */
    data class Disconnected(val reason: CallEndReason) : CallState()
}
```


## Comparing states

```kotlin
fun CallState.isSameAs(other: CallState): Boolean
```

Checks whether two states are logically the same, ignoring different
`Disconnected` reasons.


## CallEndReason

Always returned when a call transitions to `Disconnected`.

```kotlin
data class CallEndReason(
    val code: Int,
    val message: String,
    val category: CallEndCategory
)
```

- `code` — the raw SIP status code (e.g. 200, 486, 503)
- `message` — human-readable description, suitable for display or logging
- `category` — high-level classification, useful for simple checks like `if (reason.category == Error)`


## CallEndCategory

```kotlin
enum class CallEndCategory {
    Normal,
    Busy,
    Unavailable,
    Error,
    Canceled,
    Unknown
}
```

- `Normal` — the call ended normally (e.g. SIP 200 OK, SIP 603 Decline)
- `Busy` — the remote party was busy (486 Busy Here, 600 Busy Everywhere)
- `Unavailable` — the user was temporarily unavailable (480 Temporarily Unavailable)
- `Error` — a system or protocol error (500+ SIP errors, 403, 415, or unexpected SIP behavior)
- `Canceled` — the call was canceled before being answered (487, 481, 408, or the caller hung up first)
- `Unknown` — fallback when no specific reason or SIP code was available
