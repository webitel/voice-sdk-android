package com.webitel.voice.sdk.internal.manager

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import java.util.concurrent.ScheduledFuture


/**
 * Decides when the app's audio is switched into call mode. SIP thread only.
 *
 * - [Mode.AUTOMATIC] (`SystemCallIntegration.None`): requests audio focus and sets
 *   `MODE_IN_COMMUNICATION` as soon as a call needs audio, restores both on [reset].
 *   A cellular call taking the focus away triggers [onCellularCall]; when the focus comes
 *   back the communication mode is re-applied, since telephony leaves `MODE_NORMAL` behind.
 * - [Mode.EXTERNAL] (`SystemCallIntegration.HostManaged`): the app's Telecom integration
 *   owns audio focus and the audio mode; nothing is touched.
 */
internal class AudioController(
    private val audioManager: AudioManager,
    private val onCellularCall: () -> Unit
) {

    enum class Mode { AUTOMATIC, EXTERNAL }

    private var mode = Mode.AUTOMATIC
    private var hasFocus = false
    private var focusRequest: AudioFocusRequest? = null
    private var focusLost = false
    private var cellularCheck: ScheduledFuture<*>? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        SdkThreads.postSip { onFocusChange(change) }
    }


    fun setMode(mode: Mode) {
        if (this.mode == mode) return
        reset()
        this.mode = mode
    }


    /** A call started connecting or was answered. Idempotent. */
    fun requestAudio() {
        if (mode != Mode.AUTOMATIC || hasFocus) return
        hasFocus = true
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            SdkLogger.error(TAG, "requestAudio: ${e.message}")
        }
    }


    private fun onFocusChange(change: Int) {
        SdkLogger.debug(TAG, "audio focus changed: $change, audio mode ${audioManager.mode}")
        if (mode != Mode.AUTOMATIC || !hasFocus) return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLost = true
                checkCellularCall()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                stopCellularCheck()
                if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
                    try {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    } catch (e: Exception) {
                        SdkLogger.error(TAG, "onFocusChange: ${e.message}")
                    }
                }
            }
        }
    }


    /**
     * Holds the call only for a cellular call; other apps' transient focus must not. The
     * focus is lost already while the cellular call rings, so this keeps checking until it
     * is answered (`MODE_IN_CALL`) or the focus comes back.
     */
    private fun checkCellularCall() {
        cellularCheck?.cancel(false)
        cellularCheck = null
        if (!focusLost || mode != Mode.AUTOMATIC || !hasFocus) return
        when (audioManager.mode) {
            AudioManager.MODE_IN_CALL -> onCellularCall()
            AudioManager.MODE_RINGTONE -> {
                cellularCheck = SdkThreads.postSipDelayed(CELLULAR_CHECK_INTERVAL_MS) { checkCellularCall() }
            }
        }
    }


    private fun stopCellularCheck() {
        focusLost = false
        cellularCheck?.cancel(false)
        cellularCheck = null
    }


    /** No call needs audio anymore. */
    fun reset() {
        stopCellularCheck()
        if (!hasFocus) return
        hasFocus = false
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            SdkLogger.error(TAG, "reset: ${e.message}")
        }
    }


    private companion object {
        const val TAG = "AudioController"
        const val CELLULAR_CHECK_INTERVAL_MS = 1_000L
    }
}
