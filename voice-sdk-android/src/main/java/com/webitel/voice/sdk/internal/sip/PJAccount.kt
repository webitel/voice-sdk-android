package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.pj_constants_
import org.pjsip.pjsua2.pjsip_status_code


internal class PJAccount(
    val config: SipConfig,
    private val listener: SipEventListener
): Account() {

    override fun onRegState(prm: OnRegStateParam?) {
        super.onRegState(prm)
        prm ?: return
        val isSuccess = prm.code / 100 == 2
        // The final unREGISTER (expiration 0) is irrelevant unless it failed.
        if (isSuccess && prm.expiration == 0L) return

        // No final response at all (e.g. a transport error) is reported as a timeout.
        val code = if (prm.code == 0 && prm.status != pj_constants_.PJ_SUCCESS) {
            pjsip_status_code.PJSIP_SC_REQUEST_TIMEOUT
        } else {
            prm.code
        }
        val reason = prm.reason.orEmpty()
        SdkThreads.postSip { listener.onRegistrationState(code, reason) }
    }


    override fun onIncomingCall(prm: OnIncomingCallParam) {
        val sipId = prm.callId
        val callUuid = try {
            SipHeaders.webitelUuid(prm.rdata.wholeMsg)
        } catch (e: Exception) {
            SdkLogger.error("PJAccount", "onIncomingCall: failed to read INVITE: ${e.message}")
            null
        }
        SdkLogger.debug("PJAccount", "onIncomingCall: sipId - $sipId; X-Webitel-Uuid - $callUuid")

        // Must be created before this callback returns, or PJSIP rejects the call with 500.
        val call = PJCall(this, sipId, listener)
        SdkThreads.postSip { listener.onIncomingCall(call, callUuid) }
    }
}
