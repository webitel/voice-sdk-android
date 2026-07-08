package com.webitel.voice.sdk.internal.sip

import org.pjsip.pjsua2.pjmedia_orient
internal object CameraOrientation {

    fun forDeviceRotation(deviceRotation: Int, isBackCamera: Boolean): Int {

        return if (isBackCamera) {
            val backOrient = when (deviceRotation) {
                1    -> pjmedia_orient.PJMEDIA_ORIENT_NATURAL
                2    -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_90DEG
                3    -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_180DEG
                else -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_270DEG
            }
            backOrient

        } else {
            when (deviceRotation) {
                1    -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_180DEG
                2    -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_90DEG
                3    -> pjmedia_orient.PJMEDIA_ORIENT_NATURAL
                else -> pjmedia_orient.PJMEDIA_ORIENT_ROTATE_270DEG
            }
        }
    }
}
