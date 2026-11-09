package com.webitel.voice.sdk


/**
 * How the SDK authenticates with the server to obtain SIP credentials.
 * Passed to [VoiceClient.Builder].
 */
sealed class AuthMethod {

    /**
     * Authenticates with a JWT issued by your backend.
     *
     * [tokenProvider] is called every time the SDK needs the token (before each call and
     * each push-related request), so it can return a refreshed token. It is called on a
     * background thread.
     */
    class Token(val tokenProvider: () -> String) : AuthMethod()

    /**
     * Logs in as a contact identified by [identity] (OpenID Connect style claims).
     * Requires the client token set with [VoiceClient.Builder.clientToken].
     */
    class Contact(val identity: User) : AuthMethod()
}
