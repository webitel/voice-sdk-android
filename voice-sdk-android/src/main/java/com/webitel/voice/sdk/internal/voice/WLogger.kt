package com.webitel.voice.sdk.internal.voice

import android.util.Log
import com.webitel.voice.sdk.LogLevel
import org.pjsip.pjsua2.LogEntry
import org.pjsip.pjsua2.LogWriter


internal class WLogger: LogWriter() {
    var level: LogLevel = LogLevel.ERROR
    private val pref = "webitel:"
    fun info(tag: String, message: String) {
        if (level <= LogLevel.INFO) {
            Log.i(pref+tag, message)
        }
    }

    fun debug(tag: String, message: String) {
        if (level <= LogLevel.DEBUG) {
            Log.d(pref+tag, message)
        }
    }

    fun warn(tag: String, message: String) {
        if (level <= LogLevel.WARN) {
            Log.w(pref+tag, message)
        }
    }

    fun error(tag: String, message: String) {
        if (level <= LogLevel.ERROR) {
            Log.e(pref+tag, message)
        }
    }

    override fun write(entry: LogEntry?) {
        debug("sip", javaClass.simpleName + " " + (entry?.msg ?: "empty"))
    }
}