package com.webitel.voice.sdk.internal.voice

import android.content.Context.CAMERA_SERVICE
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallEventListener
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.ConnectionEvent
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

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioManager: AudioManager by lazy {
        client.application.getSystemService(AudioManager::class.java)
    }
    private val audioRouter: CallAudioRouter by lazy { CallAudioRouter(audioManager) }

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
        listener: CallEventListener
    ): Call {
        activeCall?.takeIf { it.state !is CallState.Disconnected }?.let { existingCall ->
            logger.warn("WebitelVoiceClient",
                "makeAudioCall: Active call already exists in state: ${existingCall.state}"
            )
            existingCall.addEventListener(listener)
            return existingCall
        }

        requestCallAudioFocus(audioManager)

        val voice = WebitelCall(VoiceManager, options, authManager, audioRouter).apply {
            addEventListener(listener)
            addEventListener { event ->
                if (event is ConnectionEvent.StateChanged && event.state is CallState.Disconnected) {
                    abandonCallAudioFocus(audioManager)
                    audioRouter.reset()
                }
            }
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
        listener: CallEventListener
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


    private fun requestCallAudioFocus(audioManager: AudioManager) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }


    private fun abandonCallAudioFocus(audioManager: AudioManager) {
        audioManager.mode = AudioManager.MODE_NORMAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }
}