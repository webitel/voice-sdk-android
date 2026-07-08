package com.webitel.voice.sdk.internal.voice

import android.content.Context.CAMERA_SERVICE
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallListener
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.User
import com.webitel.voice.sdk.VoiceClient
import com.webitel.voice.sdk.internal.auth.AuthManager
import com.webitel.voice.sdk.internal.repository.DeviceInfoRepository
import com.webitel.voice.sdk.internal.repository.storage.DeviceInfoStorageSharedPref
import org.pjsip.PjCameraInfo2


internal class WebitelVoiceClient(private val client: VoiceClient.Builder): VoiceClient {
    private val authManager: AuthManager
    private val deviceInfoRepository = DeviceInfoRepository(
        client.application,
        DeviceInfoStorageSharedPref(
            client.application
        )
    )

    override val activeCall: Call?
        get() {
            return VoiceManager.activeCall
        }

    companion object {
        val logger: WLogger = WLogger()
    }

    init {
        logger.level = client.logLevel
        VoiceManager.configure(client.callSettings)

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
         val cm = client.application.getSystemService(CAMERA_SERVICE) as CameraManager
         PjCameraInfo2.SetCameraManager(cm)
    }


    override fun setUser(user: User) {
        authManager.setUser(user)
    }


    override fun setUserJWT(token: String) {
        authManager.setJWT(token)
    }


    override fun makeCall(
        options: CallOptions,
        listener: CallListener
    ): Call {
        activeCall?.takeIf { it.state !is CallState.Disconnected }?.let { existingCall ->
            logger.warn("WebitelVoiceClient",
                "makeAudioCall: Active call already exists in state: ${existingCall.state}"
            )
            existingCall.addListener(listener)
            return existingCall
        }

        val audioManager = client.application.getSystemService(AudioManager::class.java)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val voice = WebitelCall(VoiceManager, options).apply {
            addListener(listener)
            addListener(object : CallListener {
                override fun onCallStateChanged(call: Call, state: CallState) {
                    if (state is CallState.Disconnected) {
                        audioManager.mode = AudioManager.MODE_NORMAL
                    }
                }
            })
        }
        VoiceManager.activeCall = voice

        voice.launchInScope {
            handleSipConfig(options, voice)
        }

        return voice
    }


    override fun makeCall(
        jwt: String,
        options: CallOptions,
        listener: CallListener
    ): Call {
        setUserJWT(jwt)
        return makeCall(options, listener)
    }


    private suspend fun handleSipConfig(callOptions: CallOptions, call: WebitelCall) {
        authManager.getSipConfig(callOptions.meetingId)
            .onSuccess { sip ->
                logger.debug("WebitelVoiceClient", "makeAudioCall: call to Service...")
                VoiceManager.makeCall(callOptions, call, sip)
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
        VoiceManager.shutdown {
            onComplete()
        }
    }


    private fun getUserAgent(): String {
        return deviceInfoRepository.getUserAgent()
    }
}