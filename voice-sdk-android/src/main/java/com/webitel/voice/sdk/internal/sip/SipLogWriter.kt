package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient
import org.pjsip.pjsua2.LogEntry
import org.pjsip.pjsua2.LogWriter


internal class SipLogWriter : LogWriter() {
    override fun write(entry: LogEntry?) {
        WebitelVoiceClient.logger.debug("sip", javaClass.simpleName + " " + (entry?.msg ?: "empty"))
    }
}