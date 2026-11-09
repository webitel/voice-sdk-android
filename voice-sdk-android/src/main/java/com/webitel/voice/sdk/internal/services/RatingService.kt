package com.webitel.voice.sdk.internal.services

import com.webitel.voice.sdk.internal.call.CallRatingProvider
import com.webitel.voice.sdk.internal.network.HttpClient
import com.webitel.voice.sdk.internal.network.MeetingApi
import com.webitel.voice.sdk.internal.network.dto.SatisfactionRequest


/** Rating of calls placed into a meeting. All methods throw `CallError`. */
internal class RatingService(
    private val authManager: AuthManager,
    private val http: HttpClient
) : CallRatingProvider {
    private val api = http.create(MeetingApi::class.java)


    override suspend fun checkRatable(meetingId: String): Boolean {
        val meeting = http.send("getMeeting") { api.getMeeting(authManager.portalJwt(), meetingId) }
        return meeting?.allowSatisfaction ?: false
    }


    override suspend fun submitRating(meetingId: String, satisfaction: String) {
        http.send("submitSatisfaction") {
            api.submitSatisfaction(authManager.portalJwt(), meetingId, SatisfactionRequest(satisfaction))
        }
    }
}
