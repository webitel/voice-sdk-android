package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.CallConfig
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import java.lang.Exception


internal class PJAccount(
    val transportUse: CallConfig.TransportUse,
    val config: SipConfig
): Account() {

    private var isRegistered: Boolean = false

    override fun onRegState(prm: OnRegStateParam?) {
        super.onRegState(prm)
        prm?.let {
            val code = it.code
            val expiration = it.expiration.toInt()
            try {
                val returnCode: Int = code
                if (returnCode / 100 == 2 && expiration != 0) {
                    if (!isRegistered) {
                        isRegistered = true
                    }
                } else {
                    isRegistered = false

                }
            } catch (e: Exception) {
                isRegistered = false
            }
        }
    }


    override fun onIncomingCall(prm: OnIncomingCallParam) {
        var wid = ""
        val msg  = prm.rdata.wholeMsg
        val index = msg.indexOf("X-Webitel-Uuid")
        if (index != -1) {
            val m = msg.substring(index)
            val s = m.split("\r\n").first().toString()
            wid = s.substring(s.indexOf(':')).trim(':', ' ')
        }

        logger.debug("PJAccount", "======== Incoming call ======== \n" +
                "id - ${prm.callId}; wid - $wid")
    }
}