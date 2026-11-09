package com.webitel.voice.sdk.internal.call


/** Rating of calls placed into a meeting. Both methods throw `CallError`. */
internal interface CallRatingProvider {
    suspend fun checkRatable(meetingId: String): Boolean
    suspend fun submitRating(meetingId: String, satisfaction: String)
}
