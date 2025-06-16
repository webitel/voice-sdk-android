package com.webitel.voice.sdk


/**
 * Represents the state of a call.
 */
sealed class CallState {

    /**
     * The call is idle — no active connection or interaction.
     */
    object IDLE : CallState() {
        override fun toString() = "IDLE"
    }

    /**
     * The call is in the process of connecting (e.g., dialing or setting up signaling).
     */
    object Connecting : CallState() {
        override fun toString() = "Connecting"
    }

    /**
     * This may indicate that the device is ringing locally (incoming call)
     * or that the remote party is being alerted (outgoing call).
     */
    object Ringing : CallState() {
        override fun toString() = "Ringing"
    }

    /**
     * The call is active and ongoing.
     */
    object Ongoing : CallState() {
        override fun toString() = "Ongoing"
    }

    /**
     * The call has ended or been disconnected.
     *
     * @property reason the reason why the call ended
     */
    data class Disconnected(val reason: CallEndReason) : CallState() {
        override fun toString(): String = buildString {
            append("Disconnected(reason={ ")
            append("code: ${reason.code}, ")
            append("message: \"${reason.message}\", ")
            append("category: ${reason.category}")
            append(" })")
        }
    }
}



/**
 * Checks if the current state is considered logically the same as [other].
 * Ignores different Disconnected reasons.
 */
fun CallState.isSameAs(other: CallState): Boolean {
    return when {
        this is CallState.Disconnected && other is CallState.Disconnected -> true
        this::class == other::class -> true
        else -> false
    }
}