package com.kipu.app.feature.recurrences.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed interface RecurrencesApiResponse<out T> {
    data class Success<T>(val data: T) : RecurrencesApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : RecurrencesApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : RecurrencesApiResponse<Nothing>
}

@Serializable
data class RecurrenceRemoteError(val code: String, val revision: Long? = null)

@Serializable
data class RecurrenceRemoteResult(
    val status: String,
    @SerialName("recurrence_id") val recurrenceId: String? = null,
    val revision: Long? = null,
    val error: RecurrenceRemoteError? = null,
)

@Singleton
class RecurrencesApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    suspend fun execute(userId: String, commandType: String, command: JsonObject): RecurrencesApiResponse<RecurrenceRemoteResult> {
        val rpc = when (commandType) {
            "CREATE_RECURRENCE" -> "create_recurrence_v1"
            "UPDATE_RECURRENCE" -> "update_recurrence_v1"
            "SET_RECURRENCE_STATUS" -> "set_recurrence_status_v1"
            else -> return RecurrencesApiResponse.Error(400, "Unsupported recurrence command")
        }
        return callRpc(userId, rpc, mapOf("p_command" to command))
    }

    suspend fun pullChanges(userId: String, afterSequence: Long): RecurrencesApiResponse<PullChangesResponseDto> =
        callRpc(userId, "pull_financial_changes_v1", PullChangesRequestDto(afterSequence = afterSequence))

    private suspend inline fun <reified T, reified R> callRpc(userId: String, rpc: String, request: T): RecurrencesApiResponse<R> = try {
        val session = sessionProvider.currentSession()
        if (session == null || session.userId.toString() != userId) {
            RecurrencesApiResponse.Error(401, "No active session for owner")
        } else {
            val response = httpClient.post("rest/v1/rpc/$rpc") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer " + session.accessToken)
                setBody(request)
            }
            if (response.status.value in 200..299) RecurrencesApiResponse.Success(response.body<R>())
            else RecurrencesApiResponse.Error(response.status.value, response.body<String>())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RecurrencesApiResponse.NetworkFailure(e)
    }
}
