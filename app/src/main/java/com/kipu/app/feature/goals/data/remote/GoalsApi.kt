package com.kipu.app.feature.goals.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

sealed interface GoalsApiResponse<out T> {
    data class Success<T>(val data: T) : GoalsApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : GoalsApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : GoalsApiResponse<Nothing>
}

@Singleton
class GoalsApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    suspend fun execute(commandType: GoalCommandType, payload: JsonObject): GoalsApiResponse<GoalCommandRemoteResult> {
        val rpc = RPC_BY_COMMAND[commandType]
            ?: return GoalsApiResponse.Error(400, "Unsupported goal command")
        return try {
            val session = sessionProvider.currentSession()
                ?: return GoalsApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/$rpc") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer " + session.accessToken)
                setBody(GoalRpcRequestDto(payload))
            }
            when (response.status) {
                HttpStatusCode.OK -> GoalsApiResponse.Success(response.body<GoalCommandRemoteResult>())
                else -> GoalsApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GoalsApiResponse.NetworkFailure(e)
        }
    }

    private companion object {
        val RPC_BY_COMMAND = mapOf(
            GoalCommandType.CREATE_GOAL to "create_goal_v1",
            GoalCommandType.UPDATE_GOAL to "update_goal_v1",
            GoalCommandType.DELETE_EMPTY_GOAL to "delete_empty_goal_v1",
            GoalCommandType.RESERVE_GOAL to "reserve_goal_v1",
            GoalCommandType.RELEASE_GOAL to "release_goal_v1",
            GoalCommandType.REASSIGN_GOAL_RESERVE to "reassign_goal_reserve_v1",
            GoalCommandType.ARCHIVE_GOAL to "archive_goal_v1",
        )
    }
}
