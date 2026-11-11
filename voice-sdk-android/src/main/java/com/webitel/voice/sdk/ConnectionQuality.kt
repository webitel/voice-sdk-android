package com.webitel.voice.sdk


/**
 * How well media is delivered between this device and the Webitel media server during an
 * ongoing call, estimated every few seconds from RTCP statistics of the audio stream
 * (packet loss in both directions and round-trip time).
 *
 * It describes **your** connection to the server only. Media is relayed by the server, so
 * the other party's network is measured on their own leg and is mostly invisible here: when
 * they have a weak connection you may hear gaps while this still reports [GOOD]. The one
 * exception is a complete loss of incoming media, which is reported as [BAD] (unless the
 * other party reported being muted).
 *
 * Outgoing loss and round-trip time come from the server's RTCP reports; if the server
 * doesn't send them, only incoming loss is taken into account.
 *
 * Read it from [Call.connectionQuality]; changes are reported with
 * [ConnectionEvent.QualityChanged]. Use it to warn the user about a weak connection or to
 * suggest turning video off — the SDK does not change the video quality by itself.
 *
 * Levels from best to worst: [GOOD], [MEDIUM], [BAD].
 */
enum class ConnectionQuality {

    /** Not measured yet, the call is on hold (local or remote), or the call is not ongoing. */
    UNKNOWN,

    /** Packet loss below 3% and round-trip time below 300 ms. */
    GOOD,

    /** Noticeable loss or delay: audio may stutter, video may freeze briefly. */
    MEDIUM,

    /**
     * Packet loss above 10%, round-trip time above 700 ms, or no media received from the
     * server: the call is barely usable.
     */
    BAD
}
