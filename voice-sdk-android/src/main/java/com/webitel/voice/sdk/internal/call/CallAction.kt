package com.webitel.voice.sdk.internal.call


/** A user action on a call, validated synchronously and performed on the SIP thread. */
internal sealed class CallAction {
    object Answer : CallAction() {
        override fun toString() = "answer"
    }

    object End : CallAction() {
        override fun toString() = "end"
    }

    data class Mute(val muted: Boolean) : CallAction()

    data class Hold(val onHold: Boolean) : CallAction()

    data class Dtmf(val digits: String) : CallAction()
}
