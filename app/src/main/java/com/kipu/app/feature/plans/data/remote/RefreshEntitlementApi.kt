package com.kipu.app.feature.plans.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class RefreshEntitlementRequestDto(val installationPublicKey: String) {
    override fun toString(): String = "RefreshEntitlementRequestDto(installationPublicKey=[REDACTED])"
}

@Serializable
data class RefreshEntitlementResponseDto(
    val effectiveTier: String,
    val source: String,
    val expiresAt: String? = null,
    val offlineEntitlementGrant: RefreshEntitlementGrantDto? = null,
)

@Serializable
data class RefreshEntitlementGrantDto(
    val payload: String,
    val signature: String,
    val keyId: String,
) {
    fun asSignedGrant() = SignedOfflineEntitlementGrant(payload, signature, keyId)
}

sealed interface RefreshEntitlementApiResult {
    data class Success(val value: RefreshEntitlementResponseDto) : RefreshEntitlementApiResult
    data class Failure(val code: String) : RefreshEntitlementApiResult
}

class RefreshEntitlementApi(private val client: HttpClient, private val supabaseUrl: String) {
    suspend fun refresh(session: AuthenticatedSession, installationPublicKey: String): RefreshEntitlementApiResult = try {
        val response = client.post(supabaseUrl.trimEnd('/') + "/functions/v1/refresh-entitlement") {
            bearerAuth(session.accessToken)
            header("Content-Type", ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody(RefreshEntitlementRequestDto(installationPublicKey))
        }
        if (response.status.isSuccess()) {
            RefreshEntitlementApiResult.Success(response.body())
        } else {
            // Supabase error details are intentionally not surfaced; do not log auth/session data.
            val code = runCatching { response.body<RefreshEntitlementErrorDto>().code }.getOrNull()
            RefreshEntitlementApiResult.Failure(code ?: when (response.status.value) {
                401 -> "UNAUTHENTICATED"
                403 -> "TEST_PLANS_DISABLED"
                else -> "REFRESH_UNAVAILABLE"
            })
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        RefreshEntitlementApiResult.Failure("REFRESH_UNAVAILABLE")
    }
}

@Serializable
private data class RefreshEntitlementErrorDto(val code: String? = null)
