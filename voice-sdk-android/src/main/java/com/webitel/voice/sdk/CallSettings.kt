package com.webitel.voice.sdk


/**
 * Client-wide configuration for calls: signaling transport, NAT traversal, media encryption
 * and [videoQuality]. Pass a configured instance to [VoiceClient.Builder.callSettings], or
 * replace it later with [VoiceClient.configure].
 *
 * The defaults are suitable for most deployments; only change these if your network
 * environment (NAT, firewall, media encryption policy) requires it.
 */
class CallSettings {
    constructor()

    /**
     * Constructs settings from raw enum `value`s, primarily for cross-platform bridging
     * (e.g. Flutter platform channels) where enums travel as integers.
     *
     * @throws IllegalArgumentException if an enum value is unknown.
     *
     * @param transport [TransportUse.value].
     * @param natMediaStunUse [MediaStunUse.value] for media (RTP) traffic.
     * @param natSipStunUse [MediaStunUse.value] for call signaling traffic.
     * @param natIceEnabled whether ICE NAT traversal is enabled.
     * @param natSdpNatRewriteUse whether to rewrite NAT'd addresses in the session description.
     * @param natContactRewriteUse whether to rewrite the contact address behind NAT.
     * @param natViaRewriteUse whether to rewrite the via address behind NAT.
     * @param busyEverywhereUse whether to report "busy everywhere" when declining calls.
     * @param srtpSecureSignaling required signaling security level for SRTP.
     * @param srtpUse [SrtpUse.value].
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
        this.transport = TransportUse.fromValue(transport)
        this.natMediaStunUse = MediaStunUse.fromValue(natMediaStunUse)
        this.natSipStunUse = MediaStunUse.fromValue(natSipStunUse)
        this.natIceEnabled = natIceEnabled
        this.natSdpNatRewriteUse = natSdpNatRewriteUse
        this.natContactRewriteUse = natContactRewriteUse
        this.natViaRewriteUse = natViaRewriteUse
        this.srtpSecureSignaling = srtpSecureSignaling
        this.srtpUse = SrtpUse.fromValue(srtpUse)
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

    /**
     * Whether declining an unanswered incoming call responds with 600 Busy Everywhere
     * (stops the call ringing on the user's other devices) instead of 486 Busy Here.
     */
    var busyEverywhereUse: Boolean = false

    /**
     * Video encoding preset for all calls of the client. Default: [VideoQuality.MEDIUM].
     * Changed through [VoiceClient.configure], it applies to calls started afterwards.
     */
    var videoQuality: VideoQuality = VideoQuality.DEFAULT


    override fun toString(): String {
        return "transport: ${transport}; natMediaStunUse: $natMediaStunUse; natSipStunUse: $natSipStunUse; " +
                "natIceEnabled: $natIceEnabled; natSdpNatRewriteUse: $natSdpNatRewriteUse; natContactRewriteUse: $natContactRewriteUse" +
                "; natViaRewriteUse: $natViaRewriteUse; srtpSecureSignaling: $srtpSecureSignaling; srtpUse: $srtpUse" +
                "; busyEverywhereUse: $busyEverywhereUse; videoQuality: $videoQuality"
    }


    /** Media encryption (SRTP) usage policy. */
    enum class SrtpUse(val value: Int) {
        DISABLED(0),
        OPTIONAL(1),
        MANDATORY(2);

        internal companion object {
            fun fromValue(value: Int): SrtpUse =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unknown SrtpUse value: $value")
        }
    }


    /** Network transport protocol used for call signaling. */
    enum class TransportUse(val value: Int) {
        UDP(0),
        TCP(1),
        TCP_UDP(2),
        TLS(3);

        internal companion object {
            fun fromValue(value: Int): TransportUse =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unknown TransportUse value: $value")
        }
    }


    /** STUN usage policy for NAT traversal. */
    enum class MediaStunUse(val value: Int) {
        DEFAULT(0),
        DISABLED(1),
        RETRY_ON_FAILURE(2);

        internal companion object {
            fun fromValue(value: Int): MediaStunUse =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unknown MediaStunUse value: $value")
        }
    }
}