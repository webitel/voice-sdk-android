package com.webitel.voice.sdk.internal.voice


internal interface CallStateListener {
    fun onActiveCall(callId: String)
    fun onEndCall(callId: String)
}