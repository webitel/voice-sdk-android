package com.webitel.voice.sdk.internal.sip

import org.pjsip.pjsua2.CallMediaInfo


internal interface SipCallCallbacks {
    fun onCallStatePJSIP(state: Int, lastStatusCode: Int, lastReason: String)
    fun onHoldCallPJSIP(hold: Boolean)

    fun onShowRemoteVideo(cmi: CallMediaInfo)
    fun onShowLocalVideo(cmi: CallMediaInfo)
    fun onRemoteVideoFormatChanged()

    /** Fired when local video encoding stops (media becomes inactive or direction loses encoding). */
    fun onLocalVideoStopped()

    /** Fired when remote video decoding stops (media becomes inactive or direction loses decoding). */
    fun onRemoteVideoStopped()
}