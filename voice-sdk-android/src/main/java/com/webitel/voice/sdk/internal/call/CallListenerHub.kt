package com.webitel.voice.sdk.internal.call

import com.webitel.voice.sdk.CallEvent
import com.webitel.voice.sdk.CallEventListener
import com.webitel.voice.sdk.internal.support.SdkLogger
import com.webitel.voice.sdk.internal.support.SdkThreads
import java.util.concurrent.CopyOnWriteArraySet


/** Delivers [CallEvent]s in order on the SDK events thread. */
internal class CallListenerHub {
    private val listeners = CopyOnWriteArraySet<CallEventListener>()

    fun add(listener: CallEventListener) {
        listeners += listener
    }

    fun remove(listener: CallEventListener) {
        listeners -= listener
    }

    fun notify(event: CallEvent) {
        SdkThreads.postEvent {
            for (listener in listeners) {
                try {
                    listener.onEvent(event)
                } catch (e: Throwable) {
                    SdkLogger.error("CallListenerHub", "Unhandled exception in a listener\n${e.stackTraceToString()}")
                }
            }
        }
    }
}
