package com.kipu.app.feature.movements.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementApiTest {
    @Test fun `sync command refuses credentials for another owner before HTTP`() = runTest {
        val http = HttpClient(MockEngine { error("Wrong-owner request must never be dispatched") })
        try {
            val api = MovementApi(http, Sessions())
            assertEquals(401, (api.registerTransaction(io.mockk.mockk(), "owner-A") as MovementApiResponse.Error).statusCode)
            assertEquals(401, (api.registerRefund(io.mockk.mockk(), "owner-A") as MovementApiResponse.Error).statusCode)
            assertEquals(401, (api.reviseOrVoidTransaction(io.mockk.mockk(), "owner-A") as MovementApiResponse.Error).statusCode)
        } finally { http.close() }
    }

    @Test
    fun `refund uses its dedicated versioned rpc and preserves the refund request contract`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/refund_transaction_v1", request.url.encodedPath)
            val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                .getValue("p_command").jsonObject
            assertEquals("1", command.getValue("contract_version").jsonPrimitive.content)
            assertEquals("refund-key", command.getValue("idempotency_key").jsonPrimitive.content)
            assertEquals("refund-hash", command.getValue("request_hash").jsonPrimitive.content)
            assertEquals("purchase-id", command.getValue("purchase_transaction_id").jsonPrimitive.content)
            val refund = command.getValue("refund_transaction").jsonObject
            assertEquals("refund-id", refund.getValue("id").jsonPrimitive.content)
            assertEquals("3500", refund.getValue("amount_minor").jsonPrimitive.content)
            assertEquals("PEN", refund.getValue("currency_code").jsonPrimitive.content)
            assertEquals("2026-10-24T12:30:00Z", refund.getValue("occurred_at").jsonPrimitive.content)
            assertEquals(setOf("contract_version", "idempotency_key", "request_hash", "purchase_transaction_id", "refund_transaction"), command.keys)
            respond("""{"status":"REJECTED","error":{"code":"REFUND_EXCEEDS_REMAINDER","retryable":false}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = MovementApi(client, Sessions()).registerRefund(
                RefundTransactionRequestDto(
                    idempotencyKey = "refund-key",
                    requestHash = "refund-hash",
                    purchaseTransactionId = "purchase-id",
                    refundTransaction = RefundTransactionDto(
                        id = "refund-id",
                        amountMinor = 3_500L,
                        currencyCode = "PEN",
                        occurredAt = "2026-10-24T12:30:00Z",
                    ),
                ),
            )
            assertEquals("REFUND_EXCEEDS_REMAINDER", (result as MovementApiResponse.Success).data.error?.code)
        } finally { client.close() }
    }

    @Test
    fun `expired session refresh preserves pending movement identity`() = runTest {
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession(): AuthenticatedSession? = null
            override suspend fun refreshSession() = AuthenticatedSession(UUID.randomUUID(), "renewed-token")
        }
        val client = client(MockEngine { request ->
            assertEquals("Bearer renewed-token", request.headers[HttpHeaders.Authorization])
            val payload = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("p_command").jsonObject
            assertEquals("original-key", payload.getValue("idempotency_key").jsonPrimitive.content)
            respond("""{"status":"APPLIED"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertTrue(MovementApi(client, sessions).registerTransaction(command(2)) is MovementApiResponse.Success)
        } finally { client.close() }
    }
    @Test
    fun `persisted v1 and new v2 use their own RPC without altering identity or hash`() = runTest {
        for (version in listOf(1, 2)) {
            val client = client(MockEngine { request ->
                assertEquals("/rest/v1/rpc/register_transaction_v$version", request.url.encodedPath)
                val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    .getValue("p_command").jsonObject
                assertEquals("original-key", command.getValue("idempotency_key").jsonPrimitive.content)
                assertEquals("original-hash", command.getValue("request_hash").jsonPrimitive.content)
                assertEquals("old note;with:delimiters", command.getValue("transaction").jsonObject
                    .getValue("note").jsonPrimitive.content)
                respond("""{"status":"APPLIED"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            })
            try {
                assertTrue(MovementApi(client, Sessions()).registerTransaction(command(version)) is MovementApiResponse.Success)
            } finally { client.close() }
        }
    }

    @Test
    fun `unsupported contract does not invoke HTTP`() = runTest {
        var calls = 0
        val client = client(MockEngine { calls++; error("Unexpected HTTP request") })
        try {
            val response = MovementApi(client, Sessions()).registerTransaction(command(3))
            assertEquals(400, (response as MovementApiResponse.Error).statusCode)
            assertEquals(0, calls)
        } finally { client.close() }
    }

    @Test
    fun `cancellation from session is propagated`() = runTest {
        val client = client(MockEngine { error("Unexpected HTTP request") })
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession(): AuthenticatedSession? = throw CancellationException("cancelled")
            override suspend fun refreshSession(): AuthenticatedSession? = null
        }
        try {
            var cancelled = false
            try { MovementApi(client, sessions).registerTransaction(command(1)) }
            catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
        } finally { client.close() }
    }

    @Test
    fun `revise transaction posts to revise_transaction_v1 and parses result`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/revise_transaction_v1", request.url.encodedPath)
            val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                .getValue("p_command").jsonObject
            assertEquals("cmd-revise", command.getValue("idempotency_key").jsonPrimitive.content)
            respond("""{"status":"APPLIED","result":"REVISED","resulting_revision":2}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val req = ReviseOrVoidTransactionRequestDto(
                contractVersion = 1,
                commandType = "REVISE_TRANSACTION",
                idempotencyKey = "cmd-revise",
                transactionId = "tx-1",
                expectedRevision = 1L,
                requestHash = "hash-1",
                revisedPayload = RevisedMovementPayloadDto(
                    type = "EXPENSE",
                    amountMinor = 1500L,
                    currencyCode = "PEN",
                    sourceAccountId = "acc-1",
                    occurredAt = "2026-10-02T12:00:00Z",
                ),
            )
            val resp = MovementApi(client, Sessions()).reviseOrVoidTransaction(req)
            assertTrue(resp is MovementApiResponse.Success)
            assertEquals("APPLIED", (resp as MovementApiResponse.Success).data.status)
            assertEquals("REVISED", resp.data.result)
            assertEquals(2L, resp.data.resultingRevision)
        } finally { client.close() }
    }

    @Test
    fun `void transaction posts to void_transaction_v1 and parses result`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/void_transaction_v1", request.url.encodedPath)
            val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                .getValue("p_command").jsonObject
            assertEquals("cmd-void", command.getValue("idempotency_key").jsonPrimitive.content)
            assertEquals("1", command.getValue("contract_version").jsonPrimitive.content)
            assertEquals(kotlinx.serialization.json.JsonNull, command.getValue("revised_payload"))
            assertEquals(kotlinx.serialization.json.JsonNull, command.getValue("depends_on_command_id"))
            respond("""{"status":"APPLIED","result":"VOIDED","resulting_revision":3}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val req = ReviseOrVoidTransactionRequestDto(
                contractVersion = 1,
                commandType = "VOID_TRANSACTION",
                idempotencyKey = "cmd-void",
                transactionId = "tx-1",
                expectedRevision = 2L,
                requestHash = "hash-void",
                revisedPayload = null,
            )
            val resp = MovementApi(client, Sessions()).reviseOrVoidTransaction(req)
            assertTrue(resp is MovementApiResponse.Success)
            assertEquals("APPLIED", (resp as MovementApiResponse.Success).data.status)
            assertEquals("VOIDED", resp.data.result)
            assertEquals(3L, resp.data.resultingRevision)
        } finally { client.close() }
    }

    private fun command(version: Int) = RegisterTransactionRequestDto(
        contractVersion = version, idempotencyKey = "original-key", requestHash = "original-hash",
        transaction = TransactionDto(id = "transaction-id", type = "INCOME", amountMinor = 100L,
            currencyCode = "PEN", sourceAccountId = "account-id", occurredAt = "2026-10-02T00:00:00Z",
            note = "old note;with:delimiters"),
    )

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { encodeDefaults = false }) }
        defaultRequest { url("https://kipu.test/") }
    }

    private class Sessions : AuthenticatedSessionProvider {
        private val session = AuthenticatedSession(UUID.randomUUID(), "test-token")
        override suspend fun currentSession(): AuthenticatedSession = session
        override suspend fun refreshSession(): AuthenticatedSession = session
    }
}
