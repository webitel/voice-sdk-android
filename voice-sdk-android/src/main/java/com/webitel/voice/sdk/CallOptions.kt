package com.webitel.voice.sdk


/**
 * Options used to configure a call before it is initiated via [VoiceClient.makeCall].
 *
 * @property type The media type to start the call with. See [hasVideo].
 * @property videoQuality The initial video quality preset, used when [type] is [CallType.VIDEO].
 * @property videoOrientation The initial video encoding orientation, used when [type] is [CallType.VIDEO].
 * @property meetingId Optional identifier of the meeting/room to join, if applicable.
 * @property toNumber The destination number or address to call.
 * @property toName A human-readable display name for the destination, used for UI purposes.
 */
data class CallOptions(
    val type: CallType = CallType.AUDIO,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
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
enum class CallType(val code: Long) {
    AUDIO(0),
    VIDEO(1)
}