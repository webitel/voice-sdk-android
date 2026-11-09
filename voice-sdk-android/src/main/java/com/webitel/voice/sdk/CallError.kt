package com.webitel.voice.sdk

import kotlinx.coroutines.CancellationException


/**
 * Errors returned by the SDK — as `Result.failure` from [Call] and [VoiceClient] methods,
 * or mapped into the [CallEndReason] of a call that failed to set up.
 */
sealed class CallError(override val message: String) : Exception(message) {

    /** A server or SIP stack response was malformed, missing fields, or otherwise unusable. */
    class InvalidResponse(message: String) : CallError(message)

    /**
     * The operation can't be performed in the call's current state — e.g. muting a call
     * that is not active yet, or answering an outgoing call.
     */
    class InvalidState(message: String) : CallError(message)

    /** Authentication failed: an invalid or expired token, or missing credentials. */
    class Unauthorized(message: String) : CallError(message)

    /** The server address is invalid. */
    class InvalidUrl(message: String) : CallError(message)

    /** A SIP stack operation failed. */
    class SipError(message: String) : CallError(message)

    /**
     * SIP registration, needed to receive an incoming call, was rejected or could not
     * be completed. [code] is the final SIP response code (e.g. 401, 403, 408, 503).
     */
    class RegistrationFailed(val code: Int, message: String) : CallError(message)

    /** Any other error, e.g. a network failure or an unexpected HTTP status ([code]). */
    class Unknown(message: String, val code: Int) : CallError(message)


    override fun toString(): String = "${javaClass.simpleName}(message=$message)"


    internal companion object {
        const val CODE_CANCELLED = -2
        const val CODE_UNKNOWN = -1

        /** Passes a [CallError] through unchanged; wraps anything else. */
        fun from(error: Throwable): CallError = when (error) {
            is CallError -> error
            is CancellationException -> Unknown("Operation was cancelled", CODE_CANCELLED)
            else -> Unknown(error.message ?: error.toString(), CODE_UNKNOWN)
        }
    }
}
