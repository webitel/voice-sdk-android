package com.webitel.voice.sdk.internal.call

import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.internal.sip.PJCall
import com.webitel.voice.sdk.internal.sip.SipMediaInfo
import com.webitel.voice.sdk.internal.sip.SipStreamStat


/**
 * The call engine as seen by [CallImpl]. Implemented by `VoiceManager`. Unless noted
 * otherwise, methods must be called on the SIP thread and throw `CallError` on failure.
 */
internal interface CallControlDelegate {
    fun disconnectCall(target: DisconnectTarget)

    /** @throws IllegalStateException if the call has no active audio media yet. */
    fun muteCall(sipId: Int, muted: Boolean)

    fun holdCall(sipId: Int, onHold: Boolean, withVideo: Boolean)

    fun sendDtmf(sipId: Int, digits: String)

    fun answerCall(sipId: Int)

    /** Any thread. */
    val isSpeakerActive: Boolean

    /** Any thread. */
    fun setSpeakerphoneOn(enabled: Boolean): Result<Unit>

    fun setVideoEnabled(sipId: Int, enabled: Boolean)

    /** @throws IllegalStateException if there is no active local video stream. */
    fun setLocalVideoTransmitting(sipId: Int, transmit: Boolean)

    fun updateVideoOrientation(orientation: VideoOrientation)

    fun sendMediaStateInfo(sipId: Int, audioMuted: Boolean, videoMuted: Boolean, hold: Boolean)

    fun sipCall(sipId: Int): PJCall?

    fun mediaInfos(sipId: Int): List<SipMediaInfo>?

    /** RTCP counters of the call's audio stream, or `null` if unavailable. */
    fun audioStreamStat(sipId: Int): SipStreamStat?
}


internal sealed class DisconnectTarget {
    /** The call has a SIP dialog: hang it up. */
    data class Sip(val sipId: Int) : DisconnectTarget()

    /** The call never reached SIP (still fetching credentials or waiting for the INVITE). */
    data class Local(val callId: String) : DisconnectTarget()
}


/** SIP status codes used to end a call. */
internal object CancelCallCode {
    const val NORMAL_CLEARING = 200
    const val USER_BUSY_HERE = 486
    const val ORIGINATOR_CANCEL = 487
    const val USER_BUSY_EVERYWHERE = 600
    const val DECLINE = 603

    /** The code to hang up a call with, depending on whether and how it was answered. */
    fun forHangup(isAnswered: Boolean, isOutgoing: Boolean, busyEverywhere: Boolean): Int = when {
        isAnswered -> NORMAL_CLEARING
        isOutgoing -> ORIGINATOR_CANCEL
        busyEverywhere -> USER_BUSY_EVERYWHERE
        else -> USER_BUSY_HERE
    }
}
