package com.webitel.voice.sdk


/**
 * How well media is delivered between you and the other party during an ongoing call,
 * estimated every few seconds from RTCP statistics (packet loss in both directions and
 * round-trip time).
 *
 * It describes the whole media path — your network, the server and the other party's
 * network — so a bad value doesn't necessarily mean the problem is on your side: if the
 * other party has a weak connection, you will hear the same gaps.
 *
 * Read it from [Call.connectionQuality]; changes are reported with
 * [ConnectionEvent.QualityChanged]. Use it to warn the user about a weak connection or to
 * suggest turning video off — the SDK does not change the video quality by itself.
 *
 * Levels from best to worst: [GOOD], [MEDIUM], [BAD].
 */
enum class ConnectionQuality {

    /** Not measured yet, the call is on hold, or the call is not ongoing. */
    UNKNOWN,

    /** Packet loss below 3% and round-trip time below 300 ms. */
    GOOD,

    /** Noticeable loss or delay: audio may stutter, video may freeze briefly. */
    MEDIUM,

    /**
     * Packet loss above 10%, round-trip time above 700 ms, or no media received from the
     * other party: the call is barely usable.
     */
    BAD
}
