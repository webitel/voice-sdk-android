package com.webitel.voice.sdk.internal.call

import com.webitel.voice.sdk.ConnectionQuality
import com.webitel.voice.sdk.internal.sip.SipStreamStat
import com.webitel.voice.sdk.internal.support.SdkThreads
import java.util.concurrent.ScheduledFuture


/**
 * Turns successive cumulative RTCP counters into a [ConnectionQuality]. Pure logic, no
 * threading: feed it samples with [onSample].
 *
 * Loss is the worse of two directions:
 *  - incoming — computed per sample from the gaps in RTP sequence numbers PJSIP sees;
 *  - outgoing — known only from the media server's RTCP receiver reports (~1 per 5 s), so
 *    it is computed between two reports, and the last value is kept until the next one.
 *    Computing it per sample instead would overstate it ~2.5× when a report arrives and
 *    read 0 in between.
 *
 * A new value is published only after [confirmSamples] consecutive samples agree, so a
 * single bad interval doesn't make the quality flicker.
 */
internal class ConnectionQualityEvaluator(private val confirmSamples: Int = 2) {

    var current: ConnectionQuality = ConnectionQuality.UNKNOWN
        private set

    private var previous: SipStreamStat? = null

    /** Counters at the last receiver report, the start of the next outgoing-loss window. */
    private var lastReport: SipStreamStat? = null
    private var txLossPercent = 0.0

    private var candidate: ConnectionQuality? = null
    private var candidateCount = 0


    /** Forgets the baseline, e.g. after a hold; the next sample only becomes the new baseline. */
    fun reset() {
        previous = null
        lastReport = null
        txLossPercent = 0.0
        candidate = null
        candidateCount = 0
    }


    /** Forgets everything, including [current]. */
    fun clear() {
        reset()
        current = ConnectionQuality.UNKNOWN
    }


    /**
     * @param expectsIncomingMedia false when the remote party may legitimately send nothing
     *        (e.g. it muted itself), so silence isn't treated as a dead connection.
     * @return the new quality if it changed, or `null`.
     */
    fun onSample(stat: SipStreamStat, expectsIncomingMedia: Boolean = true): ConnectionQuality? {
        val prev = previous
        previous = stat
        if (prev == null) {
            lastReport = stat
            return null
        }

        // Counters went backwards: the stream was recreated (re-INVITE). Use as a new baseline.
        if (stat.rxPkt < prev.rxPkt || stat.rxLoss < prev.rxLoss ||
            stat.txPkt < prev.txPkt || stat.txLoss < prev.txLoss || stat.txReports < prev.txReports
        ) {
            reset()
            previous = stat
            lastReport = stat
            return null
        }

        val rxPkt = stat.rxPkt - prev.rxPkt
        val rxLoss = stat.rxLoss - prev.rxLoss
        updateTxLoss(stat)

        val lossPercent = maxOf(percent(rxLoss, rxPkt + rxLoss), txLossPercent)
        val noIncomingMedia = expectsIncomingMedia && rxPkt == 0L
        val sample = classify(lossPercent, stat.rttMs, noIncomingMedia)

        if (sample == current) {
            candidate = null
            candidateCount = 0
            return null
        }
        if (sample == candidate) candidateCount++ else {
            candidate = sample
            candidateCount = 1
        }
        if (candidateCount < confirmSamples) return null

        current = sample
        candidate = null
        candidateCount = 0
        return sample
    }


    /** Recomputes the outgoing loss when a new receiver report arrived since [lastReport]. */
    private fun updateTxLoss(stat: SipStreamStat) {
        val report = lastReport
        if (report == null) {
            lastReport = stat
            return
        }
        if (stat.txReports == report.txReports) return
        txLossPercent = percent(stat.txLoss - report.txLoss, stat.txPkt - report.txPkt)
        lastReport = stat
    }


    companion object {
        const val GOOD_MAX_LOSS_PERCENT = 3.0
        const val GOOD_MAX_RTT_MS = 300
        const val BAD_MIN_LOSS_PERCENT = 10.0
        const val BAD_MIN_RTT_MS = 700

        fun classify(lossPercent: Double, rttMs: Int, noIncomingMedia: Boolean): ConnectionQuality = when {
            noIncomingMedia || lossPercent > BAD_MIN_LOSS_PERCENT || rttMs > BAD_MIN_RTT_MS -> ConnectionQuality.BAD
            lossPercent < GOOD_MAX_LOSS_PERCENT && rttMs < GOOD_MAX_RTT_MS -> ConnectionQuality.GOOD
            else -> ConnectionQuality.MEDIUM
        }

        private fun percent(part: Long, total: Long): Double =
            if (total <= 0) 0.0 else (part * 100.0 / total).coerceAtMost(100.0)
    }
}


/**
 * Periodically samples a call's audio RTCP statistics on the SIP thread and reports
 * [ConnectionQuality] changes. SIP thread only.
 *
 * The audio stream is used because every call has one and loss/RTT reflect the same
 * network path as video.
 */
internal class ConnectionQualityMonitor(
    private val host: Host,
    private val intervalMs: Long = INTERVAL_MS
) {

    interface Host {
        /** Current counters, or `null` if the call has no active audio stream. */
        fun readStat(): SipStreamStat?

        /** True once the call ended; the monitor then stops by itself. */
        val isEnded: Boolean

        /** True while sampling makes no sense, e.g. on hold. */
        val isPaused: Boolean

        /** False when the remote party may send no media (e.g. it muted itself). */
        val expectsIncomingMedia: Boolean

        fun onQualityChanged(quality: ConnectionQuality)
    }

    private val evaluator = ConnectionQualityEvaluator()
    private var task: ScheduledFuture<*>? = null

    val current: ConnectionQuality get() = evaluator.current


    fun start() {
        if (task != null) return
        evaluator.reset()
        schedule()
    }


    /** Stops sampling and resets the quality to [ConnectionQuality.UNKNOWN] without reporting it. */
    fun stop() {
        task?.cancel(false)
        task = null
        evaluator.clear()
    }


    private fun schedule() {
        task = SdkThreads.postSipDelayed(intervalMs) {
            if (task == null) return@postSipDelayed
            if (host.isEnded) {
                stop()
                return@postSipDelayed
            }
            sample()
            if (task != null) schedule()
        }
    }


    private fun sample() {
        if (host.isPaused) {
            evaluator.reset()
            publish(ConnectionQuality.UNKNOWN)
            return
        }
        val stat = host.readStat() ?: return
        evaluator.onSample(stat, host.expectsIncomingMedia)?.let { host.onQualityChanged(it) }
    }


    private fun publish(quality: ConnectionQuality) {
        if (evaluator.current == quality) return
        evaluator.clear()
        host.onQualityChanged(quality)
    }


    companion object {
        const val INTERVAL_MS = 2_000L
    }
}
