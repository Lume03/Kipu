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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class DevPlanApiTest {
    private val owner = AuthenticatedSession(UUID.randomUUID(),"session-jwt")

    @Test fun `mode sends bearer to RPC and reads a server boolean`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/dev_plan_mode", request.url.encodedPath)
            assertEquals("Bearer session-jwt",request.headers[HttpHeaders.Authorization])
            assertEquals("{}",(request.body as TextContent).text)
            respond("true",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try { assertTrue(DevPlanApi(client,"https://kipu.test").mode(owner)) }
        finally { client.close() }
    }

    @Test fun `activation sends only stable operation and plan with no owner parameter`() = runTest {
        val operation = UUID.randomUUID()
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/dev_activate_plan",request.url.encodedPath)
            assertEquals("Bearer session-jwt",request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("p_operation_id","p_plan_type"),body.keys)
            assertEquals(operation.toString(),body.getValue("p_operation_id").jsonPrimitive.content)
            assertEquals("PRO_LIFETIME",body.getValue("p_plan_type").jsonPrimitive.content)
            respond("""{"outcome":"APPLIED","planType":"PRO_LIFETIME","operationId":"$operation","expiresAt":null}""",
                HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val response = DevPlanApi(client,"https://kipu.test").activate(owner,operation,"PRO_LIFETIME")
            assertEquals("APPLIED",response.outcome)
            assertNull(response.expiresAt)
        } finally { client.close() }
    }

    @Test fun `mode errors cannot silently select a charging flow`() = runTest {
        val client = client(MockEngine { respond("{}",HttpStatusCode.ServiceUnavailable) })
        try {
            try { DevPlanApi(client,"https://kipu.test").mode(owner); fail("mode must remain unavailable") }
            catch (_: IllegalStateException) { }
        } finally { client.close() }
    }

    @Test fun `activation cancellation propagates through HTTP boundary`() = runTest {
        val client = client(MockEngine { throw CancellationException("cancel") })
        try {
            try { DevPlanApi(client,"https://kipu.test").activate(owner,UUID.randomUUID(),"FREE"); fail("must cancel") }
            catch (_: CancellationException) { }
        } finally { client.close() }
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}
