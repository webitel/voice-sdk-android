package com.webitel.voice.sdk.internal.sip

import org.json.JSONObject


/**
 * Wire format for the local mute / local-video-pause / hold state exchanged over SIP INFO —
 * byte-compatible with the iOS SDK: `Content-Type: application/json`, body
 * `{"audioMuted":Bool,"videoMuted":Bool,"hold":Bool}`. On receive each field is optional:
 * a field absent from the JSON means "unchanged", not "false".
 */
internal object MediaStateInfo {
    const val CONTENT_TYPE = "application/json"

    private const val AUDIO_MUTED = "audioMuted"
    private const val VIDEO_MUTED = "videoMuted"
    private const val HOLD = "hold"


    data class Payload(val audioMuted: Boolean?, val videoMuted: Boolean?, val hold: Boolean?)


    fun encode(audioMuted: Boolean, videoMuted: Boolean, hold: Boolean): String {
        return JSONObject()
            .put(VIDEO_MUTED, videoMuted)
            .put(AUDIO_MUTED, audioMuted)
            .put(HOLD, hold)
            .toString()
    }


    /**
     * Parses a raw SIP message. PJSIP reports both a genuine incoming INFO request and the
     * 200 OK to our own outgoing INFO the same way, so a response (first line starting with
     * "SIP/2.0") is skipped. Returns `null` unless this is an INFO request with a JSON body.
     */
    fun parse(wholeMsg: String): Payload? {
        val firstLineEnd = wholeMsg.indexOf('\n')
        if (firstLineEnd < 0) return null
        if (wholeMsg.substring(0, firstLineEnd).trim().startsWith("SIP/2.0")) return null
        if (!wholeMsg.contains(CONTENT_TYPE, ignoreCase = true)) return null

        val crlf = wholeMsg.indexOf("\r\n\r\n")
        val bodyStart = if (crlf >= 0) crlf + 4 else wholeMsg.indexOf("\n\n").let { if (it >= 0) it + 2 else -1 }
        if (bodyStart <= 0 || bodyStart >= wholeMsg.length) return null
        val body = wholeMsg.substring(bodyStart).trim()
        if (body.isEmpty()) return null

        return try {
            val json = JSONObject(body)
            Payload(
                audioMuted = if (json.has(AUDIO_MUTED)) json.getBoolean(AUDIO_MUTED) else null,
                videoMuted = if (json.has(VIDEO_MUTED)) json.getBoolean(VIDEO_MUTED) else null,
                hold = if (json.has(HOLD)) json.getBoolean(HOLD) else null
            )
        } catch (e: Exception) {
            null
        }
    }
}
