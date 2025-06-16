package com.webitel.voice.sdk


/**
 * Represents a structured reason why a call ended.
 *
 * Contains the raw SIP status code, a human-readable description,
 * and a high-level category that simplifies client-side logic.
 *
 * This object is always returned when a call transitions to the Disconnected state.
 *
 * @property code The SIP status code (e.g., 200, 486, 503) that indicates the reason.
 * @property message A short human-readable explanation of the reason.
 *                   This can be either a default message based on the code,
 *                   or a custom message from the server or SDK.
 * @property category A general classification of the call termination reason.
 *                    Helps the client group similar reasons (e.g., all errors or user rejections).
 */
data class CallEndReason(
    val code: Int,

    /**
     * A textual description of the reason, suitable for displaying to the user or logging.
     * Example: "Call declined", "Temporarily unavailable", "Busy here"
     */
    val message: String,

    /**
     * High-level classification of the reason.
     * Useful for simple checks like: if (reason.category == Error) { ... }
     */
    val category: CallEndCategory
)


enum class CallEndReasonCode(
    val sipCode: Int,
    val message: String,
    val category: CallEndCategory
) {
    OK(200, "Call completed successfully", CallEndCategory.Normal),

    // Busy / Unavailable
    BUSY_HERE(486, "User is busy", CallEndCategory.Busy),
    BUSY_EVERYWHERE(600, "Busy everywhere", CallEndCategory.Busy),
    TEMPORARILY_UNAVAILABLE(480, "User is temporarily unavailable", CallEndCategory.Unavailable),

    // Rejected
    DECLINED(603, "Call was declined", CallEndCategory.Normal),
    REJECTED(608, "Call was rejected", CallEndCategory.Normal),
    UNWANTED(607, "Call was marked as unwanted", CallEndCategory.Normal),

    // Authorization / Forbidden
    UNAUTHORIZED(401, "Unauthorized", CallEndCategory.Error),
    FORBIDDEN(403, "Forbidden", CallEndCategory.Error),
    PROXY_AUTH_REQUIRED(407, "Proxy authentication required", CallEndCategory.Error),

    // Not Found / Not Allowed
    NOT_FOUND(404, "User not found", CallEndCategory.Canceled),
    METHOD_NOT_ALLOWED(405, "Method not allowed", CallEndCategory.Error),
    NOT_ACCEPTABLE_HERE(488, "Not acceptable here", CallEndCategory.Error),

    // Timeout / Canceled
    REQUEST_TIMEOUT(408, "Request timeout", CallEndCategory.Canceled),
    REQUEST_TERMINATED(487, "Request was terminated", CallEndCategory.Canceled),
    CALL_DOES_NOT_EXIST(481, "Call transaction does not exist", CallEndCategory.Canceled),
    REQUEST_PENDING(491, "Request pending", CallEndCategory.Error),

    // Internal Server / Transport
    SERVICE_UNAVAILABLE(503, "Service unavailable", CallEndCategory.Error),
    INTERNAL_SERVER_ERROR(500, "Internal server error", CallEndCategory.Error),
    BAD_REQUEST(400, "Bad request", CallEndCategory.Error),
    NOT_IMPLEMENTED(501, "Not implemented", CallEndCategory.Error),
    SERVER_TIMEOUT(504, "Server timeout", CallEndCategory.Error),
    BAD_GATEWAY(502, "Bad gateway", CallEndCategory.Error),

    // Default for manually canceled
    REQUEST_CANCELLED(0, "Call cancelled before response", CallEndCategory.Canceled);


    companion object {
        fun fromCode(code: Int, messageOverride: String? = null): CallEndReason {
            val match = entries.find { it.sipCode == code }
            return if (match != null) {
                CallEndReason(match.sipCode, match.message, match.category)
            } else {
                CallEndReason(
                    code,
                    "Unmapped SIP status: ${messageOverride ?: code}",
                    CallEndCategory.Unknown
                )
            }
        }
    }
}
