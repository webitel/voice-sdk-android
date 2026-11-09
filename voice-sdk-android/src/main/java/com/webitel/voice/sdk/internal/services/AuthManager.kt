package com.webitel.voice.sdk.internal.services

import com.webitel.voice.sdk.AuthMethod
import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.User
import com.webitel.voice.sdk.internal.client.ClientContext
import com.webitel.voice.sdk.internal.network.HttpClient
import com.webitel.voice.sdk.internal.network.TokenApi
import com.webitel.voice.sdk.internal.network.dto.AccessTokenResponse
import com.webitel.voice.sdk.internal.network.dto.Identity
import com.webitel.voice.sdk.internal.network.dto.TokenRequest
import com.webitel.voice.sdk.internal.sip.SipConfig
import com.webitel.voice.sdk.internal.support.SdkLogger


/** Obtains SIP credentials and the access token for authenticated non-SIP requests. */
internal class AuthManager(
    private val context: ClientContext,
    private val http: HttpClient
) {
    private val api = http.create(TokenApi::class.java)

    /**
     * Last access token obtained while fetching the SIP config: the provider JWT for
     * [AuthMethod.Token], or the token returned by login for [AuthMethod.Contact].
     */
    @Volatile
    private var accessToken: String? = null


    /** @throws CallError */
    suspend fun getSipConfig(meetingId: String?): SipConfig {
        val meeting = meetingId?.takeIf { it.isNotBlank() }
        return when (val method = context.authMethod) {
            is AuthMethod.Contact -> fetchSipConfigWithUser(method.identity, meeting)
            is AuthMethod.Token -> fetchSipConfigWithJwt(method.tokenProvider())
        }
    }


    /**
     * Token for authenticated non-SIP requests (push subscription and confirmation).
     * @throws CallError
     */
    suspend fun currentAccessToken(): String {
        when (val method = context.authMethod) {
            is AuthMethod.Token -> accessToken = method.tokenProvider()
            is AuthMethod.Contact -> if (accessToken == null) getSipConfig(meetingId = null)
        }
        return accessToken?.takeIf { it.isNotEmpty() }
            ?: throw CallError.Unauthorized("Access token is not available")
    }


    /** JWT for the meeting (rating) API; only [AuthMethod.Token] provides one. */
    fun portalJwt(): String? {
        return (context.authMethod as? AuthMethod.Token)?.tokenProvider?.invoke()?.takeIf { it.isNotEmpty() }
    }


    private suspend fun fetchSipConfigWithUser(user: User, meetingId: String?): SipConfig {
        SdkLogger.debug(TAG, "fetchSipConfigWithUser: sub - ${user.sub}; name - ${user.name}; meetingId - $meetingId")
        val request = TokenRequest(
            scope = listOf("call"),
            grantType = "identity",
            appToken = context.clientToken,
            responseType = listOf("call", "token"),
            identity = Identity(iss = user.iss, sub = user.sub, name = user.name),
            meetingId = meetingId,
            code = "authorization_code"
        )
        val response = http.send("login") { api.login(request) }
        return processSipResponse(response)
    }


    private suspend fun fetchSipConfigWithJwt(jwt: String): SipConfig {
        SdkLogger.debug(TAG, "fetchSipConfigWithJwt")
        if (jwt.isEmpty()) throw CallError.Unauthorized("The token provider returned an empty token")
        val response = http.send("getSipConfig") { api.getSipConfig(jwt) }
        accessToken = jwt
        return processSipResponse(response)
    }


    private fun processSipResponse(response: AccessTokenResponse?): SipConfig {
        val call = response?.call
            ?: throw CallError.InvalidResponse("Sip Config not found in response")

        response.accessToken?.takeIf { it.isNotEmpty() }?.let { accessToken = it }
        val password = call.secret?.takeIf { it.isNotEmpty() } ?: response.accessToken.orEmpty()

        val config = SipConfig(
            auth = context.deviceId,
            domain = call.realm,
            extension = call.userId,
            password = password,
            proxy = call.proxy
        )
        SdkLogger.debug(TAG, "processSipResponse: $config")
        return config
    }


    private companion object {
        const val TAG = "AuthManager"
    }
}
