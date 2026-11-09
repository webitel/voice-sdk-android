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
 */
data class CallOptions(
    val type: CallType = CallType.AUDIO,
    val videoOrientation: VideoOrientation = VideoOrientation.PORTRAIT,
    val meetingId: String? = null,
    val toNumber: String = "service",
    val toName: String = "Service"
) {
    /** True if the call is configured to start with video. */
    val hasVideo: Boolean
        get() = type == CallType.VIDEO
}


/**
 * The media type a call is initiated with.
 */
enum class CallType(internal val code: Long) {
    AUDIO(0),
    VIDEO(1)
}
