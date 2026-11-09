package com.webitel.voice.sdk.internal.sip


/**
 * Events from the SIP stack. [PJCall] and [PJAccount] copy what they need inside the PJSIP
 * callback and invoke these on the SIP thread (`SdkThreads.postSip`).
 */
internal interface SipEventListener {

    /** `state` is a `pjsip_inv_state` value. */
    fun onCallState(sipId: Int, state: Int, lastStatusCode: Int, lastReason: String)

    fun onCallMediaState(sipId: Int)

    /** The remote video stream at [mediaIndex] changed its frame size (`PJMEDIA_EVENT_FMT_CHANGED`). */
    fun onRemoteVideoFormatChanged(sipId: Int, mediaIndex: Int, width: Int, height: Int)

    /** A SIP INFO request or response was received; [wholeMsg] is the raw message. */
    fun onIncomingInfo(sipId: Int, wholeMsg: String)

    /**
     * An INVITE arrived. [call] is already created (PJSIP rejects an incoming call that has
     * no `Call` object once the callback returns); [callUuid] is its `X-Webitel-Uuid` header.
     */
    fun onIncomingCall(call: PJCall, callUuid: String?)

    /** The final response to a REGISTER: 2xx on success. */
    fun onRegistrationState(code: Int, reason: String)
}
