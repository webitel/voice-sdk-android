package com.webitel.voice.sdk.internal.call

import android.view.Surface
import com.webitel.voice.sdk.internal.support.SdkLogger
import org.pjsip.pjsua2.VideoWindow
import org.pjsip.pjsua2.VideoWindowHandle


/** Binds a PJSIP video window to an Android [Surface]. SIP thread only. */
internal class VideoSurfaceHandler {
    private var videoWindow: VideoWindow? = null
    private var handle: VideoWindowHandle? = null
    private var active = false


    fun setVideoWindow(vw: VideoWindow) {
        try { videoWindow?.delete() } catch (_: Exception) {}
        videoWindow = vw
        if (handle == null) {
            handle = VideoWindowHandle()
        }
        active = true
    }


    fun resetVideoWindow() {
        active = false
        try { videoWindow?.delete() } catch (_: Exception) {}
        videoWindow = null
        // Do NOT call setWindow(null) here: a null ANativeWindow before PJSIP's renderer
        // (vid_conf) disconnects EGL causes "EGLNativeWindowType disconnect failed", which
        // can corrupt PJSIP's internal port list → SIGBUS. PJSIP disconnects EGL on its own
        // while the Surface is still alive (see CallVideoController.onDisconnected).
        try { handle?.delete() } catch (_: Exception) {}
        handle = null
    }


    fun attach(surface: Surface?) {
        if (!active || handle == null) return
        if (surface == null || !surface.isValid) return
        val vw = videoWindow ?: return
        try {
            handle?.handle?.setWindow(surface)
            vw.setWindow(handle)
        } catch (e: Exception) {
            SdkLogger.error("VideoSurfaceHandler", "attach: ${e.message}")
        }
    }
}
