package com.webitel.voice.sdk


/**
 * How calls are integrated with the Android system call infrastructure (Telecom).
 * Set with [VoiceClient.Builder.systemCallIntegration]; fixed for the client's lifetime.
 */
sealed class SystemCallIntegration {

    /**
     * No Telecom integration. The SDK manages call audio itself: it requests audio focus and
     * switches the audio mode to `MODE_IN_COMMUNICATION` while a call needs audio, and
     * restores them when the last call ends. A cellular call puts the call on hold.
     *
     * The app is responsible for the incoming call UI (notification, ringtone) and for
     * keeping the process alive in the background (e.g. a foreground service).
     */
    object None : SystemCallIntegration() {
        override fun toString() = "None"
    }

    /**
     * The app integrates calls with Telecom itself (Jetpack `core-telecom` or a self-managed
     * `ConnectionService`): it reports calls to the system, maps system actions to [Call]
     * methods and mirrors call events back to Telecom.
     *
     * Telecom then owns audio focus, the audio mode and audio routing, so the SDK touches
     * none of them: route the speaker through Telecom's call endpoints —
     * [Call.setSpeakerphoneOn] fails in this mode.
     */
    object HostManaged : SystemCallIntegration() {
        override fun toString() = "HostManaged"
    }
}
