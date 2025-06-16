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
    fun makeAudioCall(listener: CallListener): Call


    /**
     * Initiates an audio-only call by providing a JWT token inline.
     * This method allows performing a call without globally setting the token via [setUserJWT].
     *
     * @param jwt A JWT token used for authentication during the call setup.
     * @param listener A listener to observe call state and events.
     * @return A new Call instance representing the initiated audio call.
     */
    fun makeAudioCall(jwt: String, listener: CallListener): Call


//    /**
//     * Initiates an outgoing video call using the specified surfaces for local and remote video streams.
//     *
//     * @param localSurface The [Surface] where the local camera preview will be rendered.
//     *                     This is typically attached to a [SurfaceView] or [TextureView] in the UI.
//     *
//     * @param remoteSurface The [Surface] where the remote video stream will be displayed.
//     *                      This should be connected to a visible UI element capable of rendering video.
//     *
//     * @param config Optional [VideoCallConfig] to customize call behavior such as resolution,
//     *               codec preferences, bitrate limits, etc. Defaults to standard values.
//     *
//     * This function assumes the PJSIP video subsystem is already initialized and media transport
//     * is ready. It sets up video media for both sending and receiving streams.
//     */
//    fun makeVideoCall(
//        localSurface: Surface,
//        remoteSurface: Surface,
//        config: VideoCallConfig = VideoCallConfig()
//    )

//    /**
//     * Sets client configuration such as audio/video codecs, NAT traversal, etc.
//     */
//    fun setConfig(config: CallConfig)


    /**
     * Terminates all active calls, deletes the SIP account, and fully shuts down the SIP stack.
     *
     * Call this method if you want to completely reset the internal SIP state, for example,
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
     * @property address Server address (e.g., SIP or WebSocket endpoint)
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
        internal var ver: String = "0.0.0"
        internal var name: String = ""

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
         * Sets the Android application display name.
         * @param name The display name of the application.
         * @return The Builder instance for method chaining.
         */
        fun appName(name: String) = apply { this.name = name }


        /**
         * Sets the Android application version.
         * @param version The version of the application.
         * @return The Builder instance for method chaining.
         */
        fun appVersion(version: String) = apply { this.ver = version }


        /**
         * Sets the device ID for the client.
         * @param value The device ID.
         * @return The Builder instance for method chaining.
         */
        fun deviceId(value: String) = apply { this.deviceId = value }


        /**
         * Builds and returns a configured VoiceClient instance.
         */
        fun build(): VoiceClient {
            return WebitelVoiceClient(this)
        }
    }
}