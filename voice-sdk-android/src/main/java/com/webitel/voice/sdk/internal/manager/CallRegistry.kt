package com.webitel.voice.sdk.internal.manager

import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.internal.call.CallImpl
import com.webitel.voice.sdk.internal.support.Protected


/** Thread-safe index of live calls by id and SIP id, plus the active call. */
internal class CallRegistry {

    class State {
        val callsById = LinkedHashMap<String, CallImpl>()
        val callsBySipId = HashMap<Int, String>()
        var activeCall: CallImpl? = null
    }

    enum class InsertOutcome {
        INSERTED,
        EXISTING,
        /** The call was created but not registered; the caller must end it right away. */
        REJECTED
    }

    private val state = Protected(State())

    val activeCall: CallImpl?
        get() = state.read { it.activeCall }

    val calls: List<CallImpl>
        get() = state.read { it.callsById.values.toList() }

    val isEmpty: Boolean
        get() = state.read { it.callsById.isEmpty() }


    /**
     * Atomically returns the call [existing] picks from the registry, or creates one with
     * [make] and registers it — so two concurrent `makeCall`s can't both create a call.
     * If [reject] says the registry can't take another call, the call is created but
     * not registered.
     */
    fun insert(
        existing: (State) -> CallImpl?,
        reject: (State) -> Boolean,
        make: () -> CallImpl
    ): Pair<CallImpl, InsertOutcome> = state.write { registry ->
        existing(registry)?.let { return@write it to InsertOutcome.EXISTING }
        val call = make()
        if (reject(registry)) return@write call to InsertOutcome.REJECTED
        registry.callsById[call.id] = call
        if (registry.activeCall == null) registry.activeCall = call
        call to InsertOutcome.INSERTED
    }


    fun remove(callId: String) {
        state.write { registry ->
            val call = registry.callsById.remove(callId) ?: return@write
            registry.callsBySipId.remove(call.sipId)
            if (registry.activeCall?.id == callId) {
                // The remaining call stays on hold until the app resumes it.
                val remaining = registry.callsById.values
                registry.activeCall = remaining.firstOrNull { it.state == CallState.Ongoing } ?: remaining.firstOrNull()
            }
        }
    }


    fun setActive(call: CallImpl) {
        state.write { if (it.activeCall?.id != call.id) it.activeCall = call }
    }


    fun find(callId: String): CallImpl? = state.read { it.callsById[callId] }


    fun findBySipId(sipId: Int): CallImpl? = state.read { registry ->
        registry.callsBySipId[sipId]?.let { registry.callsById[it] }
    }


    fun assignSipId(sipId: Int, callId: String) {
        state.write { registry ->
            val call = registry.callsById[callId] ?: return@write
            call.sipId = sipId
            registry.callsBySipId[sipId] = callId
        }
    }


    /** Ongoing calls other than [exceptId] that are neither held nor being held. */
    fun holdableCalls(exceptId: String): List<CallImpl> = state.read { registry ->
        registry.callsById.values.filter {
            it.id != exceptId && it.state == CallState.Ongoing && !it.isOnHold && !it.isHoldInProgress
        }
    }


    companion object {
        /**
         * Call waiting: one active call plus one more (held or ringing). Only an incoming
         * call can be the second one.
         */
        const val MAX_CALLS = 2
    }
}
