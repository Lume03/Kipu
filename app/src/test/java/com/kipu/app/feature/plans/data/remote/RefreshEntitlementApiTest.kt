package com.kipu.app.feature.plans.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshEntitlementApiTest {
    @Test
    fun `refresh posts only installation public key with current bearer and parses grant response`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/functions/v1/refresh-entitlement", request.url.encodedPath)
            assertEquals("Bearer session-jwt", request.headers[HttpHeaders.Authorization])
            assertEquals(
                setOf("installationPublicKey"),
                Json.parseToJsonElement((request.body as TextContent).text).jsonObject.keys,
            )
            assertEquals("spki-key", Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                .getValue("installationPublicKey").jsonPrimitive.content)
            respond(
                """{"effectiveTier":"PREMIUM","source":"TEST_OVERRIDE","expiresAt":null,"offlineEntitlementGrant":{"payload":"p","signature":"s","keyId":"k"}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val result = RefreshEntitlementApi(client, "https://kipu.test/").refresh(
                AuthenticatedSession(UUID.randomUUID(), "session-jwt"), "spki-key",
            )
            assertTrue(result is RefreshEntitlementApiResult.Success)
            val response = (result as RefreshEntitlementApiResult.Success).value
            assertEquals("TEST_OVERRIDE", response.source)
            assertEquals("k", response.offlineEntitlementGrant?.keyId)
        } finally {
            client.close()
        }
    }

    @Test
    fun `403 identifies disabled test plans`() = runTest {
        val client = client(MockEngine {
            respond("""{"code":"TEST_PLANS_DISABLED"}""", HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = RefreshEntitlementApi(client, "https://kipu.test").refresh(
                AuthenticatedSession(UUID.randomUUID(), "jwt"), "key",
            )
            assertEquals("TEST_PLANS_DISABLED", (result as RefreshEntitlementApiResult.Failure).code)
        } finally {
            client.close()
        }
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
    }
}
