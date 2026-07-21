package com.webitel.voice.sdk.internal.voice

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build


/**
 * Routes call audio to the built-in speaker or back to the default device (earpiece / an
 * already-connected wired or Bluetooth device), via the official AudioManager routing API.
 */
internal class CallAudioRouter(private val audioManager: AudioManager) {

    var isSpeakerphoneOn: Boolean = false
        private set


    fun setSpeakerphoneOn(enabled: Boolean): Result<Unit> {
        if (enabled && hasPreferredExternalAudioDevice()) {
            return Result.failure(IllegalStateException(
                "setSpeakerphoneOn(true) ignored: a wired or Bluetooth audio device is connected"
            ))
        }

        return try {
            if (enabled) routeToSpeaker() else routeToDefault()
            isSpeakerphoneOn = enabled
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun reset() {
        if (isSpeakerphoneOn) {
            routeToDefault()
            isSpeakerphoneOn = false
        }
    }


    private fun hasPreferredExternalAudioDevice(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn
        }
    }


    private fun routeToSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            speaker?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }


    private fun routeToDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }
}
