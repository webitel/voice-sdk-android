package com.webitel.voice.sdk.internal.auth

import com.google.gson.annotations.SerializedName


internal data class TokenRequest(
    val scope: List<String>,
    @SerializedName("grant_type")
    val grantType: String,
    @SerializedName("app_token")
    val appToken: String,
    @SerializedName("response_type")
    val responseType: List<String>,
    val identity: Identity,
    val code: String
)


internal data class Identity(
    val iss: String,
    val sub: String,
    val name: String
)