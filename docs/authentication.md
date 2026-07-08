# Authentication

Authentication is configured either upfront via the `VoiceClient.Builder`, or per call.


## Supported methods

- User object (`setUser`)
- JWT token (`setUserJWT`, or inline per call)


## Option 1 — User object

```kotlin
val user = User.Builder(
    iss = "https://demo.webitel.com/portal",
    sub = "user-123",
    name = "John Smith"
)
    .email("john@example.com")
    .phoneNumber("+15551234567")
    .locale("en-US")
    .build()

voiceClient.setUser(user)
```

The same `User` object can also be provided upfront, while building the
client via `VoiceClient.Builder.user()`, see [Initialization](initialization.md):

```kotlin
VoiceClient.Builder(application, address, token)
    .user(user)
    .build()
```

This is equivalent to calling `setUser(user)` right after the client is
built — the provided information is then used for authentication.


## Option 2 — JWT token

Set it globally:

```kotlin
voiceClient.setUserJWT("your-jwt-token")
```

or provide it inline when starting a call, see [Calls](calls.md):

```kotlin
voiceClient.makeCall(jwt = "your-jwt-token", listener = callListener)
```


## User model

```kotlin
class User private constructor(builder: Builder) {

    /** Issuer Identifier for the Issuer of the response. */
    val iss: String

    /** Unique Subject Identifier for the End-User within the Issuer. */
    val sub: String

    /** Full display name of the End-User. */
    val name: String

    /** Email address of the End-User. */
    val email: String

    /** Whether the End-User's email has been verified. */
    val emailVerified: Boolean

    /** Phone number of the End-User. */
    val phoneNumber: String

    /** Whether the End-User's phone number has been verified. */
    val phoneNumberVerified: Boolean

    /** Locale of the End-User, typically a BCP47 language tag (e.g. "en-US"). */
    var locale: String
}
```

`iss`, `sub`, and `name` are required and passed to `User.Builder`. The rest
are optional and set via builder methods: `email()`, `emailVerified()`,
`phoneNumber()`, `phoneNumberVerified()`, `locale()`.
