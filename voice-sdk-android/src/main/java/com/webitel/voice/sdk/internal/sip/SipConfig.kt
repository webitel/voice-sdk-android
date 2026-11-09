package com.webitel.voice.sdk.internal.sip


/** SIP account credentials obtained from the server for one session. */
internal data class SipConfig(
    val auth: String,
    val domain: String,
    val extension: String,
    val password: String,
    val proxy: String
) {

    /** The proxy as a SIP URI, e.g. `sip:host:port;transport=tcp`. */
    fun proxyUri(): String {
        return if (proxy.startsWith("sip:")) proxy else "sip:$proxy"
    }


    fun getIdUri(): String = "sip:$extension@$domain"


    fun getSipUri(number: String, name: String): String {
        val host = proxy.removePrefix("sip:")
        return "\"$name\"<sip:$number@$host>"
    }


    /** Never prints the password. */
    override fun toString(): String {
        return "SipConfig(auth=$auth, domain=$domain, extension=$extension, proxy=$proxy)"
    }
}
