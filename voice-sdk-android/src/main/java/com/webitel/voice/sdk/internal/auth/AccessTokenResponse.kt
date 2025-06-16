package com.webitel.voice.sdk.internal.auth

import com.google.gson.annotations.SerializedName


internal data class AccessTokenResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    val call: CallInfo
)


internal data class CallInfo(
    @SerializedName("user_id")
    val userId: String,
    val proxy: String,
    val realm: String,
    val secret: String?
)