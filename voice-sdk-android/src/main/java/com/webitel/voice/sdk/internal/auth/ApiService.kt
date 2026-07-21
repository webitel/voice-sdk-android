package com.webitel.voice.sdk.internal.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


internal interface ApiService {
    @POST("api/portal/token")
    suspend fun userLogin(@Body request: TokenRequest): Response<AccessTokenResponse>

    @GET("api/portal/token")
    suspend fun getSipConfig(): Response<AccessTokenResponse>

    @GET("api/meetings/{id}")
    suspend fun getMeeting(@Path("id") meetingId: String): Response<MeetingResponse>

    @POST("api/meetings/{id}/satisfaction")
    suspend fun submitSatisfaction(
        @Path("id") meetingId: String,
        @Body request: SatisfactionRequest
    ): Response<Unit>
}

