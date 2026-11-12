package com.kipu.app.core.network

import com.kipu.app.feature.debts.data.remote.*
import com.kipu.app.feature.goals.data.remote.*
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import com.kipu.app.feature.categories.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test

class CommandOwnerSessionTest {
    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private class Sessions(var session: AuthenticatedSession) : AuthenticatedSessionProvider {
        override suspend fun currentSession() = session
        override suspend fun refreshSession() = session
    }

    @Test fun `switching from A to B between debt commands never sends B token`() = runTest { checkOwner("debt", false) }
    @Test fun `switching from A to B between goal commands never sends B token`() = runTest { checkOwner("goal", false) }
    @Test fun `switching from A to B between category commands never sends B token`() = runTest { checkOwner("category", false) }
    @Test fun `debt response after session switch is discarded`() = runTest { checkOwner("debt", true) }
    @Test fun `goal response after session switch is discarded`() = runTest { checkOwner("goal", true) }
    @Test fun `category response after session switch is discarded`() = runTest { checkOwner("category", true) }

    private suspend fun checkOwner(module: String, switchDuringRequest: Boolean) {
        val sessions = Sessions(AuthenticatedSession(owner, "token-A"))
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("Bearer token-A", request.headers[HttpHeaders.Authorization])
            if (switchDuringRequest) sessions.session = AuthenticatedSession(other, "token-B")
            respond(if (module == "category") """{"success":true,"status":"APPLIED"}""" else """{"status":"APPLIED"}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
        }
        try {
            val debt = DebtApi(client, sessions)
            val goals = GoalsApi(client, sessions)
            val categories = CategoriesApi(client, sessions)
            suspend fun send(): Boolean = when (module) {
                "debt" -> debt.execute(owner.toString(), "OPEN_DEBT", buildJsonObject {}) is DebtApiResponse.Success
                "goal" -> goals.execute(owner.toString(), GoalCommandType.CREATE_GOAL, buildJsonObject {}) is GoalsApiResponse.Success
                else -> categories.resolveConflict(owner.toString(), ResolveCategoryConflictRequestDto("op", "conflict", "LOCAL", "hash")) is CategoryApiResponse.Success
            }
            assertEquals(!switchDuringRequest, send())
            sessions.session = AuthenticatedSession(other, "token-B")
            assertFalse(send())
            assertEquals(1, requests)
        } finally { client.close() }
    }
}
