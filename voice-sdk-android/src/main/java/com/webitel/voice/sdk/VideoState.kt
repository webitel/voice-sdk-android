package com.webitel.voice.sdk


/**
 * Describes the current video activity state of a call.
 *
 * Updated via [CallListener.onVideoStateChanged] whenever local or remote
 * video streams start or stop. Also carried in [CallEvent.VideoStateChanged].
 */
enum class VideoState {

    /** No video streams are active in either direction. */
    INACTIVE,

    /** Only the local camera is transmitting; no remote video is being received. */
    LOCAL_ONLY,

    /** Remote video is being received; local camera is not transmitting. */
    REMOTE_ONLY,

    /** Both local and remote video streams are active. */
    ACTIVE
}
