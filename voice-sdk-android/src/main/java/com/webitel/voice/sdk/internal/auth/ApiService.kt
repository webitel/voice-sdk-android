package com.webitel.voice.sdk.internal.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST


internal interface ApiService {
    @POST("api/portal/token")
    suspend fun userLogin(@Body request: TokenRequest): Response<AccessTokenResponse>

    @GET("api/portal/token")
    suspend fun getSipConfig(): Response<AccessTokenResponse>
}

