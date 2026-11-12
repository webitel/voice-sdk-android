package com.webitel.voice.sdk


/**
 * Options used to configure a call before it is initiated via [VoiceClient.makeCall].
 *
 * @property type The media type to start the call with. See [hasVideo].
 * @property videoOrientation The initial video encoding orientation; applies to video started
 *           with the call ([CallType.VIDEO]) or later via [Call.enableVideo].
 * @property meetingId Optional identifier of the meeting/room to join, if applicable.
 * @property toNumber The destination number or address to call. Defaults to `"service"` —
 *           the portal's service endpoint used for contact-center calls.
 * @property toName A human-readable display name for the destination, used for UI purposes.
 * @property metadata Arbitrary key-value data attached to the outgoing call and delivered to
 *           the server in the SIP INVITE (a single `X-Webitel-Metadata` header with compact
 *           JSON, e.g. `{"lang":"uk","orderId":"12345"}`).
 *
 *           Intended for small identifiers (order id, language, source screen) — NOT for
 *           payloads: the whole INVITE (including SDP) must fit the transport, and on UDP a
 *           large INVITE gets fragmented or dropped. The encoded JSON is limited to
 *           [MAX_METADATA_BYTES]; if the limit is exceeded the metadata is NOT sent (the call
 *           still proceeds) and a warning is logged. The JSON is pure ASCII: non-ASCII
 *           characters are sent as `\uXXXX` escapes, so each counts as 6 bytes towards the
 *           limit (12 for an emoji). Empty keys are ignored. Values are never
 *           logged. Defaults to empty (no header is sent).
 */
data class CallOptions(
    val type: CallType = CallType.AUDIO,
    val videoOrientation: VideoOrientation = VideoOrientation.PORTRAIT,
    val meetingId: String? = null,
    val toNumber: String = "service",
    val toName: String = "Service",
    val metadata: Map<String, String> = emptyMap()
) {
    /** True if the call is configured to start with video. */
    val hasVideo: Boolean
        get() = type == CallType.VIDEO

    companion object {
        /** Maximum size, in bytes, of [metadata] encoded as (ASCII-only) JSON. */
        const val MAX_METADATA_BYTES = 1024
    }
}


/**
 * The media type a call is initiated with.
 */
enum class CallType(internal val code: Long) {
    AUDIO(0),
    VIDEO(1)
}
