package com.webitel.voice.sdk.internal.voice

import android.view.Surface
import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallEvent
import com.webitel.voice.sdk.CallEventListener
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.CallType
import com.webitel.voice.sdk.ConnectionEvent
import com.webitel.voice.sdk.LocalMediaEvent
import com.webitel.voice.sdk.RemoteMediaEvent
import com.webitel.voice.sdk.VideoEvent
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.VideoState
import com.webitel.voice.sdk.internal.sip.CameraOrientation
import com.webitel.voice.sdk.internal.sip.PJCall
import com.webitel.voice.sdk.internal.sip.SipCallCallbacks
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import com.webitel.voice.sdk.isSameAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.pjsip.pjsua2.CallMediaInfo
import org.pjsip.pjsua2.CallVidSetStreamParam
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.VideoPreview
import org.pjsip.pjsua2.VideoPreviewOpParam
import org.pjsip.pjsua2.VideoSwitchParam
import org.pjsip.pjsua2.VideoWindow
import org.pjsip.pjsua2.VideoWindowHandle
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsua_call_vid_strm_op
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.Exception
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.Throws


internal class WebitelCall(
    private val listener: CallStateListener,
    private var options: CallOptions,
    private val ratingProvider: CallRatingProvider,
    private val audioRouter: CallAudioRouter
): Call, SipCallCallbacks {
    private val localHandler = VideoSurfaceHandler()
    private val remoteHandler = VideoSurfaceHandler()
    override val id: String = UUID.randomUUID().toString()
    private var pjCall: PJCall? = null
    private val listeners: MutableSet<CallEventListener> =
        Collections.newSetFromMap(ConcurrentHashMap())

    override val type: CallType
        get() = options.type

    private var currentOrientation: VideoOrientation = options.videoOrientation

    private var isLocalVideoActive = false
    private var isRemoteVideoActive = false
    private var videoCountOnHold: Long = 0L

    override val videoState: VideoState
        get() = when {
            isLocalVideoActive && isRemoteVideoActive -> VideoState.ACTIVE
            isLocalVideoActive -> VideoState.LOCAL_ONLY
            isRemoteVideoActive -> VideoState.REMOTE_ONLY
            else -> VideoState.INACTIVE
        }

    override var isLocalVideoPaused: Boolean = false
        private set

    override var isRemoteMuted: Boolean = false
        private set
    override var isRemoteOnHold: Boolean = false
        private set
    override var isRemoteVideoPaused: Boolean = false
        private set
    private var localSurface: Surface? = null
    private var remoteSurface: Surface? = null
    private var currentCameraId: Int = -1
    private var activeCaptureDeviceId: Int = -1
    private var videoPreview: VideoPreview? = null

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var previewStarted: Boolean = false
    private var formatChangedJob: Job? = null

    override var state: CallState = CallState.IDLE
        private set

    override val isMuted: Boolean
        get() {
            return pjCall?.isOnMute ?: false
        }

    override val isSpeakerphoneOn: Boolean
        get() = audioRouter.isSpeakerphoneOn

    override var isOnHold: Boolean = false
        private set

    override var answeredAt: Long = 0
        private set

    override var isOutgoing: Boolean = true
        private set

    private val isAnswered: Boolean
        get() { return answeredAt > 0 }


    override fun mute(mute: Boolean): Result<Unit> {
        val call = pjCall
        if (state == CallState.IDLE || state is CallState.Disconnected) {
            val message = "Mute failed: call is not active. Current state: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (call == null) {
            val message = "Mute failed: pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (isMuted == mute) {
            return Result.success(Unit)
        }

        return try {
            logger.debug("WCall", "mute: $mute")
            call.setMute(mute)
            fireMuteChanged(mute)
            if (state == CallState.Ongoing) sendMediaStateInfo()
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            // No active audio media yet (e.g. still Connecting/Ringing) — record the
            // desired state; PJCall's mute-restore logic (onCallMediaState) applies it
            // for real as soon as audio media actually activates.
            logger.debug("WCall", "mute (pending): $mute")
            call.setDesiredMute(mute)
            fireMuteChanged(mute)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "setMute error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun setSpeakerphoneOn(enabled: Boolean): Result<Unit> {
        if (state != CallState.Ongoing) {
            val message = "setSpeakerphoneOn failed: call is not in an ongoing state. Current state: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        return audioRouter.setSpeakerphoneOn(enabled)
            .onSuccess { fireSpeakerphoneChanged(enabled) }
    }


    override fun hold(hold: Boolean): Result<Unit> {
        val call = pjCall
        if (state != CallState.Ongoing) {
            val message = "Hold failed: call is not in an ongoing state. Current state: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (call == null) {
            val message = "hold failed: pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (isOnHold == hold) {
            return Result.success(Unit)
        }

        return try {
            logger.debug("WCall", "hold: $hold")
            checkThread()
            if (hold) {
                videoCountOnHold = if (videoState != VideoState.INACTIVE) 1L else 0L
                if (videoCountOnHold == 1L) {
                    // Re-arm onShowLocalVideo() so it fully re-syncs preview + transmit
                    // to currentCameraId once the video media channel reactivates after
                    // resume (PJSIP re-inits the capture device on the re-INVITE).
                    synchronized(this) { previewStarted = false }
                }
                call.setHold(videoCountOnHold)
            } else {
                call.setUnHold(videoCountOnHold)
            }

            return Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("WCall", "setHold error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun sendDTMF(digits: String): Result<Unit> {
        val call = pjCall
        if (state != CallState.Ongoing) {
            val message = "DTMF send failed: call is not active. State: $state"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (call == null) {
            val message = "failed pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (digits.isEmpty()) {
            return Result.failure(IllegalArgumentException("DTMF digits must not be empty"))
        }

        return try {
            logger.debug("WCall", "sendDTMF: $digits")
            call.sendDtmf(digits)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "sendDTMF: error: ${e.message}")
            Result.failure(e)
        }
    }


    /** Switch to the next available camera (cyclically). */
    override fun switchCamera(): Result<Unit> {
        return try {
            if (!isVideo()) {
                val message = "The call does not support video."
                logger.error("WCall", message)
                return Result.failure(IllegalStateException(message))
            }
            if (state == CallState.IDLE || state is CallState.Disconnected) {
                val message = "switchCamera failed: call is not active. State: $state"
                logger.error("WCall", message)
                return Result.failure(IllegalStateException(message))
            }

            checkThread()

            val endpoint = Endpoint.instance()
            val vidDevMgr = endpoint.vidDevManager()
            val cameras = vidDevMgr.enumDev2()
            val frontCameraId: Int?
            val backCameraId: Int?
            try {
                frontCameraId = cameras.firstOrNull { it.name.contains("front", ignoreCase = true) }?.id
                backCameraId = cameras.firstOrNull {
                    it.name.contains("back", ignoreCase = true)
                        || it.name.contains("rear", ignoreCase = true)
                }?.id
            } finally {
                try { cameras.delete() } catch (_: Exception) {}
            }

            if (currentCameraId == -1) {
                // switchCamera() called before onShowLocalVideo() ever ran (e.g. right after
                // enableVideo(), before SDP negotiation for video completes) —
                // currentCameraId/activeCaptureDeviceId are still unset. Prefer the call's
                // real current capture device; if that's not available yet either, the
                // account always opens the front camera as its default, so fall back to that
                // instead of leaving it unset (which silently no-ops the first press).
                val callInfo = pjCall?.info
                var vmi: CallMediaInfo? = null
                try {
                    vmi = callInfo?.media?.firstOrNull { it.type == pjmedia_type.PJMEDIA_TYPE_VIDEO }
                    val seedCaptureDevId = vmi?.videoCapDev?.takeIf { it >= 0 } ?: frontCameraId
                    seedCaptureDevId?.let { resyncCaptureDevice(it) }
                } finally {
                    try { vmi?.delete() } catch (_: Exception) {}
                    try { callInfo?.delete() } catch (_: Exception) {}
                }
            }

            val isFrontActive = currentCameraId == frontCameraId

            val nextCameraId = if (isFrontActive) {
                backCameraId
                    ?: return Result.failure(IllegalStateException("Back camera not found"))
            } else {
                frontCameraId
                    ?: return Result.failure(IllegalStateException("Front camera not found"))
            }

            val switchParam = VideoSwitchParam()
            switchParam.target_id = nextCameraId

            try {
                vidDevMgr.switchDev(activeCaptureDeviceId, switchParam)
                currentCameraId = nextCameraId
                logger.debug("WCall",
                    "switchCamera: ${if (isFrontActive) "BACK" else "FRONT"} (id=$nextCameraId)"
                )
                try {
                    videoPreview?.videoWindow?.info?.size?.let { size ->
                        fireVideoSizeChanged(isLocal = true, width = size.w.toInt(), height = size.h.toInt())
                    }
                } catch (e: Exception) {
                    logger.error("WCall", "switchCamera: failed reading new camera size: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error("WCall", "switchCamera: ${e.stackTraceToString()}")
            } finally {
                try { switchParam.delete() } catch (_: Exception) {}
            }

            val rotation = currentOrientation.rotation
            val switchingToBack = isFrontActive  // if front was active, we're switching to back
            val newOrient = CameraOrientation.forDeviceRotation(rotation, isBackCamera = switchingToBack)
            vidDevMgr.setCaptureOrient(nextCameraId, newOrient)

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "switchCamera: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }


    override fun attachVideoSurfaces(
        localSurface: Surface,
        remoteSurface: Surface
    ): Result<Unit> {
        if (!isVideo()) {
            val message = "attachVideoSurfaces failed: no video active. Enable video first via enableVideo()."
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (!localSurface.isValid) {
            val message = "Local Surface is not valid"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (!remoteSurface.isValid) {
            val message = "Remote Surface is not valid"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        return try {
            checkThread()
            logger.debug("WCall", "attachVideoSurfaces")
            setVideoSurfaces(localSurface, remoteSurface)
            if (pjCall == null) return Result.success(Unit)
            synchronized(this) {
                // Only rebind the existing preview window to the new Surface here — never
                // create/recreate the VideoPreview itself. This gets called synchronously
                // from inside pjsip's own media-state callback stack (via onVideoStateChanged
                // reacting to hold/resume), while the video channel can be mid-teardown for
                // its own re-INVITE; touching the capture device or opening a VideoPreview at
                // that moment fails natively (PJ_ENOTFOUND / PJMEDIA_EVID_INVDEV) and corrupts
                // the preview permanently. onShowLocalVideo() owns preview creation.
                if (videoPreview != null) {
                    localHandler.attach(localSurface)
                }

                val callInfo = pjCall?.info
                var vmi: CallMediaInfo? = null
                try {
                    vmi = callInfo?.media?.firstOrNull {
                        it.type == pjmedia_type.PJMEDIA_TYPE_VIDEO && it.videoIncomingWindowId >= 0
                    }
                    val winId = vmi?.videoIncomingWindowId
                    if (winId != null && winId >= 0) {
                        remoteHandler.setVideoWindow(VideoWindow(winId))
                        remoteHandler.attach(remoteSurface)
                    }
                } finally {
                    try { vmi?.delete() } catch (_: Exception) {}
                    try { callInfo?.delete() } catch (_: Exception) {}
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "attachVideoSurfaces: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }


    override fun attachLocalVideoSurface(surface: Surface): Result<Unit> {
        if (!isVideo()) {
            val message = "attachLocalVideoSurface failed: no video active. Enable video first via enableVideo()."
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (!surface.isValid) {
            val message = "Local Surface is not valid"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        return try {
            checkThread()
            logger.debug("WCall", "attachLocalVideoSurface")
            localSurface = surface
            if (pjCall == null) return Result.success(Unit)
            synchronized(this) {
                // See attachVideoSurfaces() — only rebind, never (re)create the VideoPreview.
                if (videoPreview != null) {
                    localHandler.attach(surface)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "attachLocalVideoSurface: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }


    override fun attachRemoteVideoSurface(surface: Surface): Result<Unit> {
        if (!isVideo()) {
            val message = "attachRemoteVideoSurface failed: no video active. Enable video first via enableVideo()."
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (!surface.isValid) {
            val message = "Remote Surface is not valid"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        return try {
            checkThread()
            logger.debug("WCall", "attachRemoteVideoSurface")
            remoteSurface = surface
            synchronized(this) {
                val callInfo = pjCall?.info
                var vmi: CallMediaInfo? = null
                try {
                    vmi = callInfo?.media?.firstOrNull {
                        it.type == pjmedia_type.PJMEDIA_TYPE_VIDEO && it.videoIncomingWindowId >= 0
                    }
                    val winId = vmi?.videoIncomingWindowId
                    if (winId != null && winId >= 0) {
                        remoteHandler.setVideoWindow(VideoWindow(winId))
                        remoteHandler.attach(surface)
                    }
                } finally {
                    try { vmi?.delete() } catch (_: Exception) {}
                    try { callInfo?.delete() } catch (_: Exception) {}
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "attachRemoteVideoSurface: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }


    override fun enableVideo(): Result<Unit> {
        if (state != CallState.Ongoing) {
            val message = "enableVideo failed: call is not ongoing. State: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }
        if (videoState != VideoState.INACTIVE) {
            return Result.success(Unit)
        }
        val call = pjCall ?: return Result.failure(IllegalStateException("enableVideo failed: pjCall is null"))
        return try {
            checkThread()
            call.reinviteWithVideoCount(1L)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "enableVideo error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun setVideoOrientation(orientation: VideoOrientation): Result<Unit> {
        if (state != CallState.Ongoing) {
            val message = "setVideoOrientation failed: call not ongoing. State: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }
        return try {
            checkThread()
            currentOrientation = orientation
            VoiceManager.updateVideoOrientation(options.videoQuality, orientation, isCurrentCameraBack())
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "setVideoOrientation error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun disableVideo(): Result<Unit> {
        if (state != CallState.Ongoing) {
            val message = "disableVideo failed: call is not ongoing. State: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }
        if (videoState == VideoState.INACTIVE) {
            return Result.success(Unit)
        }
        val call = pjCall ?: return Result.failure(IllegalStateException("disableVideo failed: pjCall is null"))
        return try {
            checkThread()
            val wasPaused = isLocalVideoPaused
            call.reinviteWithVideoCount(0L)
            stopPreview()
            if (wasPaused) {
                fireLocalVideoPausedChanged(false)
            }
            fireVideoStateChanged()
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "disableVideo error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun setLocalVideoPaused(paused: Boolean): Result<Unit> {
        val call = pjCall
        if (state == CallState.IDLE || state is CallState.Disconnected) {
            val message = "setLocalVideoPaused failed: call is not active. State: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }
        if (call == null) {
            val message = "setLocalVideoPaused failed: pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }
        if (!isVideo()) {
            val message = "setLocalVideoPaused failed: call does not support video."
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }
        if (isLocalVideoPaused == paused) {
            return Result.success(Unit)
        }

        return try {
            checkThread()
            call.setLocalVideoTransmitting(!paused)
            isLocalVideoPaused = paused
            fireLocalVideoPausedChanged(paused)
            if (state == CallState.Ongoing) sendMediaStateInfo()
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            // No active local video stream yet (e.g. still Connecting/Ringing, or video
            // temporarily inactive) — record the desired state; onShowLocalVideo()'s
            // re-apply logic pauses transmission for real as soon as local video
            // actually activates.
            isLocalVideoPaused = paused
            fireLocalVideoPausedChanged(paused)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "setLocalVideoPaused error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun disconnect(): Result<Unit> {
        return try {
            logger.debug("WCall", "on disconnect call")
            internalDisconnect(fromCode(0), cancelScope = true)
            Result.success(Unit)
        }catch (e: Exception) {
            logger.error("WCall", "disconnect: ${e.message}")
            Result.failure(e)
        }
    }


    override fun addEventListener(listener: CallEventListener) {
        listeners += listener
    }


    override fun removeEventListener(listener: CallEventListener) {
        listeners -= listener
    }


    override fun removeAllEventListeners() {
        listeners.clear()
    }


    override fun isRatable(callback: (Result<Boolean>) -> Unit) {
        val meetingId = options.meetingId
        if (meetingId.isNullOrBlank()) {
            callback(Result.success(false))
            return
        }
        scope.launch { callback(ratingProvider.checkRatable(meetingId)) }
    }


    override fun rate(satisfaction: String, callback: (Result<Unit>) -> Unit) {
        val meetingId = options.meetingId
        if (meetingId.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("rate failed: call has no meetingId")))
            return
        }
        scope.launch { callback(ratingProvider.submitRating(meetingId, satisfaction)) }
    }


    override fun onHoldCallPJSIP(hold: Boolean) {
        onHold(hold)
        if (!hold) {
            listener.onActiveCall(id)
        }
        // previewStarted is no longer touched here: hold/unhold only pauses the media
        // direction in the SDP, it does not stop the local camera capture. See
        // PJCall.handleVideoMedia() — LOCAL_HOLD/REMOTE_HOLD leaves the local preview
        // untouched, so there is nothing to reset when the call goes on/off hold.
    }

    override fun onRemoteMediaStateInfo(audioMuted: Boolean?, videoMuted: Boolean?, hold: Boolean?) {
        if (audioMuted != null && audioMuted != isRemoteMuted) {
            isRemoteMuted = audioMuted
            fireRemoteMuteChanged(audioMuted)
        }
        if (hold != null && hold != isRemoteOnHold) {
            isRemoteOnHold = hold
            fireRemoteHoldChanged(hold)
        }
        if (videoMuted != null && videoMuted != isRemoteVideoPaused) {
            isRemoteVideoPaused = videoMuted
            fireRemoteVideoPausedChanged(videoMuted)
        }
    }


    fun resumeVideo() {
        val callVidPrm = CallVidSetStreamParam()
        try {
            pjCall?.vidSetStream(
                pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_START_TRANSMIT,
                callVidPrm
            )
        } catch (e: Exception) {
            logger.error("WCall", "resumeVideo: ${e.stackTraceToString()}")
        } finally {
            try { callVidPrm.delete() } catch (_: Exception) {}
        }
    }


    override fun onShowRemoteVideo(cmi: CallMediaInfo) {
        logger.debug("WCall", "onShowRemoteVideo")
        checkThread()
        val winId = cmi.videoIncomingWindowId
        if (winId >= 0) {
            synchronized(this) {
                remoteHandler.setVideoWindow(VideoWindow(winId))
                remoteHandler.attach(remoteSurface)
            }
        }
        if (!isRemoteVideoActive) {
            isRemoteVideoActive = true
            fireVideoStateChanged()
        }
    }


    override fun onLocalVideoStopped() {
        if (!isLocalVideoActive) return
        isLocalVideoActive = false
        fireVideoStateChanged()
    }


    override fun onRemoteVideoStopped() {
        if (!isRemoteVideoActive) return
        isRemoteVideoActive = false
        fireVideoStateChanged()
    }


    override fun onRemoteVideoFormatChanged(width: Int, height: Int) {
        logger.debug("WCall", "onRemoteVideoFormatChanged: event size=${width}x${height}")
        formatChangedJob?.cancel()
        formatChangedJob = scope.launch {
            kotlinx.coroutines.delay(300)
            try {
                checkThread()
                val callInfo = pjCall?.info ?: return@launch
                var winId: Int? = null
                var vmi: CallMediaInfo? = null
                try {
                    vmi = callInfo.media.firstOrNull {
                        it.type == pjmedia_type.PJMEDIA_TYPE_VIDEO &&
                        it.videoIncomingWindowId >= 0
                    }
                    winId = vmi?.videoIncomingWindowId
                } finally {
                    try { vmi?.delete() } catch (_: Exception) {}
                    try { callInfo.delete() } catch (_: Exception) {}
                }
                if (winId == null || winId < 0) return@launch
                synchronized(this@WebitelCall) {
                    remoteHandler.setVideoWindow(VideoWindow(winId))
                    remoteHandler.attach(remoteSurface)
                }
                if (!isRemoteVideoActive) {
                    isRemoteVideoActive = true
                    fireVideoStateChanged()
                }
                // DIAGNOSTIC: compares the render window's actual size against the event's
                // reported size — if the window is always square regardless of event size,
                // SipManager's forced-square decFmt (applyVideoQualitySafe) is distorting the
                // frame before it reaches this window, and needs its own separate fix.
                try {
                    val winSize = VideoWindow(winId).info.size
                    logger.debug("WCall",
                        "onRemoteVideoFormatChanged: render window size=${winSize.w}x${winSize.h} " +
                        "vs event size=${width}x${height}"
                    )
                } catch (e: Exception) {
                    logger.error("WCall", "onRemoteVideoFormatChanged: failed reading window size: ${e.message}")
                }
                if (width > 0 && height > 0) {
                    fireVideoSizeChanged(isLocal = false, width = width, height = height)
                }
            } catch (e: Exception) {
                logger.error("WCall", "onRemoteVideoFormatChanged: ${e.message}")
            }
        }
    }


    override fun onShowLocalVideo(cmi: CallMediaInfo) {
        logger.debug("WCall", "onShowLocalVideo")
        synchronized(this) {
            if (previewStarted) {
                if (!isLocalVideoActive) {
                    isLocalVideoActive = true
                    fireVideoStateChanged()
                }
                return
            }
            previewStarted = true
        }
        checkThread()

        try {
            // cmi.videoCapDev is the device actually feeding the call's own encoder right
            // now (pjsip re-opens its account-default capture device on every hold/resume
            // re-INVITE, forgetting any prior switchCamera() selection). VideoPreview must be
            // built on THIS id, not on the user's selected camera id directly — pjsip shares
            // a single device instance between preview and call capture only when the ids
            // match; otherwise it opens a second, disconnected camera session.
            resyncCaptureDevice(cmi.videoCapDev)

            logger.debug("WCall",
                "onShowLocalVideo: call capture device id=$activeCaptureDeviceId, selected camera id=$currentCameraId"
            )

            synchronized(this) {
                // The previous VideoPreview (if any) belongs to a video channel pjsip has
                // already torn down for this re-INVITE — stop()/delete() on it can fail
                // natively (e.g. PJ_ENOTFOUND). Both must stay non-fatal: if either throws,
                // we still need to fall through and create a fresh, working preview below,
                // or the local preview is left stuck forever.
                try { videoPreview?.stop() } catch (_: Exception) {}
                try { videoPreview?.delete() } catch (_: Exception) {}
                videoPreview = null

                val vp = VideoPreview(activeCaptureDeviceId)
                videoPreview = vp

                val param = VideoPreviewOpParam()
                param.show = true

                try {
                    videoPreview?.start(param)
                } finally {
                    try { param.delete() } catch (_: Exception) {}
                }

                localHandler.setVideoWindow(vp.videoWindow)
                localHandler.attach(localSurface)

                try {
                    val size = vp.videoWindow.info.size
                    fireVideoSizeChanged(isLocal = true, width = size.w.toInt(), height = size.h.toInt())
                } catch (e: Exception) {
                    logger.error("WCall", "onShowLocalVideo: failed reading local window size: ${e.message}")
                }
            }

            logger.debug("WCall",
                "onShowLocalVideo: Local preview started successfully"
            )

            resumeVideo()

            if (isLocalVideoPaused) {
                // A preview rebuild (e.g. unhold re-arming previewStarted) just force-resumed
                // transmission via resumeVideo() above — re-apply the user's pause so it
                // survives transparently. No event: the net paused state hasn't changed.
                try {
                    pjCall?.setLocalVideoTransmitting(false)
                } catch (e: Exception) {
                    logger.error("WCall", "onShowLocalVideo: failed to re-apply local video pause: ${e.message}")
                }
            }

            if (!isLocalVideoActive) {
                isLocalVideoActive = true
                fireVideoStateChanged()
            }

        } catch (e: Exception) {
            logger.error("WCall",
                "onShowLocalVideo: Failed showing local video: ${e.stackTraceToString()}"
            )
        }
    }


    override fun onCallStatePJSIP(state: Int, lastStatusCode: Int, lastReason: String) {
        checkThread()
        when (state) {
            pjsip_inv_state.PJSIP_INV_STATE_NULL,
            pjsip_inv_state.PJSIP_INV_STATE_CONNECTING,
            pjsip_inv_state.PJSIP_INV_STATE_CALLING -> {
                onCallState(CallState.Connecting)
            }

            pjsip_inv_state.PJSIP_INV_STATE_EARLY -> {
                if (isOutgoing) onCallState(CallState.Ringing) // outgoing, remote is ringing
                else onCallState(CallState.Connecting)  // incoming, still negotiating
            }

            pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> {
                onCallState(CallState.Ringing)
            } // incoming ringing

            pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> {
                if (!isAnswered) {
                    answeredAt = System.currentTimeMillis()
                }
                onCallState(CallState.Ongoing)
            }

            pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> {
                // Do NOT call stopPreview() here — PJSIP's own call-media teardown
                // (triggered by this same disconnect) is already destroying the preview,
                // video windows/streams and OpenGL renderer on this call, on this very
                // thread and concurrently on its vid_conf worker thread. Calling
                // pjsua_vid_preview_stop() / deleting the VideoWindow wrappers again here
                // races that teardown and corrupts vid_conf's port list / renderer state
                // → SIGSEGV ~100-200ms later. Only reset Kotlin-side flags synchronously;
                // native cleanup is deferred below to run after shutdownStack().
                previewStarted = false
                isLocalVideoActive = false
                isRemoteVideoActive = false
                isLocalVideoPaused = false

                // Do NOT call pjCall.delete() here (or deferred to VoiceThread, or anywhere
                // else after DISCONNECTED) — confirmed by testing that deleting the Call/
                // PJCall SWIG wrapper at all races PJSIP's own internal freeing/reuse of
                // this call slot right after onCallState() returns, corrupting its state
                // → SIGSEGV. Just drop the reference and let the wrapper be garbage
                // collected; the whole native stack is destroyed shortly after anyway
                // (shutdownStack() below).
                val vp = videoPreview
                val s1 = localSurface
                val s2 = remoteSurface
                pjCall = null
                videoPreview = null
                localSurface = null
                remoteSurface = null
                listener.onEndCall(id)
                // Runs after shutdownStack() (queued by onEndCall above) — PJSIP's stack
                // is fully torn down by then, so no concurrent native teardown can race
                // these deletes. Also keep s1/s2 alive until this point: dropping them
                // early lets GC call ANativeWindow_release() while PJSIP's OpenGL renderer
                // still holds a raw ANativeWindow* → fault addr 0x4 SIGSEGV.
                listener.onScheduleVoiceThread {
                    try { vp?.delete() } catch (_: Exception) {}
                    localHandler.resetVideoWindow()
                    remoteHandler.resetVideoWindow()
                    s1.let {}
                    s2.let {}
                }
                onCallState(CallState.Disconnected(fromCode(lastStatusCode, lastReason)))
            }
        }
    }

    fun isVideo() = type == CallType.VIDEO || videoState != VideoState.INACTIVE


    fun setVideoSurfaces(local: Surface, remote: Surface) {
        localSurface = local
        remoteSurface = remote
    }


    fun disconnectWithReason(callEndReason: CallEndReason) {
        try {
            internalDisconnect(callEndReason, cancelScope = false)
        } catch (e: Exception) {
            logger.error("WCall", "disconnectWithReason: ${e.message}")
        }
    }


    fun setSipVoice(pjCall: PJCall) {
        this.pjCall = pjCall
    }


    fun launchInScope(block: suspend CoroutineScope.() -> Unit) {
        job = scope.launch {
            sendConnectingEvent()
            block()
        }
    }


    /**
     * Whether [currentCameraId] refers to the back camera, using the same enumDev2()-by-name
     * lookup [switchCamera] already relies on. VidDevManager.isCaptureActive() turned out
     * unreliable for this (always reported the front camera as active), so orientation
     * updates must be told the active camera explicitly instead of guessing it natively.
     */
    private fun isCurrentCameraBack(): Boolean {
        if (currentCameraId == -1) return false // account defaults to the front camera
        return try {
            val cameras = Endpoint.instance().vidDevManager().enumDev2()
            try {
                val frontCameraId = cameras.firstOrNull { it.name.contains("front", ignoreCase = true) }?.id
                currentCameraId != frontCameraId
            } finally {
                try { cameras.delete() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            false
        }
    }


    /**
     * Keeps the call's actual capture device (whatever pjsip just opened for this call's
     * own encoder, e.g. after a hold/resume re-INVITE) in sync with the user's selected
     * camera. Fast-switches [callCaptureDevId] onto [currentCameraId] in place when they
     * differ — the same mechanism [switchCamera] already uses successfully mid-call.
     */
    private fun resyncCaptureDevice(callCaptureDevId: Int) {
        activeCaptureDeviceId = callCaptureDevId
        if (currentCameraId == -1) {
            currentCameraId = callCaptureDevId
            return
        }
        if (currentCameraId == callCaptureDevId) return

        val switchParam = VideoSwitchParam()
        switchParam.target_id = currentCameraId
        try {
            Endpoint.instance().vidDevManager().switchDev(callCaptureDevId, switchParam)
        } catch (e: Exception) {
            logger.error("WCall", "resyncCaptureDevice: ${e.stackTraceToString()}")
        } finally {
            try { switchParam.delete() } catch (_: Exception) {}
        }
    }


    private fun stopPreview(clearSurfaces: Boolean = false) {
        checkThread()
        previewStarted = false
        // Reset video flags without firing events — disconnect already signals state change
        isLocalVideoActive = false
        isRemoteVideoActive = false
        isLocalVideoPaused = false
        isRemoteMuted = false
        isRemoteOnHold = false
        isRemoteVideoPaused = false
        try { videoPreview?.stop() } catch (_: Exception) {}
        try { videoPreview?.delete() } catch (_: Exception) {}
        videoPreview = null
        localHandler.resetVideoWindow()
        remoteHandler.resetVideoWindow()
        if (clearSurfaces) {
            localSurface = null
            remoteSurface = null
        }
    }


    private fun cancelScope() {
        job?.cancel(CancellationException())
        job = null
    }


    @Throws(IllegalStateException::class)
    private fun internalDisconnect(reason: CallEndReason, cancelScope: Boolean) {
        if (state is CallState.Disconnected) {
            throw IllegalStateException("unavailable, call is DISCONNECTED.")
        }

        if (pjCall == null) {
            if (cancelScope) cancelScope()
            listener.onEndCall(id)
            onCallState(CallState.Disconnected(reason))
        } else {
            checkThread()
            pjCall?.hangup(getHangupCode(true))
        }
    }


    private fun sendConnectingEvent() {
        onCallState(CallState.Connecting)
    }


    private fun getHangupCode(busyEverywhere: Boolean): Int {
        return when {
            isAnswered -> pjsip_status_code.PJSIP_SC_OK
            !isOutgoing -> {
                if (busyEverywhere) {
                    pjsip_status_code.PJSIP_SC_BUSY_EVERYWHERE
                }else {
                    pjsip_status_code.PJSIP_SC_BUSY_HERE
                }
            }
            else -> pjsip_status_code.PJSIP_SC_REQUEST_TERMINATED
        }
    }


    private val lock = Any()
    private fun onCallState(state: CallState) {
        synchronized(lock) {
            if (!this.state.isSameAs(state)) {
                val oldState = this.state
                this.state = state
                logger.debug("WCall", "onCallState: from - $oldState, to - $state")
                fireEvent(ConnectionEvent.StateChanged(id, state))
                if (state == CallState.Ongoing) {
                    sendMediaStateInfo()
                }
            }
        }
    }


    private fun onHold(onHold: Boolean) {
        if (isOnHold != onHold) {
            isOnHold = onHold
            logger.debug("WCall", "onHold: new - $onHold")
            fireEvent(LocalMediaEvent.HoldChanged(id, onHold))
            sendMediaStateInfo()
        }
    }


    private fun sendMediaStateInfo() {
        val call = pjCall ?: return
        try {
            val json = JSONObject()
                .put("videoMuted", isLocalVideoPaused)
                .put("audioMuted", isMuted)
                .put("hold", isOnHold)
                .toString()
            logger.debug("WCall", "sendMediaStateInfo: ${json}")
            call.sendInfo("application/json", json)
        } catch (e: Exception) {
            logger.error("WCall", "sendMediaStateInfo error: ${e.message}")
        }
    }


    private fun fireMuteChanged(isMuted: Boolean) {
        fireEvent(LocalMediaEvent.MuteChanged(id, isMuted))
    }


    private fun fireSpeakerphoneChanged(isSpeakerphoneOn: Boolean) {
        fireEvent(LocalMediaEvent.SpeakerphoneChanged(id, isSpeakerphoneOn))
    }


    private fun fireVideoStateChanged() {
        val state = videoState
        logger.debug("WCall", "fireVideoStateChanged: $state")
        fireEvent(VideoEvent.StateChanged(id, state))
    }


    private fun fireVideoSizeChanged(isLocal: Boolean, width: Int, height: Int) {
        logger.debug("WCall", "fireVideoSizeChanged: isLocal=$isLocal ${width}x${height}")
        fireEvent(VideoEvent.SizeChanged(id, isLocal, width, height))
    }


    private fun fireLocalVideoPausedChanged(isPaused: Boolean) {
        logger.debug("WCall", "fireLocalVideoPausedChanged: $isPaused")
        fireEvent(LocalMediaEvent.VideoPausedChanged(id, isPaused))
    }


    private fun fireRemoteMuteChanged(isMuted: Boolean) {
        logger.debug("WCall", "fireRemoteMuteChanged: $isMuted")
        fireEvent(RemoteMediaEvent.MuteChanged(id, isMuted))
    }


    private fun fireRemoteHoldChanged(isOnHold: Boolean) {
        logger.debug("WCall", "fireRemoteHoldChanged: $isOnHold")
        fireEvent(RemoteMediaEvent.HoldChanged(id, isOnHold))
    }


    private fun fireRemoteVideoPausedChanged(isPaused: Boolean) {
        logger.debug("WCall", "fireRemoteVideoPausedChanged: $isPaused")
        fireEvent(RemoteMediaEvent.VideoPausedChanged(id, isPaused))
    }


    private fun fireEvent(event: CallEvent) {
        listeners.forEach { safeListenerCall { it.onEvent(event) } }
    }


    private inline fun safeListenerCall(
        action: () -> Unit
    ) {
        try {
            action()
        } catch (e: Throwable) {
            val stackTrace = e.stackTraceToString()
            logger.error("WCall",
                "safeListenerCall: Unhandled exception in client listener\n$stackTrace"
            )
        }
    }


    @Synchronized
    private fun checkThread() {
        try {
            if (pjCall == null) return
            val endpoint = Endpoint.instance()
            if (endpoint != null && !endpoint.libIsThreadRegistered()) {
                endpoint.libRegisterThread(
                    Thread.currentThread().name
                )
            }
        } catch (e: Exception) {
            logger.error("WCall", "checkThread:  ${e.message}")
        }
    }
}


class VideoSurfaceHandler {
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
        // Do NOT call setWindow(null) here — setting the ANativeWindow to null before
        // PJSIP's renderer thread (vid_conf) disconnects EGL causes "EGLNativeWindowType
        // disconnect failed", which can corrupt PJSIP's internal port list → SIGBUS.
        // PJSIP will cleanly disconnect EGL on its own while the Surface is still alive
        // (Fix 3 keeps s1/s2 referenced until after shutdownStack).
        try { handle?.delete() } catch (_: Exception) {}
        handle = null
    }


    fun attach(surface: Surface?) {
        if (!active || handle == null) return
        if (surface == null || !surface.isValid) return
        val vw = videoWindow ?: return
        try {
            Endpoint.instance()?.let { ep ->
                if (!ep.libIsThreadRegistered()) {
                    ep.libRegisterThread(Thread.currentThread().name)
                }
            }
            handle?.handle?.setWindow(surface)
            vw.setWindow(handle)
        } catch (e: Exception) {
            logger.error("VideoSurfaceHandler", "attach: ${e.stackTraceToString()}")
        }
    }
}