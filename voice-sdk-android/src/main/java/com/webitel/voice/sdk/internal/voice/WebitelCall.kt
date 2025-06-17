package com.webitel.voice.sdk.internal.voice

import com.webitel.voice.sdk.Call
import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallListener
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.internal.sip.PJCall
import com.webitel.voice.sdk.internal.sip.SipCallCallbacks
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import com.webitel.voice.sdk.isSameAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import java.util.UUID
import kotlin.Exception
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.Throws


internal class WebitelCall(
    private val listener: CallStateListener
): Call, SipCallCallbacks {
    val id = UUID.randomUUID().toString()
    private var pjCall: PJCall? = null
    private val listeners = mutableSetOf<CallListener>()

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override var state: CallState = CallState.IDLE
        private set

    override val isMuted: Boolean
        get() {
            return pjCall?.isOnMute ?: false
        }

    override var isOnHold: Boolean = false
        private set

    override var answeredAt: Long = 0
        private set

    override var isOutgoing: Boolean = true
        private set

    private val isAnswered: Boolean
        get() { return answeredAt > 0 }


    override fun mute(mute: Boolean): Result<Unit> {
        val call = pjCall
        if (state != CallState.Ongoing) {
            val message = "Mute failed: call is not in an ongoing state. Current state: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (call == null) {
            val message = "Mute failed: pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (isMuted == mute) {
            return Result.success(Unit)
        }

        return try {
            logger.debug("WCall", "mute: $mute")
            call.setMute(mute)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "setMute error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun hold(hold: Boolean): Result<Unit> {
        val call = pjCall
        if (state != CallState.Ongoing) {
            val message = "Hold failed: call is not in an ongoing state. Current state: $state"
            logger.warn("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (call == null) {
            val message = "hold failed: pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (isOnHold == hold) {
            return Result.success(Unit)
        }

        return try {
            logger.debug("WCall", "hold: $hold")
            if (hold) {
                call.setHold()
            } else {
                call.setUnHold()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "setHold error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun sendDTMF(digits: String): Result<Unit> {
        val call = pjCall
        if (state != CallState.Ongoing) {
            val message = "DTMF send failed: call is not active. State: $state"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (call == null) {
            val message = "failed pjCall is null"
            logger.error("WCall", message)
            return Result.failure(IllegalStateException(message))
        }

        if (digits.isEmpty()) {
            return Result.failure(IllegalArgumentException("DTMF digits must not be empty"))
        }

        return try {
            logger.debug("WCall", "sendDTMF: $digits")
            call.sendDtmf(digits)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("WCall", "sendDTMF: error: ${e.message}")
            Result.failure(e)
        }
    }


    override fun disconnect(): Result<Unit> {
        return try {
            logger.debug("WCall", "on disconnect call")
            internalDisconnect(fromCode(0), cancelScope = true)
            Result.success(Unit)
        }catch (e: Exception) {
            logger.error("WCall", "disconnect: ${e.message}")
            Result.failure(e)
        }
    }


    override fun addListener(listener: CallListener) {
        listeners += listener
    }


    override fun removeListener(listener: CallListener) {
        listeners -= listener
    }


    override fun removeAllListeners() {
        listeners.clear()
    }


    override fun onHoldCallPJSIP(hold: Boolean) {
        onHold(hold)
        if (!hold) {
            listener.onActiveCall(id)
        }
    }


    override fun onCallStatePJSIP(state: Int, lastStatusCode: Int, lastReason: String) {
        when (state) {
            pjsip_inv_state.PJSIP_INV_STATE_NULL,
            pjsip_inv_state.PJSIP_INV_STATE_CONNECTING,
            pjsip_inv_state.PJSIP_INV_STATE_CALLING -> {
                onCallState(CallState.Connecting)
            }

            pjsip_inv_state.PJSIP_INV_STATE_EARLY -> {
                if (isOutgoing) onCallState(CallState.Ringing) // outgoing, remote is ringing
                else onCallState(CallState.Connecting)  // incoming, still negotiating
            }

            pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> {
                onCallState(CallState.Ringing)
            } // incoming ringing

            pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> {
                if (!isAnswered) {
                    answeredAt = System.currentTimeMillis()
                }
                onCallState(CallState.Ongoing)
            }

            pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> {
                listener.onEndCall(id)
                onCallState(CallState.Disconnected(fromCode(lastStatusCode, lastReason)))
            }
        }
    }


    fun disconnectWithReason(callEndReason: CallEndReason) {
        try {
            internalDisconnect(callEndReason, cancelScope = false)
        } catch (e: Exception) {
            logger.error("WCall", "disconnectWithReason: ${e.message}")
        }
    }


    fun setSipVoice(pjCall: PJCall) {
        this.pjCall = pjCall
    }


    fun launchInScope(block: suspend CoroutineScope.() -> Unit) {
        job = scope.launch {
            sendConnectingEvent()
            block()
        }
    }


    private fun cancelScope() {
        job?.cancel(CancellationException())
        job = null
    }


    @Throws(IllegalStateException::class)
    private fun internalDisconnect(reason: CallEndReason, cancelScope: Boolean) {
        if (state is CallState.Disconnected) {
            throw IllegalStateException("unavailable, call is DISCONNECTED.")
        }

        if (pjCall == null) {
            if (cancelScope) cancelScope()
            listener.onEndCall(id)
            onCallState(CallState.Disconnected(reason))
        } else {
            pjCall?.hangup(getHangupCode(true))
        }
    }


    private fun sendConnectingEvent() {
        onCallState(CallState.Connecting)
    }


    private fun getHangupCode(busyEverywhere: Boolean): Int {
        return when {
            isAnswered -> pjsip_status_code.PJSIP_SC_OK
            !isOutgoing -> {
                if (busyEverywhere) {
                    pjsip_status_code.PJSIP_SC_BUSY_EVERYWHERE
                }else {
                    pjsip_status_code.PJSIP_SC_BUSY_HERE
                }
            }
            else -> pjsip_status_code.PJSIP_SC_REQUEST_TERMINATED
        }
    }


    private val lock = Any()
    private fun onCallState(state: CallState) {
        synchronized(lock) {
            if (!this.state.isSameAs(state)) {
                val oldState = this.state
                this.state = state
                logger.debug(
                    "WCall",
                    "onCallState: from - ${oldState}, to - ${state}"
                )
                listeners.forEach {
                    safeListenerCall { it.onCallStateChanged(this, state) }
                }
            }
        }
    }


    private fun onHold(onHold: Boolean) {
        if (isOnHold != onHold) {
            isOnHold = onHold
            logger.debug(
                "WCall",
                "onHold: new - $onHold"
            )
            listeners.forEach {
                safeListenerCall { it.onHoldChanged(this, onHold) }
            }
        }
    }


    private inline fun safeListenerCall(
        action: () -> Unit
    ) {
        try {
            action()
        } catch (e: Throwable) {
            val stackTrace = e.stackTraceToString()
            logger.error("WCall",
                "safeListenerCall: Unhandled exception in client listener\n$stackTrace"
            )
        }
    }
}