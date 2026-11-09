package com.webitel.voice.sdk.internal.support

import android.util.Log
import com.webitel.voice.sdk.LogLevel


/** Process-wide SDK logger; [level] is set from `VoiceClient.Builder.logLevel`. */
internal object SdkLogger {

    @Volatile
    var level: LogLevel = LogLevel.ERROR

    private const val PREFIX = "webitel:"


    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = log(LogLevel.ERROR, tag, message)


    private fun log(messageLevel: LogLevel, tag: String, message: String) {
        if (level == LogLevel.OFF || messageLevel < level) return
        try {
            when (messageLevel) {
                LogLevel.DEBUG -> Log.d(PREFIX + tag, message)
                LogLevel.INFO -> Log.i(PREFIX + tag, message)
                LogLevel.WARN -> Log.w(PREFIX + tag, message)
                LogLevel.ERROR -> Log.e(PREFIX + tag, message)
                LogLevel.OFF -> Unit
            }
        } catch (_: RuntimeException) {
            // android.util.Log is a stub in local JVM unit tests.
        }
    }
}
