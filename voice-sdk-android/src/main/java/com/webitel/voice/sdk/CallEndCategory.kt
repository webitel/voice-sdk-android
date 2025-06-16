package com.webitel.voice.sdk


/**
 * High-level classification of the reason why a call ended.
 * Used to simplify client-side handling of different termination scenarios.
 */
enum class CallEndCategory {

    /**
     * The call ended normally.
     *
     * This includes successful completion of a conversation or a call
     * that was intentionally ended by either side without errors.
     * Example: SIP 200 OK, SIP 603 Decline (user manually rejected the call).
     */
    Normal,

    /**
     * The remote party was busy and couldn't accept the call.
     *
     * Includes status codes like 486 (Busy Here) or 600 (Busy Everywhere).
     * These indicate the user is actively on another call or declined due to being busy.
     */
    Busy,

    /**
     * The user was temporarily unavailable.
     *
     * Includes 480 (Temporarily Unavailable), which means the recipient is registered
     * but currently unreachable, e.g., offline or with poor network.
     */
    Unavailable,

    /**
     * The call failed due to a system or protocol error.
     *
     * Covers server-side failures (e.g. 500+ SIP errors), unsupported requests,
     * authentication issues (like 403, 415), or unexpected SIP behavior.
     */
    Error,

    /**
     * The call was canceled before it was answered.
     *
     * Covers cases like 487 (Request Terminated), 481 (Call Does Not Exist),
     * 408 (Request Timeout), or when the caller hangs up before the callee answers.
     */
    Canceled,

    /**
     * The reason for call termination is unknown or unclassified.
     *
     * Used as a fallback when a specific reason or SIP code was not available.
     */
    Unknown
}