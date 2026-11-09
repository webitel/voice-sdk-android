package com.webitel.voice.sdk.internal.services

import com.webitel.voice.sdk.internal.network.HttpClient
import com.webitel.voice.sdk.internal.network.PushApi
import com.webitel.voice.sdk.internal.network.dto.PushSubscriptionRequest


/** Push flows that need the current access token from [AuthManager]. All methods throw `CallError`. */
internal class PushService(
    private val authManager: AuthManager,
    private val http: HttpClient
) {
    private val api = http.create(PushApi::class.java)


    suspend fun confirmPush(callId: String) {
        val token = authManager.currentAccessToken()
        http.send("confirmPush") { api.confirmPush(token, callId.lowercase()) }
    }


    suspend fun registerPushToken(fcmToken: String) {
        val token = authManager.currentAccessToken()
        http.send("subscribePush") {
            api.subscribe(token, PushSubscriptionRequest(pnType = PN_TYPE_FCM, pnRpid = fcmToken))
        }
    }


    suspend fun unregisterPushToken() {
        val token = authManager.currentAccessToken()
        http.send("unsubscribePush") {
            api.subscribe(token, PushSubscriptionRequest(pnType = "", pnRpid = null))
        }
    }


    private companion object {
        const val PN_TYPE_FCM = "fcm"
    }
}
