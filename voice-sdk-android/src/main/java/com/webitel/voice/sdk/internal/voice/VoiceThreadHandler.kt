package com.webitel.voice.sdk.internal.voice

import android.os.Handler
import android.os.HandlerThread
import android.os.Process


internal class VoiceThreadHandler {
    private val thread: HandlerThread = HandlerThread(
        "pjsip_webitel",
        Process.THREAD_PRIORITY_BACKGROUND
    )

    private val handler: Handler by lazy {
        Handler(thread.looper)
    }


    fun make(job: Runnable) {
        if (!thread.isAlive) {
            thread.priority = Thread.MAX_PRIORITY
            thread.start()
        }

        handler.post(job)
    }
}