package com.webitel.voice.sdk.internal.network.dto

import com.google.gson.annotations.SerializedName


internal data class AccessTokenResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    val call: PortalCallInfo?
)


internal data class PortalCallInfo(
    @SerializedName("user_id")
    val userId: String,
    val proxy: String,
    val realm: String,
    val secret: String?
)
