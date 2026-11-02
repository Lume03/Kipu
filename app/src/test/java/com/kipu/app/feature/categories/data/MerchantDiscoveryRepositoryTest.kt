package com.kipu.app.feature.categories.data

import com.kipu.app.core.network.*
import com.kipu.app.feature.categories.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MerchantDiscoveryRepositoryTest {
    private val owner = UUID.fromString("f0000000-0000-0000-0000-000000000001")
    private val sessions = object : AuthenticatedSessionProvider {
        override suspend fun currentSession() = AuthenticatedSession(owner, "session-token")
        override suspend fun refreshSession() = currentSession()
    }
    private fun repository(engine: MockEngine, dao: FakeMerchantCatalogDao = FakeMerchantCatalogDao()): MerchantDiscoveryRepositoryImpl {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
            expectSuccess = false
        }
        return MerchantDiscoveryRepositoryImpl(client, sessions, dao)
    }
    @Test fun `private creation inserts locally with owner and does not advance sync cursor`() = runTest {
        val dao = FakeMerchantCatalogDao()
        val repo = repository(MockEngine { req ->
            assertEquals("kipu.test", req.url.host)
            assertEquals("Bearer session-token", req.headers[HttpHeaders.Authorization])
            respond("""{"merchant":{"id":"merchant","name":"Shop","normalized_name":"shop","version":999,"user_id":"$owner"}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
        }, dao)
        val result = repo.createPrivate("Shop")
        assertEquals("merchant", result.id.value)
        assertEquals(owner.toString(), dao.merchants["merchant"]?.userId)
        assertEquals(0L, dao.merchants["merchant"]?.version)
    }
    @Test fun `internet result strips untrusted preview URL`() = runTest {
        val repo = repository(MockEngine {
            respond("""{"candidates":[{"name":"Shop","domain":"shop.test","query":"shop","logo_url":"https://evil.test/logo"}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
        })
        assertNull(repo.searchInternet("shop").single().logoUrl)
    }
    @Test fun `quota returns actionable message and inserts nothing`() = runTest {
        val dao = FakeMerchantCatalogDao()
        val repo = repository(MockEngine { respond("""{"code":"MERCHANT_RATE_LIMITED"}""",HttpStatusCode.TooManyRequests,headersOf(HttpHeaders.ContentType,"application/json")) },dao)
        val failure = runCatching { repo.createPrivate("Shop") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("l\u00edmite"))
        assertTrue(dao.merchants.isEmpty())
    }
    @Test fun `offline has clear error`() = runTest {
        val repo = repository(MockEngine { throw java.io.IOException("offline") })
        val failure = runCatching { repo.searchInternet("shop") }.exceptionOrNull()
        assertTrue(failure!!.message!!.contains("internet"))
    }
    @Test fun `response for another owner is never cached`() = runTest {
        val dao=FakeMerchantCatalogDao()
        val repo=repository(MockEngine { respond("""{"merchant":{"id":"merchant","name":"Shop","normalized_name":"shop","user_id":"other"}}""",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json")) },dao)
        assertTrue(runCatching { repo.createPrivate("Shop") }.isFailure)
        assertTrue(dao.merchants.isEmpty())
    }
}
