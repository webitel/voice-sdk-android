package com.webitel.voice.sdk

import android.app.Application
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient


interface VoiceClient {

    /**
     * Returns the currently active call, if any.
     * Can be null if no call is active.
     */
    val activeCall: Call?


    /**
     * Sets the current authenticated user.
     * This method is used to associate a user identity with the client.
     *
     * @param user The user information used for authentication and call metadata.
     */
    fun setUser(user: User)


    /**
     * Sets a JWT token for user authentication.
     * This token may be used for authorizing call actions.
     *
     * @param token A valid JWT string representing the user.
     */
    fun setUserJWT(token: String)


    /**
     * Initiates an audio-only call using the currently set user or JWT token.
     *
     * @param listener A listener to observe call state and events.
     * @return A new Call instance representing the initiated audio call.
     */
    fun makeCall(options: CallOptions = CallOptions(), listener: CallListener): Call


    /**
     * Initiates an audio-only call by providing a JWT token inline.
     * This method allows performing a call without globally setting the token via [setUserJWT].
     *
     * @param jwt A JWT token used for authentication during the call setup.
     * @param listener A listener to observe call state and events.
     * @return A new Call instance representing the initiated audio call.
     */
    fun makeCall(jwt: String, options: CallOptions = CallOptions(), listener: CallListener): Call


    /**
     * Terminates all active calls and fully shuts down the internal call session.
     *
     * Call this method if you want to completely reset the internal session state, for example,
     * to reinitialize with different credentials or configuration.
     *
     * If the credentials remain the same, the SDK will automatically reinitialize itself
     * when needed (e.g., on the next `makeCall()` invocation).
     *
     * @param onComplete A callback invoked once shutdown is fully completed.
     */
    fun shutdown(onComplete: () -> Unit = {})


    /**
     * Builder for configuring and creating an instance of VoiceClient.
     *
     * @property application Application context
     * @property address Server address of the voice service
     * @property token Access token for authentication
     */
    data class Builder(
        val application: Application,
        var address: String,
        var token: String
    ) {
        internal var user: User? = null
        internal var logLevel: LogLevel = LogLevel.ERROR
        internal var deviceId: String = ""
        internal var callSettings: CallSettings = CallSettings()

        /**
         * Assigns the user information that will be used for authentication purposes.
         * @param user The user to associate with the client.
         * @return The Builder instance for method chaining.
         */
        fun user(user: User) = apply { this.user = user }


        /**
         * The logLevel method sets the log level for error and message reporting.
         * Specifies the log level to set. The following are the valid options described in ascending order:
         *  - debug — Specifies a log level in which all messages are logged.
         *  - info — Specifies a log level in which informational, warning, and error messages are logged.
         *  - warn — Specifies a log level in which warning and error messages are logged.
         *  - error — Specifies a log level in which only error messages are logged.
         *  - off — disables all logs.
         *  Default is LogLevel.error
         */
        fun logLevel(value: LogLevel) = apply { this.logLevel = value }


        /**
         * Sets the device ID for the client.
         * @param value The device ID.
         * @return The Builder instance for method chaining.
         */
        fun deviceId(value: String) = apply { this.deviceId = value }


        /**
         * Overrides the default network/transport configuration.
         *
         * If not called, the SDK uses [CallSettings] defaults (TCP transport,
         * ICE disabled, SRTP disabled).
         *
         * @param settings A configured [CallSettings] instance.
         * @return The Builder instance for method chaining.
         */
        fun callSettings(settings: CallSettings) = apply { this.callSettings = settings }


        /**
         * Builds and returns a configured VoiceClient instance.
         */
        fun build(): VoiceClient {
            return WebitelVoiceClient(this)
        }
    }
}