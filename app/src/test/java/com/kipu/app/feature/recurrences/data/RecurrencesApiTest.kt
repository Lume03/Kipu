package com.kipu.app.feature.recurrences.data

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.recurrences.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RecurrencesApiTest {
    private val owner = "61000000-0000-4000-8000-000000000001"
    private val session = mockk<AuthenticatedSessionProvider> {
        coEvery { currentSession() } returns AuthenticatedSession(UUID.fromString(owner), "token")
    }

    @Test fun successful2xxDecodesRpcAndPreservesLongAmounts() = runTest {
        val client = client { request ->
            assertEquals("/rest/v1/rpc/create_recurrence_v1", request.url.encodedPath)
            assertTrue(request.body.toByteArray().decodeToString().contains("9223372036854775807"))
            respond("""{"status":"APPLIED","recurrence_id":"r","revision":1}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = RecurrencesApi(client, session).execute(owner, "CREATE_RECURRENCE", buildJsonObject { put("amount_minor", Long.MAX_VALUE) })
        assertEquals(1L, (result as RecurrencesApiResponse.Success).data.revision)
        client.close()
    }

    @Test fun httpErrorsAreNotDecodedAsSuccessfulReceipts() = runTest {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.Conflict, HttpStatusCode.InternalServerError)) {
            val client = client { respond("not JSON", status) }
            val result = RecurrencesApi(client, session).execute(owner, "UPDATE_RECURRENCE", buildJsonObject {})
            assertEquals(status.value, (result as RecurrencesApiResponse.Error).statusCode)
            client.close()
        }
    }

    @Test fun changedAuthenticatedOwnerDoesNotReachNetwork() = runTest {
        val client = client { error("Must not send") }
        val result = RecurrencesApi(client, session).execute(UUID.randomUUID().toString(), "SET_RECURRENCE_STATUS", buildJsonObject {})
        assertEquals(401, (result as RecurrencesApiResponse.Error).statusCode)
        client.close()
    }

    @Test fun cancellationPropagates() = runTest {
        coEvery { session.currentSession() } throws CancellationException("cancel")
        val client = client { error("Must not send") }
        try {
            RecurrencesApi(client, session).execute(owner, "CREATE_RECURRENCE", buildJsonObject {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { } finally { client.close() }
    }

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://test.invalid/") }
        }
}
