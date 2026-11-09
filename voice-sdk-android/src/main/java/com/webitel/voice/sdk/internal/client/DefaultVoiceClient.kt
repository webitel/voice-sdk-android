package com.webitel.voice.sdk.internal.client

import android.app.Application
import com.webitel.voice.sdk.AuthMethod
import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.CallEventListener
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.IncomingCallPush
import com.webitel.voice.sdk.LogLevel
import com.webitel.voice.sdk.SystemCallIntegration
import com.webitel.voice.sdk.VoiceClient
import com.webitel.voice.sdk.internal.call.CallImpl
import com.webitel.voice.sdk.internal.manager.CallRegistry
import com.webitel.voice.sdk.internal.manager.VoiceManager
import com.webitel.voice.sdk.internal.network.HttpClient
import com.webitel.voice.sdk.internal.services.AuthManager
import com.webitel.voice.sdk.internal.services.PushService
import com.webitel.voice.sdk.internal.services.RatingService
import com.webitel.voice.sdk.internal.support.SdkLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext


/** Orchestrates call setup: network requests here, everything SIP in [VoiceManager]. */
internal class DefaultVoiceClient(
    private val context: ClientContext,
    private val voiceManager: VoiceManager,
    private val authManager: AuthManager,
    private val pushService: PushService,
    private val ratingService: RatingService
) : VoiceClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val activeCall: Call?
        get() = voiceManager.activeCall

    override val calls: List<Call>
        get() = voiceManager.calls

    init {
        configure(context.callSettings)
        voiceManager.setExternalAudio(context.systemCallIntegration == SystemCallIntegration.HostManaged)
    }


    override fun configure(settings: CallSettings) {
        voiceManager.updateCallSettings(settings)
    }


    // region Outgoing

    override fun makeCall(options: CallOptions, listener: CallEventListener): Call {
        val (call, outcome) = voiceManager.insertCall(
            // A repeated makeCall (e.g. a double tap) returns the call already being placed.
            existing = { registry -> registry.callsById.values.firstOrNull { it.isOutgoing } },
            // Only incoming calls can wait: no outgoing call while another call exists.
            reject = { registry -> registry.callsById.isNotEmpty() }
        ) {
            CallImpl(
                delegate = voiceManager,
                ratingService = ratingService,
                remoteName = options.toName,
                remoteNumber = options.toNumber,
                meetingId = options.meetingId,
                type = options.type,
                initialOrientation = options.videoOrientation
            )
        }
        call.addEventListener(listener)

        when (outcome) {
            CallRegistry.InsertOutcome.INSERTED -> Unit
            CallRegistry.InsertOutcome.EXISTING -> {
                SdkLogger.warn(TAG, "makeCall: an outgoing call already exists in state ${call.state}")
                return call
            }
            CallRegistry.InsertOutcome.REJECTED -> {
                SdkLogger.warn(TAG, "makeCall: can't make a call while another call is in progress")
                call.updateState(CallState.Disconnected(fromCode(-1, "Another call is in progress")))
                return call
            }
        }

        SdkLogger.debug(TAG, "makeCall: ${options.type} call ${call.id}")
        call.apiTask {
            try {
                val config = authManager.getSipConfig(options.meetingId)
                coroutineContext.ensureActive()
                voiceManager.makeCallFor(call.id, config, options)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleCallFailure(call, e)
            }
        }
        return call
    }

    // endregion


    // region Incoming

    override fun handleIncomingPush(push: IncomingCallPush, listener: CallEventListener): Call {
        val (call, outcome) = voiceManager.insertCall(
            existing = { registry -> registry.callsById[push.callId] },
            reject = { registry -> registry.callsById.size >= CallRegistry.MAX_CALLS }
        ) {
            // Created already Ringing, so answer() is valid as soon as this returns.
            CallImpl(
                delegate = voiceManager,
                ratingService = ratingService,
                id = push.callId,
                isOutgoing = false,
                remoteName = push.fromName,
                remoteNumber = push.fromNumber,
                initialState = CallState.Ringing
            )
        }
        call.addEventListener(listener)

        when (outcome) {
            CallRegistry.InsertOutcome.INSERTED -> Unit
            CallRegistry.InsertOutcome.EXISTING -> {
                SdkLogger.warn(TAG, "handleIncomingPush: call ${push.callId} already exists in state ${call.state}")
                return call
            }
            CallRegistry.InsertOutcome.REJECTED -> {
                // The push isn't confirmed, so the server stops ringing on its own timeout.
                SdkLogger.warn(TAG, "handleIncomingPush: call ${push.callId} rejected, too many calls in progress")
                call.updateState(CallState.Disconnected(fromCode(486, "Too many calls in progress")))
                return call
            }
        }

        SdkLogger.debug(TAG, "handleIncomingPush: call ${push.callId} from ${push.fromNumber}")
        call.notifyInitialState()
        call.startIncomingTimeout(context.incomingCallTimeoutMs)

        call.apiTask(setConnecting = false) {
            try {
                val config = authManager.getSipConfig(meetingId = null)
                coroutineContext.ensureActive()
                voiceManager.registerForIncoming(config)
                coroutineContext.ensureActive()
                SdkLogger.debug(TAG, "SIP registered, confirming the push for call ${call.id}")
                pushService.confirmPush(call.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleCallFailure(call, e)
            }
        }
        return call
    }


    override fun handleIncomingPush(data: Map<String, String>, listener: CallEventListener): Result<Call> {
        val push = IncomingCallPush.fromData(data)
            ?: return Result.failure(CallError.InvalidResponse("Not an incoming call push: call_id is missing"))
        return Result.success(handleIncomingPush(push, listener))
    }


    override fun registerPushToken(token: String, callback: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = runCatching { pushService.registerPushToken(token) }.mapError()
            callback(result)
        }
    }


    override fun unregisterPushToken(callback: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = runCatching { pushService.unregisterPushToken() }.mapError()
            callback(result)
        }
    }

    // endregion


    override fun shutdown(onComplete: () -> Unit) {
        voiceManager.shutdown(onComplete)
    }


    private fun handleCallFailure(call: CallImpl, error: Throwable) {
        SdkLogger.error(TAG, "Call setup failed: $error")
        val reason: CallEndReason = when (val e = CallError.from(error)) {
            is CallError.Unauthorized -> fromCode(401, e.message)
            is CallError.RegistrationFailed -> fromCode(e.code, e.message)
            is CallError.Unknown -> fromCode(e.code, e.message)
            else -> fromCode(-1, e.message)
        }
        call.disconnectWithReason(reason)
    }


    private fun <T> Result<T>.mapError(): Result<T> =
        exceptionOrNull()?.let { Result.failure(CallError.from(it)) } ?: this


    companion object {
        private const val TAG = "VoiceClient"

        fun create(
            application: Application,
            address: String,
            authMethod: AuthMethod,
            clientToken: String,
            logLevel: LogLevel,
            deviceId: String,
            callSettings: CallSettings,
            incomingCallTimeoutMs: Long,
            systemCallIntegration: SystemCallIntegration
        ): VoiceClient {
            SdkLogger.level = logLevel
            val deviceInfo = DeviceInfoRepository(application, DeviceInfoStorageSharedPref(application))
            val userAgent = deviceInfo.getUserAgent()
            val context = ClientContext(
                baseUrl = address,
                clientToken = clientToken,
                authMethod = authMethod,
                deviceId = deviceId.ifEmpty { deviceInfo.getDeviceId() },
                userAgent = userAgent,
                callSettings = callSettings,
                incomingCallTimeoutMs = incomingCallTimeoutMs,
                systemCallIntegration = systemCallIntegration
            )
            val http = HttpClient(context)
            val authManager = AuthManager(context, http)
            return DefaultVoiceClient(
                context = context,
                voiceManager = VoiceManager.shared(application, userAgent),
                authManager = authManager,
                pushService = PushService(authManager, http),
                ratingService = RatingService(authManager, http)
            )
        }
    }
}
