package com.kipu.app.feature.auth.data.remote

import com.kipu.app.core.logging.SecureLog
import com.kipu.app.feature.auth.data.AUTH_TIMEOUT_MILLIS
import com.kipu.app.feature.auth.data.withAuthTimeout
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

sealed interface ApiResponse<out T> {
    data class Success<T>(val data: T) : ApiResponse<T>
    data class Error(val statusCode: Int, val problem: ProblemDto?, val retryAfter: Int? = null) : ApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : ApiResponse<Nothing>
}

@Singleton
class AuthApi @Inject constructor(
    httpClient: HttpClient,
) {
    private val httpClient = httpClient.config {
        install(HttpTimeout) {
            requestTimeoutMillis = AUTH_TIMEOUT_MILLIS
            connectTimeoutMillis = AUTH_TIMEOUT_MILLIS
            socketTimeoutMillis = AUTH_TIMEOUT_MILLIS
        }
    }

    suspend fun register(request: RegisterRequestDto): ApiResponse<RegisterResponseDto> {
        return try {
            withAuthTimeout {
                val response = httpClient.post("functions/v1/auth-access/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when (response.status) {
                    HttpStatusCode.Created -> ApiResponse.Success(response.body<RegisterResponseDto>())
                    else -> {
                        val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                        val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
                        ApiResponse.Error(response.status.value, problem, retryAfter)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResponse.NetworkFailure(e)
        }
    }

    suspend fun login(request: LoginRequestDto): ApiResponse<SessionEnvelopeDto> {
        return try {
            withAuthTimeout {
                val response = httpClient.post("functions/v1/auth-access/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when (response.status) {
                    HttpStatusCode.OK -> ApiResponse.Success(response.body<SessionEnvelopeDto>())
                    else -> {
                        val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                        val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
                        ApiResponse.Error(response.status.value, problem, retryAfter)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.e("AuthApi", "Login request failed (${e.javaClass.simpleName})", e)
            ApiResponse.NetworkFailure(e)
        }
    }

    /** Confirms the 6-digit code mailed at registration; success returns the same session envelope as [login]. */
    suspend fun verifyOtp(request: VerifyOtpRequestDto): ApiResponse<SessionEnvelopeDto> {
        return try {
            withAuthTimeout {
                val response = httpClient.post("functions/v1/auth-access/verify-otp") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when (response.status) {
                    HttpStatusCode.OK -> ApiResponse.Success(response.body<SessionEnvelopeDto>())
                    else -> {
                        val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                        val retryAfter = response.headers["Retry-After"]?.toIntOrNull() ?: problem?.retryAfterSeconds
                        ApiResponse.Error(response.status.value, problem, retryAfter)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.e("AuthApi", "Verify OTP request failed (${e.javaClass.simpleName})", e)
            ApiResponse.NetworkFailure(e)
        }
    }

    /** Mails a new code; the server rate-limits it and answers 429 with Retry-After. */
    suspend fun resendOtp(request: ResendOtpRequestDto): ApiResponse<Unit> {
        return try {
            withAuthTimeout {
                val response = httpClient.post("functions/v1/auth-access/resend-otp") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when (response.status) {
                    HttpStatusCode.Accepted, HttpStatusCode.OK -> ApiResponse.Success(Unit)
                    else -> {
                        val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                        val retryAfter = response.headers["Retry-After"]?.toIntOrNull() ?: problem?.retryAfterSeconds
                        ApiResponse.Error(response.status.value, problem, retryAfter)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResponse.NetworkFailure(e)
        }
    }

    suspend fun recovery(request: RecoveryRequestDto): ApiResponse<Unit> {
        return try {
            withAuthTimeout {
                val response = httpClient.post("functions/v1/auth-access/recovery") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when (response.status) {
                    HttpStatusCode.Accepted, HttpStatusCode.OK -> ApiResponse.Success(Unit)
                    else -> {
                        val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                        val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
                        ApiResponse.Error(response.status.value, problem, retryAfter)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResponse.NetworkFailure(e)
        }
    }
}
