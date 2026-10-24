package com.kipu.app.feature.budgets.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BudgetApiResponse<out T> {
    data class Success<T>(val data: T) : BudgetApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : BudgetApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : BudgetApiResponse<Nothing>
}

@Singleton
class BudgetApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    suspend fun fetchBudgets(userId: String): BudgetApiResponse<List<BudgetRemoteDto>> {
        val auth = authHeader(userId) ?: return unauthenticated()
        return execute(
            block = {
                httpClient.get("rest/v1/budgets") {
                    header(HttpHeaders.Authorization, auth)
                    url {
                        parameters.append("select", BUDGET_COLUMNS)
                        parameters.append("user_id", "eq.$userId")
                        parameters.append("deleted_at", "is.null")
                        parameters.append("order", "updated_at.asc,id.asc")
                    }
                }
            },
            decode = { it.body() },
        )
    }

    suspend fun fetchPeriods(userId: String): BudgetApiResponse<List<BudgetPeriodRemoteDto>> {
        val auth = authHeader(userId) ?: return unauthenticated()
        return execute(
            block = {
                httpClient.get("rest/v1/budget_periods") {
                    header(HttpHeaders.Authorization, auth)
                    url {
                        parameters.append("select", PERIOD_COLUMNS)
                        parameters.append("user_id", "eq.$userId")
                        parameters.append("order", "starts_on.asc,id.asc")
                    }
                }
            },
            decode = { it.body() },
        )
    }

    suspend fun fetchTombstones(userId: String): BudgetApiResponse<List<BudgetTombstoneRemoteDto>> {
        val auth = authHeader(userId) ?: return unauthenticated()
        return execute(
            block = {
                httpClient.get("rest/v1/budget_tombstones") {
                    header(HttpHeaders.Authorization, auth)
                    url {
                        parameters.append("select", "budget_id,user_id,last_revision,deleted_at")
                        parameters.append("user_id", "eq.$userId")
                    }
                }
            },
            decode = { it.body() },
        )
    }

    suspend fun fetchBudget(userId: String, budgetId: String): BudgetApiResponse<List<BudgetRemoteDto>> {
        val auth = authHeader(userId) ?: return unauthenticated()
        return execute(
            block = {
                httpClient.get("rest/v1/budgets") {
                    header(HttpHeaders.Authorization, auth)
                    url {
                        parameters.append("select", BUDGET_COLUMNS)
                        parameters.append("user_id", "eq.$userId")
                        parameters.append("id", "eq.$budgetId")
                        parameters.append("deleted_at", "is.null")
                        parameters.append("limit", "1")
                    }
                }
            },
            decode = { it.body() },
        )
    }

    suspend fun upsertBudget(
        userId: String,
        budget: BudgetRemoteDto,
        expectedRevision: Long?,
    ): BudgetApiResponse<List<BudgetRemoteDto>> {
        if (budget.userId != userId || !isUuid(budget.id)) return BudgetApiResponse.Error(400, "Invalid budget owner or id")
        val auth = authHeader(userId) ?: return unauthenticated()
        val prefer = "return=representation"
        return execute(
            block = {
                if (expectedRevision == null) {
                    httpClient.post("rest/v1/budgets") {
                        header(HttpHeaders.Authorization, auth)
                        header(HttpHeaders.Prefer, prefer)
                        contentType(ContentType.Application.Json)
                        setBody(budget)
                    }
                } else {
                    httpClient.patch("rest/v1/budgets") {
                        header(HttpHeaders.Authorization, auth)
                        header(HttpHeaders.Prefer, prefer)
                        contentType(ContentType.Application.Json)
                        url {
                            parameters.append("id", "eq.${budget.id}")
                            parameters.append("user_id", "eq.$userId")
                            parameters.append("revision", "eq.$expectedRevision")
                            parameters.append("deleted_at", "is.null")
                        }
                        setBody(budget)
                    }
                }
            },
            decode = { it.body() },
        )
    }

    suspend fun upsertPeriod(userId: String, period: BudgetPeriodRemoteDto): BudgetApiResponse<Unit> {
        if (period.userId != userId || !isUuid(period.id) || !isUuid(period.budgetId)) {
            return BudgetApiResponse.Error(400, "Invalid budget period owner or id")
        }
        val auth = authHeader(userId) ?: return unauthenticated()
        return execute(
            block = {
                httpClient.post("rest/v1/budget_periods") {
                    header(HttpHeaders.Authorization, auth)
                    header(HttpHeaders.Prefer, "resolution=merge-duplicates,return=minimal")
                    contentType(ContentType.Application.Json)
                    url { parameters.append("on_conflict", "id") }
                    setBody(period)
                }
            },
            decode = { },
        )
    }

    suspend fun deleteBudget(userId: String, budgetId: String): BudgetApiResponse<BudgetCommandResponseDto> {
        if (!isUuid(budgetId)) return BudgetApiResponse.Error(400, "Invalid budget id")
        val auth = authHeader(userId) ?: return unauthenticated()
        return execute(
            block = {
                httpClient.post("rest/v1/rpc/delete_budget_v1") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(BudgetDeleteRpcRequestDto(BudgetDeleteCommandDto(budgetId)))
                }
            },
            decode = { it.body() },
        )
    }

    private suspend fun authHeader(userId: String): String? {
        val session = sessionProvider.currentSession() ?: return null
        if (session.userId != runCatching { UUID.fromString(userId) }.getOrNull()) return null
        return "Bearer ${session.accessToken}"
    }

    private suspend fun <T> execute(
        block: suspend () -> HttpResponse,
        decode: suspend (HttpResponse) -> T,
    ): BudgetApiResponse<T> = try {
        val response = block()
        if (response.status.isSuccess()) {
            BudgetApiResponse.Success(decode(response))
        } else {
            BudgetApiResponse.Error(response.status.value, response.bodyAsText().take(MAX_ERROR_BODY_LENGTH))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (responseError: ResponseException) {
        BudgetApiResponse.Error(
            responseError.response.status.value,
            runCatching { responseError.response.bodyAsText().take(MAX_ERROR_BODY_LENGTH) }.getOrNull(),
        )
    } catch (failure: Exception) {
        BudgetApiResponse.NetworkFailure(failure)
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun <T> unauthenticated(): BudgetApiResponse<T> = BudgetApiResponse.Error(401, "No matching active session")

    companion object {
        private const val MAX_ERROR_BODY_LENGTH = 240
        private const val BUDGET_COLUMNS =
            "id,user_id,name,category_id,limit_minor,currency_code,period_type,range_start_on,range_end_on," +
                "alert_thresholds_bps,status,is_active,revision,created_at,updated_at,deleted_at"
        private const val PERIOD_COLUMNS = "id,user_id,budget_id,starts_on,ends_on,limit_minor,created_at"
    }
}
