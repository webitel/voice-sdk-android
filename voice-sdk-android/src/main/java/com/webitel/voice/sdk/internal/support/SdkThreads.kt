package com.webitel.voice.sdk.internal.support

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


/**
 * The SDK's two serial threads.
 *
 * - `sip` is the only thread that touches pjsua2 and the mutable state of `VoiceManager`,
 *   `SipManager` and `CallImpl`. PJSIP callbacks copy what they need and hop here with
 *   [postSip] — never synchronously, so they can't deadlock on pjsua locks.
 * - `events` delivers [com.webitel.voice.sdk.CallEvent]s to listeners in order, so a slow
 *   listener never blocks SIP processing.
 */
internal object SdkThreads {

    @Volatile
    private var sipThread: Thread? = null

    private val sip: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "webitel-sip").apply {
            priority = Thread.MAX_PRIORITY
            sipThread = this
        }
    }

    private val events = Executors.newSingleThreadExecutor { r ->
        Thread(r, "webitel-events")
    }

    /** Coroutine dispatcher running on the SIP thread. */
    val sipDispatcher: CoroutineDispatcher = sip.asCoroutineDispatcher()

    val isOnSip: Boolean
        get() = Thread.currentThread() === sipThread


    fun postSip(block: () -> Unit) {
        sip.execute(guarded("postSip", block))
    }


    fun postSipDelayed(delayMs: Long, block: () -> Unit): ScheduledFuture<*> {
        return sip.schedule(guarded("postSipDelayed", block), delayMs, TimeUnit.MILLISECONDS)
    }


    fun postEvent(block: () -> Unit) {
        events.execute(guarded("postEvent", block))
    }


    /** Runs [block] on the SIP thread and returns its result to the calling coroutine. */
    suspend fun <T> onSip(block: () -> T): T = withContext(sipDispatcher) { block() }


    fun assertOnSip() {
        check(isOnSip) { "Must be called on the webitel-sip thread, was ${Thread.currentThread().name}" }
    }


    /** An uncaught exception would kill the executor's thread and silently drop every later job. */
    private fun guarded(name: String, block: () -> Unit) = Runnable {
        try {
            block()
        } catch (e: Throwable) {
            SdkLogger.error("SdkThreads", "$name: unhandled exception\n${e.stackTraceToString()}")
        }
    }
}
