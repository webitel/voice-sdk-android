package com.webitel.voice.sdk


/**
 * Advanced network/transport configuration for calls: signaling transport, NAT traversal,
 * and media encryption. Pass a configured instance to [VoiceClient.Builder.callSettings].
 *
 * The defaults are suitable for most deployments; only change these if your network
 * environment (NAT, firewall, media encryption policy) requires it.
 */
class CallSettings {
    constructor()

    /**
     * Constructs settings from raw ordinal values, primarily for cross-platform bridging
     * (e.g. Flutter platform channels) where enums travel as integers.
     *
     * @param transport ordinal of [TransportUse].
     * @param natMediaStunUse ordinal of [MediaStunUse] for media (RTP) traffic.
     * @param natSipStunUse ordinal of [MediaStunUse] for call signaling traffic.
     * @param natIceEnabled whether ICE NAT traversal is enabled.
     * @param natSdpNatRewriteUse whether to rewrite NAT'd addresses in the session description.
     * @param natContactRewriteUse whether to rewrite the contact address behind NAT.
     * @param natViaRewriteUse whether to rewrite the via address behind NAT.
     * @param busyEverywhereUse whether to report "busy everywhere" when declining calls.
     * @param srtpSecureSignaling required signaling security level for SRTP.
     * @param srtpUse ordinal of [SrtpUse].
     */
    constructor (
        transport: Int,
        natMediaStunUse: Int,
        natSipStunUse: Int,
        natIceEnabled: Boolean,
        natSdpNatRewriteUse: Boolean,
        natContactRewriteUse: Boolean,
        natViaRewriteUse: Boolean,
        busyEverywhereUse: Boolean,
        srtpSecureSignaling: Int,
        srtpUse: Int
    ) {
        this.transport = TransportUse.values()[transport]
        this.natMediaStunUse = MediaStunUse.values()[natMediaStunUse]
        this.natSipStunUse = MediaStunUse.values()[natSipStunUse]
        this.natIceEnabled = natIceEnabled
        this.natSdpNatRewriteUse = natSdpNatRewriteUse
        this.natContactRewriteUse = natContactRewriteUse
        this.natViaRewriteUse = natViaRewriteUse
        this.srtpSecureSignaling = srtpSecureSignaling
        this.srtpUse = SrtpUse.values()[srtpUse]
        this.busyEverywhereUse = busyEverywhereUse
    }

    /** The network transport used for call signaling. */
    var transport: TransportUse = TransportUse.TCP_UDP

    /** STUN usage policy for media (RTP) traffic behind NAT. */
    var natMediaStunUse: MediaStunUse = MediaStunUse.DEFAULT

    /** STUN usage policy for call signaling traffic behind NAT. */
    var natSipStunUse: MediaStunUse = MediaStunUse.DEFAULT

    /** Whether ICE is used for NAT traversal. */
    var natIceEnabled: Boolean = false

    /** Whether to rewrite NAT'd addresses found in the session description. */
    var natSdpNatRewriteUse: Boolean = false

    /** Whether to rewrite the contact address when behind NAT. */
    var natContactRewriteUse: Boolean = true

    /** Whether to rewrite the via address when behind NAT. */
    var natViaRewriteUse: Boolean = true

    /** Required security level of the signaling channel for [srtpUse] to apply. */
    var srtpSecureSignaling: Int = 0

    /** SRTP (media encryption) usage policy. */
    var srtpUse: SrtpUse = SrtpUse.DISABLED

    /** Whether to report "busy everywhere" instead of "busy here" when declining a call. */
    var busyEverywhereUse: Boolean = false


    override fun toString(): String {
        return "transport: ${transport}; natMediaStunUse: $natMediaStunUse; natSipStunUse: $natSipStunUse; " +
                "natIceEnabled: $natIceEnabled; natSdpNatRewriteUse: $natSdpNatRewriteUse; natContactRewriteUse: $natContactRewriteUse" +
                "; natViaRewriteUse: $natViaRewriteUse; srtpSecureSignaling: $srtpSecureSignaling; srtpUse: $srtpUse"
    }


    /** Media encryption (SRTP) usage policy. */
    enum class SrtpUse(val value: Int) {
        DISABLED(0),
        OPTIONAL(1),
        MANDATORY(2)
    }


    /** Network transport protocol used for call signaling. */
    enum class TransportUse(val value: Int) {
        UDP(0),
        TCP(1),
        TCP_UDP(2),
        TLS(3)
    }


    /** STUN usage policy for NAT traversal. */
    enum class MediaStunUse(val value: Int) {
        DEFAULT(0),
        DISABLED(1),
        RETRY_ON_FAILURE(2)
    }
}