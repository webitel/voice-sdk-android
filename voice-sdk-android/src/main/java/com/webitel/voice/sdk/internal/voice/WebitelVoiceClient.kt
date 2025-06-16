package com.webitel.voice.sdk.internal.voice

import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallListener
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.User
import com.webitel.voice.sdk.VoiceClient
import com.webitel.voice.sdk.internal.auth.AuthManager
import com.webitel.voice.sdk.internal.repository.DeviceInfoRepository
import com.webitel.voice.sdk.internal.repository.storage.DeviceInfoStorageSharedPref


internal class WebitelVoiceClient(private val client: VoiceClient.Builder): VoiceClient {
    private val authManager: AuthManager
    private val voiceManager = VoiceManager()
    private val deviceInfoRepository = DeviceInfoRepository(
        DeviceInfoStorageSharedPref(
            client.application
        )
    )

    override val activeCall: Call?
        get() {
            return voiceManager.activeCall
        }

    companion object {
        val logger: WLogger = WLogger()
    }

    init {
        logger.level = client.logLevel

        val deviceId = client.deviceId.ifEmpty {
            deviceInfoRepository.getDeviceId()
        }

        val userAgent = getUserAgent()
        authManager = AuthManager(
            baseUrl = client.address,
            deviceId = deviceId,
            userAgent = userAgent,
            clientToken = client.token
        )

        client.user?.let {
            authManager.setUser(it)
        }

        // val cm = application.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
        // PjCameraInfo2.SetCameraManager(cm)
    }


    override fun setUser(user: User) {
        authManager.setUser(user)
    }


    override fun setUserJWT(token: String) {
        authManager.setJWT(token)
    }


    override fun makeAudioCall(listener: CallListener): Call {
        activeCall?.takeIf { it.state !is CallState.Disconnected }?.let { existingCall ->
            logger.warn("WebitelVoiceClient",
                "makeAudioCall: Active call already exists in state: ${existingCall.state}"
            )
            existingCall.addListener(listener)
            return existingCall
        }

        val voice = WebitelCall(voiceManager).apply {
            addListener(listener)
        }
        voiceManager.activeCall = voice

        voice.launchInScope {
            handleSipConfig(voice)
        }

        return voice
    }


    override fun makeAudioCall(jwt: String, listener: CallListener): Call {
        setUserJWT(jwt)
        return makeAudioCall(listener)
    }


    private suspend fun handleSipConfig(call: WebitelCall) {
        authManager.getSipConfig()
            .onSuccess { sip ->
                logger.debug("WebitelVoiceClient", "makeAudioCall: call to Service...")
                voiceManager.makeAudioCall(call, sip)
            }
            .onFailure { error ->
                handleCallFailure(call, error)
            }
    }


    private fun handleCallFailure(call: WebitelCall, error: Throwable) {
        val reason = if (error is CallException) {
            fromCode(error.code, error.message)
        } else {
            fromCode(-1, error.message ?: "Unknown error")
        }
        call.disconnectWithReason(reason)
    }


    override fun shutdown(onComplete: () -> Unit) {
        voiceManager.shutdown {
            onComplete()
        }
    }


    private fun getUserAgent(): String {
        val appName: String = client.name.ifEmpty {
            client.application.packageName
        }
        val version: String = client.ver

        return deviceInfoRepository.getUserAgent(
            appName = appName,
            appVersion = version
        )
    }
}