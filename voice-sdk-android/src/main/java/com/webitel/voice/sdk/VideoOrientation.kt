package com.webitel.voice.sdk

import android.view.Surface


/**
 * Describes the exact video encoding orientation used during a call.
 *
 * Maps directly to Android's `Surface.ROTATION_*` values.
 * Pass in [CallOptions.videoOrientation] to set the initial orientation, or use
 * [fromRotation] to convert the current display rotation at runtime.
 * Change during an active call via [Call.setVideoOrientation] to react to device rotation.
 *
 * Note: [LANDSCAPE_RIGHT] (home button on the right, `Surface.ROTATION_90`) and
 * [LANDSCAPE_LEFT] (home button on the left, `Surface.ROTATION_270`) require different
 * camera compensations and must be distinguished explicitly.
 */
enum class VideoOrientation(
    /** The corresponding `Surface.ROTATION_*` value. */
    val rotation: Int
) {

    /** Portrait, device natural position (`Surface.ROTATION_0`). Default for mobile. */
    PORTRAIT(0),

    /** Landscape, home button on the right (`Surface.ROTATION_90`). */
    LANDSCAPE_RIGHT(Surface.ROTATION_90),

    /** Reverse portrait, device upside down (`Surface.ROTATION_180`). */
    PORTRAIT_REVERSED(Surface.ROTATION_180),

    /** Landscape, home button on the left (`Surface.ROTATION_270`). */
    LANDSCAPE_LEFT(Surface.ROTATION_270);


    /** True for [LANDSCAPE_RIGHT] and [LANDSCAPE_LEFT]. */
    val isLandscape: Boolean get() = this == LANDSCAPE_RIGHT || this == LANDSCAPE_LEFT


    companion object {

        /**
         * Converts an Android `Surface.ROTATION_*` value to the corresponding [VideoOrientation].
         *
         * Usage:
         * ```kotlin
         * val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
         *     display?.rotation ?: Surface.ROTATION_0
         * else
         *     @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation
         * val orientation = VideoOrientation.fromRotation(rotation)
         * ```
         */
        fun fromRotation(surfaceRotation: Int): VideoOrientation = when (surfaceRotation) {
            Surface.ROTATION_90  -> LANDSCAPE_RIGHT
            Surface.ROTATION_180 -> PORTRAIT_REVERSED
            Surface.ROTATION_270 -> LANDSCAPE_LEFT
            else                 -> PORTRAIT
        }
    }
}
