package com.webitel.voice.sdk.internal.sip


/** Reads headers from a raw SIP message (`SipRxData.wholeMsg`). */
internal object SipHeaders {
    private const val WEBITEL_UUID = "X-Webitel-Uuid"


    /**
     * The `X-Webitel-Uuid` header of an incoming INVITE: the server-side call id that also
     * arrives in the incoming call push.
     */
    fun webitelUuid(wholeMsg: String): String? = value(wholeMsg, WEBITEL_UUID)


    /** The value of the first header named [name] (case-insensitive), if present. */
    fun value(wholeMsg: String, name: String): String? {
        for (line in wholeMsg.lineSequence().drop(1)) { // skip the request/status line
            if (line.isBlank()) break // end of headers
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            if (line.substring(0, colon).trim().equals(name, ignoreCase = true)) {
                return line.substring(colon + 1).trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
