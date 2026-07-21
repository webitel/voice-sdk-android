package com.webitel.voice.sdk.internal.auth

import com.webitel.voice.sdk.LogLevel
import com.webitel.voice.sdk.User
import com.webitel.voice.sdk.internal.sip.SipConfig
import com.webitel.voice.sdk.internal.voice.CallException
import com.webitel.voice.sdk.internal.voice.CallRatingProvider
import com.webitel.voice.sdk.internal.voice.WebitelVoiceClient.Companion.logger
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException


internal class AuthManager(
    private val baseUrl: String,
    private val deviceId: String,
    private val userAgent: String,
    private val clientToken: String
) : CallRatingProvider {
    @Volatile
    private var jwt: String = ""
    @Volatile
    private var user: User? = null


    suspend fun getSipConfig(meetingId: String?): Result<SipConfig> {
        user?.let { return fetchSipConfigWithUser(it, meetingId) }

        return if (jwt.isNotEmpty()) {
            fetchSipConfigWithJwt()
        } else {
            Result.failure(CallException(10, "User or JWT not set"))
        }
    }


    fun setUser(user: User) {
        this.user = user
    }


    fun setJWT(jwt: String) {
        this.jwt = jwt
    }


    private suspend fun fetchSipConfigWithUser(user: User, meetingId: String?): Result<SipConfig> {
        logger.debug("AuthManager",
            "fetchSipConfigWithUser: sub - ${user.sub}; name - ${user.name}; meetingId - $meetingId"
        )
        val request = TokenRequest(
            scope = listOf("call"),
            grantType = "identity",
            appToken = clientToken,
            responseType = listOf("call", "token"),
            identity = Identity(
                iss = user.iss,
                sub = user.sub,
                name = user.name
            ),
            meetingId = meetingId,
            code = "authorization_code"
        )

        return try {
            val api = createApi()
            val response = api.userLogin(request)
            processSipResponse(response)
        }catch (e: Exception) {
            handleApiException(e)
        }
    }


    private suspend fun fetchSipConfigWithJwt(): Result<SipConfig> {
        logger.debug("AuthManager", "fetchSipConfigWithJwt")
        return try {
            val api = createApi(jwt)
            val response = api.getSipConfig()

            return processSipResponse(response)
        }catch (e: Exception) {
            handleApiException(e)
        }
    }


    override suspend fun checkRatable(meetingId: String): Result<Boolean> {
        return try {
            val response = createApi(jwt.ifEmpty { null }).getMeeting(meetingId)
            if (!response.isSuccessful) {
                Result.failure(CallException(response.code(), response.errorBody()?.string() ?: "Unknown error"))
            } else {
                Result.success(response.body()?.allowSatisfaction ?: false)
            }
        } catch (e: Exception) {
            Result.failure(mapExceptionToCallException(e))
        }
    }


    override suspend fun submitRating(meetingId: String, satisfaction: String): Result<Unit> {
        return try {
            val response = createApi(jwt.ifEmpty { null })
                .submitSatisfaction(meetingId, SatisfactionRequest(satisfaction))
            if (!response.isSuccessful) {
                Result.failure(CallException(response.code(), response.errorBody()?.string() ?: "Unknown error"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(mapExceptionToCallException(e))
        }
    }


    private fun handleApiException(e: Exception): Result<SipConfig> {
        val callException = mapExceptionToCallException(e)
        logger.error("AuthManager", "handleApiException: $callException")
        return Result.failure(callException)
    }


    private fun mapExceptionToCallException(e: Exception): CallException {
        return when (e) {
            is CancellationException -> CallException(10102, "Job was cancelled. Call disconnected.")
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException -> CallException(ERROR_CODE_NETWORK, "No internet connection or server unreachable")
            is SSLException -> CallException(ERROR_CODE_SSL, "SSL error: ${e.message}")
            is HttpException -> CallException(e.code(), "HTTP error: ${e.message}")
            is IOException -> CallException(ERROR_CODE_NETWORK, "Network error: ${e.message}")
            else -> CallException(ERROR_CODE_UNKNOWN, "Unexpected error: ${e.message}")
        }
    }


    private fun createApi(jwt: String? = null): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = getLoggingLevel()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("x-portal-device", deviceId)
                    .addHeader("x-portal-client", clientToken)
                    .addHeader("User-Agent", userAgent)

                jwt?.let { requestBuilder.addHeader("x-portal-access", it) }

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }


    private fun getLoggingLevel(): HttpLoggingInterceptor.Level =
        if (logger.level == LogLevel.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE



    private fun processSipResponse(response: Response<AccessTokenResponse>): Result<SipConfig> {
        if (!response.isSuccessful) {
            return Result.failure(CallException(response.code(), response.errorBody()?.string() ?: "Unknown error"))
        }

        val sip = response.body()
            ?: return Result.failure(CallException(-1, "Sip Config not found in response"))

        val password = sip.call.secret.takeIf { !it.isNullOrEmpty() } ?: sip.accessToken

        val config = SipConfig(
            auth = deviceId,
            domain = sip.call.realm,
            extension = sip.call.userId,
            password = password,
            proxy = sip.call.proxy
        )
        logger.debug("SipConfig", config.toString())

        return Result.success(config)
    }


    private fun normalizeBaseUrl(baseUrl: String): String {
        return if (baseUrl.startsWith("http://", ignoreCase = true)
            || baseUrl.startsWith("https://", ignoreCase = true)) {
            baseUrl
        } else {
            "https://$baseUrl"
        }
    }
}


const val ERROR_CODE_NETWORK = -2
const val ERROR_CODE_SSL = -3
const val ERROR_CODE_UNKNOWN = -999
