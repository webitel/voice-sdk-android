package com.webitel.voice.sdk


/**
 * Represents a phone call and its current state.
 */
interface Call {

    /** Current state of the call (e.g., ringing, active, ended). */
    val state: CallState

    /** Indicates whether the local microphone is muted. */
    val isMuted: Boolean

    /** Indicates whether the call is currently on hold. */
    val isOnHold: Boolean

    /** Timestamp in milliseconds when the call was answered. */
    val answeredAt: Long

    /** True if the call was initiated by the local user (outgoing). */
    val isOutgoing: Boolean


    /**
     * Attempts to mute or unmute the ongoing call.
     *
     * @param mute true to mute the call, false to unmute.
     * @return Result.success(Unit) if the operation was successful, or Result.failure with the error.
     */
    fun mute(mute: Boolean): Result<Unit>


    /**
     * Attempts to place the ongoing call on hold or resume it.
     *
     * @param hold true to hold the call, false to resume.
     * @return Result.success(Unit) if the operation was successful, or Result.failure with the error.
     */
    fun hold(hold: Boolean): Result<Unit>


    /**
     * Sends DTMF (Dual-Tone Multi-Frequency) digits during an active call.
     *
     * @param digits a string of digits (e.g., "123#*").
     * @return Result.success(Unit) if the digits were sent successfully, or Result.failure with the error.
     */
    fun sendDTMF(digits: String): Result<Unit>


    /**
     * Attempts to disconnect (hang up) the current call.
     *
     * @return Result.success(Unit) if the call was disconnected successfully, or Result.failure with the error.
     */
    fun disconnect(): Result<Unit>


    /**
     * Adds a listener to receive updates about the call's state.
     *
     * @param listener the listener to add
     */
    fun addListener(listener: CallListener)


    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    fun removeListener(listener: CallListener)


    /**
     * Removes all registered listeners.
     */
    fun removeAllListeners()
}