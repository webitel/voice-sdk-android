package com.webitel.voice.sdk.internal.sip

import org.pjsip.pjsua2.CallMediaInfo


internal interface SipCallCallbacks {
    fun onCallStatePJSIP(state: Int, lastStatusCode: Int, lastReason: String)
    fun onHoldCallPJSIP(hold: Boolean)

    fun onShowRemoteVideo(cmi: CallMediaInfo)
    fun onShowLocalVideo(cmi: CallMediaInfo)
    fun onRemoteVideoFormatChanged(width: Int, height: Int)

    /** Fired when local video encoding stops (media becomes inactive or direction loses encoding). */
    fun onLocalVideoStopped()

    /** Fired when remote video decoding stops (media becomes inactive or direction loses decoding). */
    fun onRemoteVideoStopped()

    /**
     * Fired when the remote party's media state is reported via a SIP INFO packet.
     * Each parameter is null if that field was absent from the payload.
     */
    fun onRemoteMediaStateInfo(audioMuted: Boolean?, videoMuted: Boolean?, hold: Boolean?)
}