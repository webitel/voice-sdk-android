package com.webitel.voice.sdk.internal.network

import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.LogLevel
import com.webitel.voice.sdk.internal.client.ClientContext
import com.webitel.voice.sdk.internal.support.SdkLogger
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


/** One OkHttp/Retrofit stack per client, shared by all API interfaces. */
internal class HttpClient(context: ClientContext) {

    private val retrofit: Retrofit

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("x-portal-device", context.deviceId)
                    .header("x-portal-client", context.clientToken)
                    .header("User-Agent", context.userAgent)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = loggingLevel() })
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(context.baseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }


    fun <T> create(api: Class<T>): T = retrofit.create(api)


    /** Runs [request] and returns its body, mapping every failure to a [CallError]. */
    suspend fun <T> send(label: String, request: suspend () -> Response<T>): T? {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = mapException(e)
            SdkLogger.error("HttpClient", "$label: $error")
            throw error
        }

        if (!response.isSuccessful) {
            val body = try { response.errorBody()?.string() } catch (_: Exception) { null }
            val message = "$label: HTTP ${response.code()} ${body.orEmpty()}".trim()
            SdkLogger.error("HttpClient", message)
            throw if (response.code() == 401) CallError.Unauthorized(message)
            else CallError.Unknown(message, response.code())
        }
        return response.body()
    }


    private fun mapException(e: Exception): CallError = when (e) {
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException ->
            CallError.Unknown("No internet connection or server unreachable", ERROR_CODE_NETWORK)
        is SSLException -> CallError.Unknown("SSL error: ${e.message}", ERROR_CODE_SSL)
        is HttpException -> CallError.Unknown("HTTP error: ${e.message}", e.code())
        is IOException -> CallError.Unknown("Network error: ${e.message}", ERROR_CODE_NETWORK)
        is IllegalArgumentException -> CallError.InvalidUrl("Invalid request: ${e.message}")
        else -> CallError.Unknown("Unexpected error: ${e.message}", ERROR_CODE_UNKNOWN)
    }


    private fun loggingLevel(): HttpLoggingInterceptor.Level =
        if (SdkLogger.level == LogLevel.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE


    private fun normalizeBaseUrl(baseUrl: String): String {
        val withScheme = if (baseUrl.startsWith("http://", ignoreCase = true)
            || baseUrl.startsWith("https://", ignoreCase = true)) {
            baseUrl
        } else {
            "https://$baseUrl"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }


    companion object {
        const val ERROR_CODE_NETWORK = -2
        const val ERROR_CODE_SSL = -3
        const val ERROR_CODE_UNKNOWN = -999
    }
}
