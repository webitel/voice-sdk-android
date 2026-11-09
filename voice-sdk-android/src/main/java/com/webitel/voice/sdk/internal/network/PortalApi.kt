package com.webitel.voice.sdk.internal.network

import com.webitel.voice.sdk.internal.network.dto.AccessTokenResponse
import com.webitel.voice.sdk.internal.network.dto.MeetingResponse
import com.webitel.voice.sdk.internal.network.dto.PushSubscriptionRequest
import com.webitel.voice.sdk.internal.network.dto.SatisfactionRequest
import com.webitel.voice.sdk.internal.network.dto.TokenRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path


/** SIP credentials: login as a contact, or exchange a JWT. */
internal interface TokenApi {
    @POST("api/portal/token")
    suspend fun login(@Body request: TokenRequest): Response<AccessTokenResponse>

    @GET("api/portal/token")
    suspend fun getSipConfig(@Header(HEADER_PORTAL_ACCESS) jwt: String): Response<AccessTokenResponse>
}


/** Call rating, available for calls placed into a meeting. */
internal interface MeetingApi {
    @GET("api/meetings/{id}")
    suspend fun getMeeting(
        @Header(HEADER_PORTAL_ACCESS) jwt: String?,
        @Path("id") meetingId: String
    ): Response<MeetingResponse>

    @POST("api/meetings/{id}/satisfaction")
    suspend fun submitSatisfaction(
        @Header(HEADER_PORTAL_ACCESS) jwt: String?,
        @Path("id") meetingId: String,
        @Body request: SatisfactionRequest
    ): Response<Unit>
}


/** Push subscription and incoming-call push confirmation. */
internal interface PushApi {
    /** Subscribes (or, with an empty `pn-type`, unsubscribes) this device to call pushes. */
    @PUT("api/push/subscription")
    suspend fun subscribe(
        @Header(HEADER_WEBITEL_ACCESS) accessToken: String,
        @Body request: PushSubscriptionRequest
    ): Response<Unit>

    /**
     * Tells the server the SIP account is registered, so the incoming call announced by
     * a push can now be delivered.
     */
    @PATCH("api/calls/active/{id}/confirm_push")
    suspend fun confirmPush(
        @Header(HEADER_WEBITEL_ACCESS) accessToken: String,
        @Path("id") callId: String
    ): Response<Unit>
}


internal const val HEADER_PORTAL_ACCESS = "x-portal-access"
internal const val HEADER_WEBITEL_ACCESS = "X-Webitel-Access"
