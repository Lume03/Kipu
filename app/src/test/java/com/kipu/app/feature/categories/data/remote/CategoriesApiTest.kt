package com.kipu.app.feature.categories.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesApiTest {
    private val postgrestError = """{"code":"23505","details":null,"hint":null,"message":"duplicate key value"}"""

    private fun api(engine: MockEngine): CategoriesApi {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
            expectSuccess = false
        }
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession() = AuthenticatedSession(UUID.randomUUID(), "token")
            override suspend fun refreshSession() = currentSession()
        }
        return CategoriesApi(client, sessions)
    }

    private fun request() = ResolveCategoryConflictRequestDto(
        operationId = "op-1", conflictId = "conflict-1", chosenVersion = "LOCAL", payloadHash = "h",
    )

    @Test fun `non 2xx rpc responses are errors and never decoded as success`() = runTest {
        for (status in listOf(400, 401, 409, 429, 500)) {
            val result = api(MockEngine {
                respond(postgrestError, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
            }).resolveConflict(request())
            assertTrue("status $status", result is CategoryApiResponse.Error)
            assertEquals(status, (result as CategoryApiResponse.Error).statusCode)
        }
    }

    @Test fun `non 2xx fetches are errors`() = runTest {
        val api = api(MockEngine { respond("""{"message":"JWT expired"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json")) })
        assertTrue(api.fetchCategories() is CategoryApiResponse.Error)
        assertTrue(api.fetchCategoryPresentations() is CategoryApiResponse.Error)
        assertTrue(api.fetchMerchantCatalog() is CategoryApiResponse.Error)
    }

    @Test fun `2xx rpc response without success flag is not a success`() = runTest {
        val result = api(MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
            .resolveConflict(request())
        assertFalse(((result as CategoryApiResponse.Success).data).success)
    }

    @Test fun `2xx rpc response keeps the server flag`() = runTest {
        val result = api(MockEngine { respond("""{"success":true,"status":"APPLIED"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
            .resolveConflict(request())
        assertTrue((result as CategoryApiResponse.Success).data.success)
    }

    @Test fun `cancellation during the request is propagated`() = runTest {
        val api = api(MockEngine { throw CancellationException("cancelled") })
        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { api.resolveConflict(request()) } }
        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { api.fetchCategories() } }
    }

    @Test fun `ordinary transport failure is a network failure`() = runTest {
        val result = api(MockEngine { throw java.io.IOException("offline") }).resolveConflict(request())
        assertTrue(result is CategoryApiResponse.NetworkFailure)
    }
}
