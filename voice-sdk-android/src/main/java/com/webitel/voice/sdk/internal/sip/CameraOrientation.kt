package com.webitel.voice.sdk.internal.sip

import org.pjsip.pjsua2.pjmedia_orient

internal object CameraOrientation {

    /**
     * Maps a `Surface.ROTATION_*` value to the PJSIP capture orientation.
     *
     * Same table as the upstream pjsua2 Android sample (`CallActivity`), used for every
     * camera: `android_dev.c` itself normalizes back-facing cameras (swaps 90 <-> 270),
     * so no front/back distinction is needed here.
     */
    fun forDeviceRotation(deviceRotation: Int): Int = when (deviceRotation) {
        1    -> pjmedia_orient.PJMEDIA_ORIENT_NATURAL       // ROTATION_90: landscape
        2    -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_90DEG  // ROTATION_180: reverse portrait
        3    -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_180DEG // ROTATION_270: landscape
        else -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_270DEG // ROTATION_0: portrait
    }
}
