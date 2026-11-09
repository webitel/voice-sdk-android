package com.webitel.voice.sdk


/**
 * Video encoding preset, set for all calls of a client via [CallSettings.videoQuality].
 *
 * The codec parameters are shared by every call of the process, so the quality is a client
 * setting, not a per-call one. A change made through [VoiceClient.configure] applies to
 * calls started afterwards.
 *
 * Video is encoded at 9:16 in portrait and 16:9 in landscape (see [VideoOrientation]).
 * The SDK does not adapt the quality to network conditions during a call — observe
 * [ConnectionEvent.QualityChanged] to react to a poor connection.
 */
enum class VideoQuality(
    /** Short side of the frame in pixels: the "p" in 360p / 720p. */
    internal val shortSide: Long,
    internal val fps: Int,
    internal val avgBitrate: Long, // in bits per second
    internal val maxBitrate: Long  // in bits per second
) {

    /** 360p at 15 fps, ~250 kbps — for weak mobile networks. */
    LOW(360, 15, 250_000, 400_000),

    /** 360p at 24 fps, ~500 kbps — the default; fits most mobile networks. */
    MEDIUM(360, 24, 500_000, 800_000),

    /** 720p at 30 fps, ~1.5 Mbps — for Wi-Fi or a strong network. The maximum supported. */
    HIGH(720, 30, 1_500_000, 2_000_000);


    /** Long side of the frame in pixels (16:9). */
    internal val longSide: Long get() = shortSide * 16 / 9


    companion object {
        /** The default preset: [MEDIUM]. */
        val DEFAULT = MEDIUM

        /**
         * Side of the square decoder buffer. Must fit any remote resolution in either
         * orientation, independent of the local preset — H.264 level 3.1 (`42e01f`, which
         * the SDK advertises) allows at most 1280×720.
         */
        internal const val MAX_DECODE_DIMENSION: Long = 1280
    }
}
