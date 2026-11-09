package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import kotlinx.coroutines.CancellationException
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.SipHeader
import org.pjsip.pjsua2.SipHeaderVector
import org.pjsip.pjsua2.pjsip_status_code


/**
 * Owns the pjsua2 endpoint, the SIP account and the native call objects, addressed by
 * SIP call id. The endpoint is started on demand and destroyed by [destroy] once no call
 * needs it.
 *
 * Must be used on the SIP thread only.
 */
internal class SipManager(
    private val listener: SipEventListener,
    private val userAgent: String
) {
    private var endpoint: PJEndpoint? = null
    private var account: PJAccount? = null
    private var logWriter: SipLogWriter? = null
    private var settings = CallSettings()

    /** Keeps the native call wrappers reachable while PJSIP owns the call. */
    private val calls = HashMap<Int, PJCall>()

    /** Completions waiting for the result of an in-flight REGISTER. */
    private val registrationWaiters = mutableListOf<(Result<Unit>) -> Unit>()

    val isRunning: Boolean
        get() = endpoint != null


    fun updateCallSettings(settings: CallSettings) {
        this.settings = settings
    }


    /** Places an outgoing call and returns its SIP id. @throws CallError.SipError */
    fun makeCall(config: SipConfig, options: CallOptions): Int {
        start(config, options.videoOrientation)
        val ep = requireNotNull(endpoint)
        val acc = requireNotNull(account)

        if (options.hasVideo) {
            val configurator = configurator()
            configurator.findH264CodecId(ep)?.let {
                configurator.applyVideoQuality(ep, it, settings.videoQuality, options.videoOrientation)
            }
        }

        val pjCall = PJCall(acc, listener)
        val callOpParam = CallOpParam()
        val headers = SipHeaderVector()
        val ownedHeaders = mutableListOf<SipHeader>()
        try {
            fun addHeader(name: String, value: String) {
                val header = SipHeader().apply { hName = name; hValue = value }
                ownedHeaders += header
                headers.add(header)
            }
            options.meetingId?.takeIf { it.isNotEmpty() }?.let { addHeader("X-Webitel-Meeting", it) }
            addHeader("X-FS-Support", "update_display,send_info")
            callOpParam.txOption.headers = headers

            callOpParam.setMediaCount(options.type.code)

            pjCall.makeCall(acc.config.getSipUri(options.toNumber, options.toName), callOpParam)
        } catch (e: Exception) {
            try { pjCall.delete() } catch (_: Exception) {}
            throw sipError("makeCall", e)
        } finally {
            ownedHeaders.forEach { try { it.delete() } catch (_: Exception) {} }
            try { headers.delete() } catch (_: Exception) {}
            try { callOpParam.delete() } catch (_: Exception) {}
        }

        val sipId = pjCall.id
        calls[sipId] = pjCall
        SdkLogger.debug(TAG, "makeCall: sipId - $sipId")
        return sipId
    }


    /**
     * Starts the endpoint if needed and registers the account so the server can deliver an
     * incoming call. [completion] is called on the SIP thread once the registrar answers, or
     * with a failure when the endpoint is destroyed first.
     */
    fun register(config: SipConfig, completion: (Result<Unit>) -> Unit) {
        // Queued before anything can throw, so the catch below always completes it.
        registrationWaiters += completion
        try {
            start(config, VideoOrientation.PORTRAIT)
            requireNotNull(account).setRegistration(true)
            SdkLogger.debug(TAG, "register: REGISTER sent")
        } catch (e: Exception) {
            completeRegistration(Result.failure(sipError("register", e)))
        }
    }


    fun onRegistrationState(code: Int, reason: String) {
        if (code / 100 == 2) {
            SdkLogger.debug(TAG, "onRegistrationState: registered ($code)")
            completeRegistration(Result.success(Unit))
        } else {
            val message = "SIP registration failed: $code $reason".trim()
            SdkLogger.error(TAG, "onRegistrationState: $message")
            completeRegistration(Result.failure(CallError.RegistrationFailed(code, message)))
        }
    }


    /** Takes ownership of an incoming call created in `PJAccount.onIncomingCall`. */
    fun adoptIncomingCall(call: PJCall) {
        calls[call.id] = call
    }


    /** Drops the reference to a disconnected call without deleting it — see [PJCall.retire]. */
    fun forgetCall(sipId: Int) {
        calls.remove(sipId)?.retire()
    }


    fun call(sipId: Int): PJCall? = if (isRunning) calls[sipId] else null


    fun sendRinging(sipId: Int) {
        try {
            requireCall(sipId).answer(pjsip_status_code.PJSIP_SC_RINGING)
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "sendRinging: ${e.message}")
        }
    }


    fun answerCall(sipId: Int) = perform("answerCall") { requireCall(sipId).answer() }


    fun hangup(sipId: Int, code: Int) = perform("hangup") { requireCall(sipId).hangup(code) }


    /** @throws IllegalStateException if the call has no active audio media yet. */
    fun setMute(sipId: Int, mute: Boolean) {
        ensureThreadRegistered()
        requireCall(sipId).setMute(mute, requireNotNull(endpoint).audDevManager())
    }


    fun setHold(sipId: Int, hold: Boolean, videoCount: Long) = perform("setHold") {
        requireCall(sipId).setHold(hold, videoCount)
    }


    fun sendDtmf(sipId: Int, digits: String) = perform("sendDtmf") { requireCall(sipId).sendDtmf(digits) }


    fun reinviteWithVideoCount(sipId: Int, videoCount: Long) = perform("reinviteWithVideoCount") {
        requireCall(sipId).reinviteWithVideoCount(videoCount)
    }


    /** @throws IllegalStateException if there is no active local video stream. */
    fun setLocalVideoTransmitting(sipId: Int, transmit: Boolean) {
        ensureThreadRegistered()
        requireCall(sipId).setLocalVideoTransmitting(transmit)
    }


    fun sendInfo(sipId: Int, contentType: String, body: String) = perform("sendInfo") {
        requireCall(sipId).sendInfo(contentType, body)
    }


    /**
     * Connects the call's active audio media to the sound device and returns all of its
     * media lines, or `null` if the call is gone.
     */
    fun handleMediaState(sipId: Int): List<SipMediaInfo>? {
        val ep = endpoint ?: return null
        val pjCall = calls[sipId] ?: return null
        ensureThreadRegistered()
        val medias = try {
            pjCall.mediaInfos()
        } catch (e: Exception) {
            SdkLogger.error(TAG, "handleMediaState: ${e.message}")
            return null
        }
        val audDevManager = try { ep.audDevManager() } catch (e: Exception) { null }
        for (media in medias) {
            val connectable = media.isAudio && (media.isActive || media.status == REMOTE_HOLD)
            if (!connectable || audDevManager == null) continue
            try {
                pjCall.connectAudio(media.index, audDevManager)
                SdkLogger.debug(TAG, "audio media connected [${media.index}]")
            } catch (e: Exception) {
                SdkLogger.error(TAG, "audio media error: ${e.message}")
            }
        }
        return medias
    }


    /** The call's media lines without side effects, or `null` if the call is gone. */
    fun mediaInfos(sipId: Int): List<SipMediaInfo>? {
        val pjCall = call(sipId) ?: return null
        ensureThreadRegistered()
        return try { pjCall.mediaInfos() } catch (e: Exception) { null }
    }


    /** RTCP counters of the call's audio stream, or `null` if unavailable. */
    fun audioStreamStat(sipId: Int): SipStreamStat? {
        val pjCall = call(sipId) ?: return null
        ensureThreadRegistered()
        return try { pjCall.audioStreamStat() } catch (e: Exception) { null }
    }


    /** Updates the video encoding orientation mid-call without a re-INVITE. */
    fun updateVideoOrientation(orientation: VideoOrientation) {
        val ep = endpoint ?: return
        ensureThreadRegistered()
        val configurator = configurator()
        val codecId = configurator.findH264CodecId(ep) ?: return
        configurator.applyVideoQuality(ep, codecId, settings.videoQuality, orientation)
        configurator.applyCameraOrientations(ep, orientation)
        // setCaptureOrient(-1, …, keep=true) is intentionally NOT called here — see
        // SipEndpointConfigurator.applyCameraOrientations.
    }


    /** Hangs up every call and destroys the endpoint. A no-op if it isn't running. */
    fun destroy() {
        completeRegistration(Result.failure(CancellationException("SIP stack destroyed")))
        val ep = endpoint ?: return
        SdkLogger.debug(TAG, "destroy: shutting down the SIP stack")
        // Each step is guarded separately: ep.delete() must run even if an earlier step fails.
        // pj::Endpoint is a native singleton; a leftover one makes the next PJEndpoint()
        // throw a C++ exception that SWIG doesn't convert -> std::terminate (abort).
        try {
            ensureThreadRegistered()
            destroyStep("hangupAllCalls") { ep.hangupAllCalls() }
            account?.let { acc ->
                destroyStep("account.shutdown") { acc.shutdown() }
                destroyStep("account.delete") { acc.delete() }
            }
            destroyStep("libDestroy") { ep.libDestroy() }
            destroyStep("endpoint.delete") { ep.delete() }
        } finally {
            calls.values.forEach { it.retire() }
            calls.clear()
            account = null
            endpoint = null
            logWriter = null
        }
    }


    private inline fun destroyStep(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            SdkLogger.error(TAG, "destroy: $name failed: ${e.message}")
        }
    }


    private fun start(config: SipConfig, orientation: VideoOrientation) {
        SdkThreads.assertOnSip()
        loadNativeLibraries()
        val ep = endpoint ?: createEndpoint(config, orientation)
        ensureThreadRegistered()

        val current = account
        if (current != null && current.config != config) {
            if (calls.isEmpty()) {
                SdkLogger.debug(TAG, "start: SIP credentials changed, recreating the account")
                try {
                    current.shutdown()
                    current.delete()
                } catch (e: Exception) {
                    SdkLogger.error(TAG, "start: failed to remove the old account: ${e.message}")
                }
                account = null
            } else {
                SdkLogger.warn(TAG, "start: SIP credentials changed while a call is in progress, keeping the account")
            }
        }
        if (account == null) {
            account = createAccount(ep, config)
        }
    }


    private fun createEndpoint(config: SipConfig, orientation: VideoOrientation): PJEndpoint {
        val configurator = configurator()
        val ep = PJEndpoint()
        try {
            ep.libCreate()
            val writer = SipLogWriter()
            logWriter = writer
            val epConfig = configurator.makeEndpointConfig(writer)
            try {
                ep.libInit(epConfig)
            } finally {
                try { epConfig.delete() } catch (_: Exception) {}
            }
            if (!ep.libIsThreadRegistered()) ep.libRegisterThread(Thread.currentThread().name)
            configurator.createTransports(ep, config.proxyUri())
            ep.libStart()
        } catch (e: Exception) {
            try { ep.libDestroy() } catch (_: Exception) {}
            try { ep.delete() } catch (_: Exception) {}
            logWriter = null
            throw sipError("createEndpoint", e)
        }
        endpoint = ep

        // The account opens the front camera by default.
        configurator.applyCameraOrientations(ep, orientation)
        configurator.applyVideoQuality(ep, configurator.findH264CodecId(ep) ?: "H264/", settings.videoQuality, orientation)
        try {
            ep.audDevManager().setOutputVolume(100, true)
            ep.audDevManager().setInputVolume(100, true)
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "createEndpoint: failed to set volume: ${e.message}")
        }
        return ep
    }


    private fun createAccount(ep: PJEndpoint, config: SipConfig): PJAccount {
        val accountConfig = configurator().makeAccountConfig(config)
        val acc = PJAccount(config, listener)
        try {
            acc.create(accountConfig, true)
        } catch (e: Exception) {
            try { acc.delete() } catch (_: Exception) {}
            throw sipError("createAccount", e)
        } finally {
            try { accountConfig.delete() } catch (_: Exception) {}
        }
        SdkLogger.debug(TAG, "createAccount: $config on ${if (ep.libIsThreadRegistered()) "registered" else "unregistered"} thread")
        return acc
    }


    private fun completeRegistration(result: Result<Unit>) {
        if (registrationWaiters.isEmpty()) return
        val waiters = registrationWaiters.toList()
        registrationWaiters.clear()
        waiters.forEach { it(result) }
    }


    private fun requireCall(sipId: Int): PJCall {
        if (!isRunning) throw CallError.InvalidState("SIP stack is not running")
        return calls[sipId] ?: throw CallError.InvalidState("SIP call $sipId not found")
    }


    private inline fun perform(name: String, block: () -> Unit) {
        ensureThreadRegistered()
        try {
            block()
        } catch (e: CallError) {
            throw e
        } catch (e: Exception) {
            throw sipError(name, e)
        }
    }


    /** The SIP thread is registered once per endpoint; a recreated endpoint needs it again. */
    private fun ensureThreadRegistered() {
        val ep = endpoint ?: return
        try {
            if (!ep.libIsThreadRegistered()) ep.libRegisterThread(Thread.currentThread().name)
        } catch (e: Exception) {
            SdkLogger.error(TAG, "ensureThreadRegistered: ${e.message}")
        }
    }


    private fun configurator() = SipEndpointConfigurator(settings, userAgent)


    private fun sipError(operation: String, e: Exception): CallError {
        if (e is CallError) return e
        SdkLogger.error(TAG, "$operation: ${e.message}")
        return CallError.SipError("$operation: ${e.message}")
    }


    private fun loadNativeLibraries() {
        if (isLibraryLoaded) return
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("pjsua2")
            isLibraryLoaded = true
        } catch (error: UnsatisfiedLinkError) {
            SdkLogger.error(TAG, "loadNativeLibraries: $error")
            throw CallError.SipError("Failed to load native libraries: ${error.message}")
        }
    }


    private companion object {
        const val TAG = "SipManager"
        const val REMOTE_HOLD = org.pjsip.pjsua2.pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD

        @Volatile
        var isLibraryLoaded = false
    }
}
