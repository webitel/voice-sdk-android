package com.webitel.voice.sdk


/**
 * Recommended video quality presets for call sessions.
 *
 * Dimensions represent the **portrait** orientation (width < height, 9:16 aspect ratio).
 * When the device is in landscape mode, width and height are swapped automatically by the SDK
 * based on [VideoOrientation] — call [Call.setVideoOrientation] to trigger the swap.
 *
 * The `width` and `height` fields always refer to the **portrait** short/long side respectively,
 * regardless of actual device orientation at encoding time.
 */
enum class VideoQuality(
    val width: Long,
    val height: Long,
    val fps: Int,
    val avgBitrate: Long, // in bits per second
    val maxBitrate: Long  // in bits per second
) {

    /**
     * Low quality (180×320) — minimal bandwidth. For very poor network conditions.
     */
    LOW(180, 320, 15, 150_000, 300_000),

    /**
     * Medium quality (360×640) — standard 9:16 HD portrait. Sweet spot for most calls.
     */
    MEDIUM(360, 640, 20, 300_000, 600_000),

    /**
     * Standard quality (540×960) — qHD portrait. Good quality on mid-range devices.
     */
    STANDARD(540, 960, 30, 512_000, 1_024_000),

    /**
     * High definition (720×1280) — HD portrait. For modern devices or Wi-Fi.
     */
    HIGH(720, 1280, 30, 1_200_000, 2_048_000),

    /**
     * Full HD (1080×1920) — excellent quality. Flagship devices and strong networks only.
     */
    FULL_HD(1080, 1920, 30, 3_000_000, 4_000_000);

    companion object {
        /**
         * Default recommended preset for Android SDK.
         * Provides good visual quality while staying efficient.
         */
        val DEFAULT = MEDIUM

        /**
         * Reconstructs a [VideoQuality] from its [name] string.
         * Useful for Flutter MethodChannel serialization where enums travel as strings.
         *
         * @return the matching preset, or [DEFAULT] if the name is unrecognized.
         */
        fun fromName(name: String): VideoQuality =
            entries.find { it.name == name } ?: DEFAULT
    }
}