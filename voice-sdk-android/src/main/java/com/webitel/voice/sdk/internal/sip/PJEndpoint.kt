package com.webitel.voice.sdk.internal.sip

import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.OnIpChangeProgressParam
import org.pjsip.pjsua2.OnTransportStateParam


internal class PJEndpoint: Endpoint() {

    override fun onIpChangeProgress(prm: OnIpChangeProgressParam?) {
        super.onIpChangeProgress(prm)
    }

    override fun onTransportState(prm: OnTransportStateParam?) {
        super.onTransportState(prm)
    }
}