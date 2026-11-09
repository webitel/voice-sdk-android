package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.internal.support.SdkLogger
import org.pjsip.pjsua2.LogEntry
import org.pjsip.pjsua2.LogWriter


internal class SipLogWriter : LogWriter() {
    override fun write(entry: LogEntry?) {
        SdkLogger.debug("sip", entry?.msg ?: "empty")
    }
}
