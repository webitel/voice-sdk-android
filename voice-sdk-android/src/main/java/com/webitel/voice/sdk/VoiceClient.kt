package com.webitel.voice.sdk

import android.app.Application
import com.webitel.voice.sdk.internal.client.DefaultVoiceClient


/**
 * Entry point of the SDK: places outgoing calls and receives incoming ones.
 *
 * Create one with [Builder]. All calls of the process share one SIP stack, which runs only
 * while at least one call exists.
 *
 * Threading: methods can be called from any thread and never block. [CallEvent]s are
 * delivered in order on a background SDK thread, not on the main thread.
 */
interface VoiceClient {

    /**
     * The call currently in focus, if any. With two calls (call waiting) it is the one
     * that is not on hold.
     */
    val activeCall: Call?

    /**
     * All live calls. Contains more than one call only while an incoming call arrives
     * during another call (call waiting).
     */
    val calls: List<Call>


    /**
     * Places an outgoing call.
     *
     * Only one outgoing call is allowed at a time: a repeated call while one is being
     * placed returns that call, and a call while another call exists is returned already
     * disconnected.
     *
     * @param options call parameters.
     * @param listener receives this call's events; more can be added with [Call.addEventListener].
     */
    fun makeCall(options: CallOptions = CallOptions(), listener: CallEventListener): Call


    /**
     * Handles an incoming call push and returns the incoming call, already [CallState.Ringing].
     *
     * The SDK then obtains SIP credentials, registers with the SIP server and confirms the
     * push, after which the server sends the call. [Call.answer] and [Call.disconnect]
     * (decline) can be called right away — before the call actually arrives; they are
     * applied as soon as it does. If it doesn't arrive within
     * [Builder.incomingCallTimeout], the call ends with code 408.
     *
     * A repeated push for the same call returns the existing call. If the maximum number
     * of calls is reached, the returned call is already disconnected (code 486).
     *
     * Show the incoming call UI (e.g. a `CallStyle` notification) for the returned call;
     * with [SystemCallIntegration.HostManaged] also report it to Telecom.
     */
    fun handleIncomingPush(push: IncomingCallPush, listener: CallEventListener): Call


    /**
     * Same as [handleIncomingPush], parsing the FCM data payload (`RemoteMessage.data`).
     *
     * @return `Result.failure` with [CallError.InvalidResponse] if the payload is not an
     *         incoming call push.
     */
    fun handleIncomingPush(data: Map<String, String>, listener: CallEventListener): Result<Call>


    /**
     * Subscribes this device to incoming call pushes with its FCM registration token.
     * Call it after authentication is available and whenever FCM issues a new token
     * (`FirebaseMessagingService.onNewToken`).
     *
     * @param callback invoked on a background thread with the result.
     */
    fun registerPushToken(token: String, callback: (Result<Unit>) -> Unit = {})


    /**
     * Unsubscribes this device from incoming call pushes, e.g. on logout.
     *
     * @param callback invoked on a background thread with the result.
     */
    fun unregisterPushToken(callback: (Result<Unit>) -> Unit = {})


    /** Replaces the network/transport settings. Applies to calls started afterwards. */
    fun configure(settings: CallSettings)


    /**
     * Ends all calls and destroys the SIP stack. The client stays usable: the stack starts
     * again with the next call.
     *
     * @param onComplete invoked on a background thread once the shutdown completes.
     */
    fun shutdown(onComplete: () -> Unit = {})


    /**
     * Configures and creates a [VoiceClient].
     *
     * @param application the application.
     * @param address the server address, e.g. `https://example.webitel.com`.
     * @param authMethod how the SDK obtains SIP credentials.
     */
    class Builder(
        private val application: Application,
        private val address: String,
        private val authMethod: AuthMethod
    ) {
        private var clientToken: String = ""
        private var logLevel: LogLevel = LogLevel.ERROR
        private var deviceId: String = ""
        private var callSettings: CallSettings = CallSettings()
        private var incomingCallTimeoutMs: Long = DEFAULT_INCOMING_CALL_TIMEOUT_MS
        private var systemCallIntegration: SystemCallIntegration = SystemCallIntegration.None


        /**
         * The client (application) token issued for your portal. Required for
         * [AuthMethod.Contact]; sent with every request.
         */
        fun clientToken(value: String) = apply { this.clientToken = value }


        /**
         * Log verbosity, from the most detailed: [LogLevel.DEBUG], [LogLevel.INFO],
         * [LogLevel.WARN], [LogLevel.ERROR] (default), [LogLevel.OFF].
         */
        fun logLevel(value: LogLevel) = apply { this.logLevel = value }


        /** A stable device identifier. Defaults to a random UUID persisted by the SDK. */
        fun deviceId(value: String) = apply { this.deviceId = value }


        /**
         * Overrides the network/transport configuration. Defaults to [CallSettings]
         * defaults (TCP+UDP transport, ICE and SRTP disabled).
         */
        fun callSettings(settings: CallSettings) = apply { this.callSettings = settings }


        /**
         * How long to wait, after [VoiceClient.handleIncomingPush], for the incoming call
         * to arrive before ending it with code 408. Default: 15 seconds.
         */
        fun incomingCallTimeout(millis: Long) = apply {
            require(millis > 0) { "incomingCallTimeout must be positive" }
            this.incomingCallTimeoutMs = millis
        }


        /**
         * How calls are integrated with the Android Telecom framework.
         * Default: [SystemCallIntegration.None].
         */
        fun systemCallIntegration(value: SystemCallIntegration) = apply { this.systemCallIntegration = value }


        fun build(): VoiceClient {
            return DefaultVoiceClient.create(
                application = application,
                address = address,
                authMethod = authMethod,
                clientToken = clientToken,
                logLevel = logLevel,
                deviceId = deviceId,
                callSettings = callSettings,
                incomingCallTimeoutMs = incomingCallTimeoutMs,
                systemCallIntegration = systemCallIntegration
            )
        }
    }


    companion object {
        const val DEFAULT_INCOMING_CALL_TIMEOUT_MS = 15_000L
    }
}
