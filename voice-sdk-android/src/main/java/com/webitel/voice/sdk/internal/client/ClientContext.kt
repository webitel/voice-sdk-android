package com.webitel.voice.sdk.internal.client

import com.webitel.voice.sdk.AuthMethod
import com.webitel.voice.sdk.CallSettings
import com.webitel.voice.sdk.SystemCallIntegration


/** Immutable configuration of one `VoiceClient`, built from its `Builder`. */
internal class ClientContext(
    val baseUrl: String,
    val clientToken: String,
    val authMethod: AuthMethod,
    val deviceId: String,
    val userAgent: String,
    val callSettings: CallSettings,
    /** Time from receiving an incoming call push to the SIP INVITE arriving. */
    val incomingCallTimeoutMs: Long,
    val systemCallIntegration: SystemCallIntegration
)
