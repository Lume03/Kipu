package com.kipu.app.feature.plans.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class DevPlanActivationDto(
    val outcome: String,
    val code: String? = null,
    val planType: String? = null,
    val operationId: String? = null,
    val expiresAt: String? = null,
)

@Serializable
private data class DevPlanRequest(val p_operation_id: String, val p_plan_type: String)

class DevPlanApi(private val client: HttpClient, private val supabaseUrl: String) {
    suspend fun mode(session: AuthenticatedSession): Boolean {
        val response = client.post(supabaseUrl.trimEnd('/') + "/rest/v1/rpc/dev_plan_mode") {
            bearerAuth(session.accessToken)
            header("Content-Type", ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody("{}")
        }
        check(response.status.isSuccess()) { "DEV_MODE_UNAVAILABLE" }
        return response.body()
    }

    suspend fun activate(session: AuthenticatedSession, operation: UUID, planType: String): DevPlanActivationDto = try {
        val response = client.post(supabaseUrl.trimEnd('/') + "/rest/v1/rpc/dev_activate_plan") {
            bearerAuth(session.accessToken)
            header("Content-Type", ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody(DevPlanRequest(operation.toString(), planType))
        }
        if (response.status.isSuccess()) response.body()
        else DevPlanActivationDto("RETRYABLE", if (response.status.value == 401) "UNAUTHENTICATED" else "DEV_PLAN_UNAVAILABLE")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DevPlanActivationDto("RETRYABLE", "DEV_PLAN_UNAVAILABLE")
    }
}
