package com.webitel.voice.sdk.internal.sip


internal interface SipCallCallbacks {
    fun onCallStatePJSIP(state: Int, lastStatusCode: Int, lastReason: String)
    fun onHoldCallPJSIP(hold: Boolean)
    //fun onMutedPJSIP(muted: Boolean)

//    fun onVideoCallStarted(callId: Int, webitelId: String)
//    fun showRemoteVideo(callId: Int, webitelId: String)
}