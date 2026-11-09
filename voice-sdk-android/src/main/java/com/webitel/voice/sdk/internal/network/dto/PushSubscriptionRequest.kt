package com.webitel.voice.sdk.internal.network.dto

import com.google.gson.annotations.SerializedName


/** An empty [pnType] with a null [pnRpid] unsubscribes the device. */
internal data class PushSubscriptionRequest(
    @SerializedName("pn-type")
    val pnType: String,
    @SerializedName("pn-rpid")
    val pnRpid: String?
)
