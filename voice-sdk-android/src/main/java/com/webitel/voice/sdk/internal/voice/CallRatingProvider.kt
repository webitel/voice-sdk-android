package com.webitel.voice.sdk.internal.voice


internal interface CallRatingProvider {
    suspend fun checkRatable(meetingId: String): Result<Boolean>
    suspend fun submitRating(meetingId: String, satisfaction: String): Result<Unit>
}
