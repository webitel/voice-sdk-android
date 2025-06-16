package com.webitel.voice.sdk.internal.voice

import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.internal.sip.SipConfig
import com.webitel.voice.sdk.internal.sip.SipManager
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger


internal class VoiceManager : CallStateListener {
    private val activeCalls = mutableMapOf<String, WebitelCall>()
    private val handler = VoiceThreadHandler()

    var activeCall: WebitelCall? = null

    companion object {
        private val sipManager = SipManager()
    }


    override fun onActiveCall(callId: String) {
        activeCalls[callId]?.let {
            activeCall = it
        }
        holdOtherCalls(callId)
    }


    override fun onEndCall(callId: String) {
        activeCalls.remove(callId)
        if (activeCall?.id == callId) {
            activeCall = activeCalls.entries.firstOrNull()?.value
        }

        if (activeCalls.isEmpty()) {
            make {
                sipManager.shutdownStack()
            }
        }
    }


    fun makeAudioCall(voice: WebitelCall, sip: SipConfig) {
        try {
            val pj = sipManager.makeAudioCall(sip, voice)
            voice.setSipVoice(pj)

            activeCall = voice
            activeCalls[voice.id] = voice
        } catch (e: Exception) {
            val reason = parseErrorMessage(e)
            logger.error("VoiceManager", "Parsed error: $reason")
            voice.disconnectWithReason(reason)
        }
    }


    fun shutdown(onComplete: () -> Unit) {
        make {
            sipManager.shutdownStack()
            onComplete()
        }
    }


    private fun parseErrorMessage(e: Exception): CallEndReason {
        val msg = e.message ?: return fromCode(-1)
        val codeRegex = Regex("""Code:\s+(\d+)""")
        val code = codeRegex.find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        return fromCode(code, msg)
    }


    private fun make(job: Runnable) {
        handler.make(job)
    }


    private fun holdOtherCalls(exceptId: String) {
        activeCalls.forEach {
            if (it.key != exceptId) {
                it.value.hold(true)
            }
        }
    }
}