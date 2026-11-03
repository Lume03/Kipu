package com.kipu.app.feature.categories.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/** H-05: a PostgREST error body must never complete the outbox command. */
class SyncCategoryCommandsWorkerRemoteErrorTest {
    private val userId = UUID.randomUUID().toString()
    private val categoryDao = mockk<CategoryDao>(relaxed = true)

    private fun worker(status: HttpStatusCode, rpcBody: String, getFails: Boolean = false): SyncCategoryCommandsWorker {
        val client = HttpClient(MockEngine { request ->
            val isRpc = request.method == HttpMethod.Post
            if (isRpc) respond(rpcBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
            else respond(if (getFails) "{}" else "[]", if (getFails) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
            expectSuccess = false
        }
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession() = AuthenticatedSession(UUID.fromString(userId), "token")
            override suspend fun refreshSession() = currentSession()
        }
        val coordinator = mockk<SessionCoordinator>(relaxed = true)
        every { coordinator.localAccess } returns MutableStateFlow(LocalAccess.Available(userId, RemoteSession.Absent))
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns workDataOf(SyncCategoryCommandsWorker.KEY_USER_ID to userId)
        val command = CategorySyncOutboxEntity(
            operationId = "op-1", userId = userId, commandType = "RESOLVE_CONFLICT", aggregateType = "CATEGORY",
            aggregateId = "cat-1",
            payloadJson = """{"operation_id":"op-1","conflict_id":"c-1","chosen_version":"LOCAL","payload_hash":"h"}""",
            payloadHash = "h", state = "PENDING", createdAt = 1L, updatedAt = 1L,
        )
        coEvery { categoryDao.getPendingOutboxCommands(userId) } returns listOf(command)
        return SyncCategoryCommandsWorker(
            mockk<Context>(relaxed = true), params, categoryDao, mockk<MerchantCatalogDao>(relaxed = true),
            mockk<MerchantRulesDao>(relaxed = true), CategoriesApi(client, sessions), coordinator,
        )
    }

    @Test fun `postgrest errors never complete the outbox command`() = runTest {
        val body = """{"code":"PGRST301","message":"JWT expired","details":null,"hint":null}"""
        for (code in listOf(400, 401, 409, 429, 500)) {
            val w = worker(HttpStatusCode.fromValue(code), body)
            val result = w.doWork()
            assertTrue(if (code in listOf(401, 429, 500)) result is ListenableWorker.Result.Retry else result is ListenableWorker.Result.Success)
            coVerify(exactly = 0) { categoryDao.updateOutboxCommand(match { it.state == "COMPLETED" }) }
        }
    }

    @Test fun `a 2xx answer with success true still completes the command`() = runTest {
        val w = worker(HttpStatusCode.OK, """{"success":true,"status":"APPLIED"}""")
        w.doWork()
        coVerify(exactly = 1) { categoryDao.updateOutboxCommand(match { it.state == "COMPLETED" }) }
    }

    @Test fun `failed hydration is retried and cannot be reported as completed`() = runTest {
        assertTrue(worker(HttpStatusCode.OK, "{}", getFails = true).doWork() is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { categoryDao.updateOutboxCommand(any()) }
    }
}
