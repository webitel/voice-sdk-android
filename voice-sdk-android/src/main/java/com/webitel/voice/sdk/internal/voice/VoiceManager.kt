package com.webitel.voice.sdk.internal.voice

import com.webitel.voice.sdk.CallEndReason
import com.webitel.voice.sdk.CallEndReasonCode.Companion.fromCode
import com.webitel.voice.sdk.CallOptions
import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.VideoOrientation
import com.webitel.voice.sdk.VideoQuality
import com.webitel.voice.sdk.internal.sip.SipConfig
import com.webitel.voice.sdk.internal.sip.SipManager
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import java.util.concurrent.ConcurrentHashMap


internal object VoiceManager : CallStateListener {
    private val activeCalls = ConcurrentHashMap<String, WebitelCall>()
    private val handler = VoiceThreadHandler()

    @Volatile
    var activeCall: WebitelCall? = null

    private val sipManager = SipManager()


    override fun onActiveCall(callId: String) {
        activeCalls[callId]?.let {
            activeCall = it
        }
        holdOtherCalls(callId)
    }


    override fun onScheduleVoiceThread(block: Runnable) {
        make(block)
    }


    override fun onEndCall(callId: String) {
        activeCalls.remove(callId)
        if (activeCall?.id == callId) {
            activeCall = activeCalls.entries.firstOrNull()?.value
        }

        if (activeCalls.isEmpty()) {
            // Queued on the same VoiceThread FIFO as WebitelCall's deferred video/surface
            // cleanup job, so shutdownStack() always runs before that job without needing
            // an explicit delay here.
            make {
                sipManager.shutdownStack()
            }
        }
    }


    fun configure(callSettings: CallSettings) {
        sipManager.configure(callSettings)
    }


    fun updateVideoOrientation(quality: VideoQuality, orientation: VideoOrientation, isBackCamera: Boolean) {
        make { sipManager.updateVideoOrientation(quality, orientation, isBackCamera) }
    }


    fun makeCall(callOptions: CallOptions, call: WebitelCall, sip: SipConfig) {
        try {
            val pj = sipManager.makeCall(callOptions, sip, call)
            call.setSipVoice(pj)

            activeCall = call
            activeCalls[call.id] = call
        } catch (e: Exception) {
            val reason = parseErrorMessage(e)
            logger.error("VoiceManager", "error: $reason")
            call.disconnectWithReason(reason)
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