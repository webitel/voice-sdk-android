package com.webitel.voice.sdk.internal.call

import android.view.Surface
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.internal.sip.CameraOrientation
import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.VideoPreview
import org.pjsip.pjsua2.VideoPreviewOpParam
import org.pjsip.pjsua2.VideoSwitchParam
import org.pjsip.pjsua2.VideoWindow
import java.util.concurrent.ScheduledFuture


/**
 * Local preview, remote rendering and camera selection of one call.
 *
 * Confined to the SIP thread, except [isLocalVideoActive] / [isRemoteVideoActive], which
 * are readable from any thread.
 */
internal class CallVideoController(
    private val host: Host,
    private val delegate: CallControlDelegate,
    initialOrientation: VideoOrientation
) {

    interface Host {
        val sipId: Int
        val isLocalVideoPaused: Boolean
        fun onVideoStateChanged()
        fun onVideoSizeChanged(isLocal: Boolean, width: Int, height: Int)
    }

    @Volatile
    var isLocalVideoActive = false
        private set

    @Volatile
    var isRemoteVideoActive = false
        private set

    private val localHandler = VideoSurfaceHandler()
    private val remoteHandler = VideoSurfaceHandler()
    private var localSurface: Surface? = null
    private var remoteSurface: Surface? = null
    private var videoPreview: VideoPreview? = null
    private var previewStarted = false
    private var currentOrientation = initialOrientation

    /** The camera the user selected; -1 until known. */
    private var currentCameraId = -1

    /** The device actually feeding the call's encoder. */
    private var activeCaptureDeviceId = -1

    /** Whether [currentCameraId] is the front camera; read from any thread. */
    @Volatile
    var isFrontCamera = true
        private set

    private var formatChangedTask: ScheduledFuture<*>? = null


    // region Surfaces

    fun attachSurfaces(local: Surface?, remote: Surface?) {
        if (local != null) {
            localSurface = local
            // Only rebind an existing preview window to the new Surface — never (re)create
            // the VideoPreview here. The video channel can be mid-teardown for its own
            // re-INVITE (e.g. hold/resume); opening a VideoPreview at that moment fails
            // natively (PJ_ENOTFOUND / PJMEDIA_EVID_INVDEV) and corrupts the preview.
            // onShowLocalVideo owns preview creation.
            if (videoPreview != null) localHandler.attach(local)
        }
        if (remote != null) {
            remoteSurface = remote
            val winId = remoteWindowId()
            if (winId != null) {
                remoteHandler.setVideoWindow(VideoWindow(winId))
                remoteHandler.attach(remote)
            }
        }
    }

    // endregion


    // region Media events

    fun onShowLocalVideo(captureDeviceId: Int) {
        if (previewStarted) {
            setLocalActive(true)
            return
        }
        previewStarted = true

        try {
            // captureDeviceId is the device actually feeding the call's encoder right now:
            // PJSIP re-opens its account-default capture device on every hold/resume
            // re-INVITE, forgetting a prior switchCamera(). The VideoPreview must be built
            // on this id — PJSIP shares one device instance between preview and call
            // capture only when the ids match; otherwise it opens a second camera session.
            resyncCaptureDevice(captureDeviceId)
            SdkLogger.debug(TAG, "onShowLocalVideo: capture device id=$activeCaptureDeviceId, selected camera id=$currentCameraId, isFrontCamera=$isFrontCamera")

            // The previous VideoPreview (if any) belongs to a video channel PJSIP already
            // tore down for this re-INVITE; stop()/delete() on it can fail natively. Both
            // must stay non-fatal, or the local preview is left stuck forever.
            try { videoPreview?.stop() } catch (_: Exception) {}
            try { videoPreview?.delete() } catch (_: Exception) {}
            videoPreview = null

            val preview = VideoPreview(activeCaptureDeviceId)
            videoPreview = preview
            val param = VideoPreviewOpParam()
            try {
                param.show = true
                preview.start(param)
            } finally {
                try { param.delete() } catch (_: Exception) {}
            }

            localHandler.setVideoWindow(preview.videoWindow)
            localHandler.attach(localSurface)
            reportLocalSize(preview)

            delegate.sipCall(host.sipId)?.let { call ->
                try {
                    call.startVideoTransmit()
                } catch (e: Exception) {
                    SdkLogger.error(TAG, "onShowLocalVideo: failed to start transmitting: ${e.message}")
                }
                if (host.isLocalVideoPaused) {
                    // Rebuilding the preview force-resumed transmission above; re-apply the
                    // user's pause. No event: the net paused state hasn't changed.
                    try {
                        call.setLocalVideoTransmitting(false)
                    } catch (e: Exception) {
                        SdkLogger.error(TAG, "onShowLocalVideo: failed to re-apply local video pause: ${e.message}")
                    }
                }
            }

            setLocalActive(true)
        } catch (e: Exception) {
            SdkLogger.error(TAG, "onShowLocalVideo: ${e.stackTraceToString()}")
        }
    }


    fun onShowRemoteVideo(windowId: Int) {
        if (windowId >= 0) {
            remoteHandler.setVideoWindow(VideoWindow(windowId))
            remoteHandler.attach(remoteSurface)
        }
        setRemoteActive(true)
    }


    fun onLocalVideoStopped() = setLocalActive(false)


    fun onRemoteVideoStopped() = setRemoteActive(false)


    /**
     * Debounced: re-attaches the render window once PJSIP has settled on the new frame
     * size, then reports it.
     */
    fun onRemoteVideoFormatChanged(width: Int, height: Int) {
        formatChangedTask?.cancel(false)
        formatChangedTask = SdkThreads.postSipDelayed(FORMAT_CHANGE_DEBOUNCE_MS) {
            val winId = remoteWindowId() ?: return@postSipDelayed
            remoteHandler.setVideoWindow(VideoWindow(winId))
            remoteHandler.attach(remoteSurface)
            setRemoteActive(true)
            if (width > 0 && height > 0) host.onVideoSizeChanged(isLocal = false, width = width, height = height)
        }
    }


    /**
     * Re-arms [onShowLocalVideo] before a hold, so it fully re-syncs preview and transmit
     * once the video channel reactivates on resume (PJSIP re-inits the capture device).
     * The preview itself is NOT stopped here: hold only changes the SDP direction, and
     * stopping it while vid_conf adjusts for the hold corrupts its port list (SIGBUS).
     */
    fun prepareForHold() {
        previewStarted = false
    }

    // endregion


    // region Controls

    /** Switches between the front and back camera. @throws IllegalStateException */
    fun switchCamera() {
        val vidDevMgr = Endpoint.instance().vidDevManager()
        val (frontCameraId, backCameraId) = findCameras()

        if (currentCameraId == -1) {
            // switchCamera() before onShowLocalVideo() ever ran (e.g. right after
            // enableVideo(), before video SDP negotiation completes): prefer the call's
            // real capture device, else the front camera the account opens by default.
            val callCaptureId = delegate.mediaInfos(host.sipId)
                ?.firstOrNull { it.isVideo }
                ?.videoCaptureDeviceId
                ?.takeIf { it >= 0 }
            (callCaptureId ?: frontCameraId)?.let { resyncCaptureDevice(it) }
        }

        val isFrontActive = currentCameraId == frontCameraId
        val nextCameraId = if (isFrontActive) {
            backCameraId ?: throw IllegalStateException("Back camera not found")
        } else {
            frontCameraId ?: throw IllegalStateException("Front camera not found")
        }

        val switchParam = VideoSwitchParam()
        try {
            switchParam.target_id = nextCameraId
            vidDevMgr.switchDev(activeCaptureDeviceId, switchParam)
            currentCameraId = nextCameraId
            isFrontCamera = !isFrontActive
            SdkLogger.debug(TAG, "switchCamera: ${if (isFrontActive) "BACK" else "FRONT"} (id=$nextCameraId)")
            videoPreview?.let { reportLocalSize(it) }
        } finally {
            try { switchParam.delete() } catch (_: Exception) {}
        }

        val orient = CameraOrientation.forDeviceRotation(currentOrientation.rotation)
        vidDevMgr.setCaptureOrient(nextCameraId, orient)
    }


    fun setOrientation(orientation: VideoOrientation) {
        currentOrientation = orientation
        delegate.updateVideoOrientation(orientation)
    }


    /** Stops the local preview after video was removed from the call (disableVideo). */
    fun stopPreview() {
        previewStarted = false
        isLocalVideoActive = false
        isRemoteVideoActive = false
        try { videoPreview?.stop() } catch (_: Exception) {}
        try { videoPreview?.delete() } catch (_: Exception) {}
        videoPreview = null
        localHandler.resetVideoWindow()
        remoteHandler.resetVideoWindow()
    }


    /**
     * The call disconnected. Only resets state: PJSIP's own media teardown for this same
     * event is already destroying the preview, windows and renderer, and touching them now
     * races it (SIGSEGV in vid_conf). Returns the native cleanup, which must run after the
     * SIP stack is destroyed (or at least after this event), on the SIP thread.
     */
    fun onDisconnected(): () -> Unit {
        formatChangedTask?.cancel(false)
        formatChangedTask = null
        previewStarted = false
        isLocalVideoActive = false
        isRemoteVideoActive = false

        val preview = videoPreview
        // Keep the Surfaces reachable until the cleanup runs: if GC releases their
        // ANativeWindow while PJSIP's OpenGL renderer still holds it → SIGSEGV.
        val local = localSurface
        val remote = remoteSurface
        videoPreview = null
        localSurface = null
        remoteSurface = null

        return {
            try { preview?.delete() } catch (_: Exception) {}
            localHandler.resetVideoWindow()
            remoteHandler.resetVideoWindow()
            local.let {}
            remote.let {}
        }
    }

    // endregion


    private fun setLocalActive(active: Boolean) {
        if (isLocalVideoActive == active) return
        isLocalVideoActive = active
        host.onVideoStateChanged()
    }


    private fun setRemoteActive(active: Boolean) {
        if (isRemoteVideoActive == active) return
        isRemoteVideoActive = active
        host.onVideoStateChanged()
    }


    private fun remoteWindowId(): Int? {
        return delegate.mediaInfos(host.sipId)
            ?.firstOrNull { it.isVideo && it.videoIncomingWindowId >= 0 }
            ?.videoIncomingWindowId
    }


    private fun reportLocalSize(preview: VideoPreview) {
        try {
            val size = preview.videoWindow.info.size
            host.onVideoSizeChanged(isLocal = true, width = size.w.toInt(), height = size.h.toInt())
        } catch (e: Exception) {
            SdkLogger.error(TAG, "reportLocalSize: ${e.message}")
        }
    }


    /** Front and back camera ids, by device name. */
    private fun findCameras(): Pair<Int?, Int?> {
        val cameras = Endpoint.instance().vidDevManager().enumDev2()
        return try {
            val front = cameras.firstOrNull { it.name.contains("front", ignoreCase = true) }?.id
            val back = cameras.firstOrNull {
                it.name.contains("back", ignoreCase = true) || it.name.contains("rear", ignoreCase = true)
            }?.id
            front to back
        } finally {
            try { cameras.delete() } catch (_: Exception) {}
        }
    }


    /**
     * Keeps the call's actual capture device (whatever PJSIP just opened, e.g. after a
     * hold/resume re-INVITE) in sync with the user's selected camera, fast-switching it in
     * place when they differ — the same mechanism [switchCamera] uses mid-call.
     */
    private fun resyncCaptureDevice(callCaptureDevId: Int) {
        activeCaptureDeviceId = callCaptureDevId
        if (currentCameraId == -1) {
            currentCameraId = callCaptureDevId
            isFrontCamera = when {
                // PJMEDIA_VID_DEFAULT_CAPTURE_DEV: android_dev lists the front camera first.
                callCaptureDevId < 0 -> true
                else -> try {
                    findCameras().first?.let { it == callCaptureDevId } ?: true
                } catch (e: Exception) {
                    true // the account defaults to the front camera
                }
            }
            return
        }
        if (currentCameraId == callCaptureDevId) return

        val switchParam = VideoSwitchParam()
        try {
            switchParam.target_id = currentCameraId
            Endpoint.instance().vidDevManager().switchDev(callCaptureDevId, switchParam)
        } catch (e: Exception) {
            SdkLogger.error(TAG, "resyncCaptureDevice: ${e.message}")
        } finally {
            try { switchParam.delete() } catch (_: Exception) {}
        }
    }


    private companion object {
        const val TAG = "CallVideo"
        const val FORMAT_CHANGE_DEBOUNCE_MS = 300L
    }
}
