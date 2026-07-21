package com.webitel.voice.sdk.internal.auth

import com.google.gson.annotations.SerializedName


internal data class MeetingResponse(
    @SerializedName("allow_satisfaction")
    val allowSatisfaction: Boolean
)
