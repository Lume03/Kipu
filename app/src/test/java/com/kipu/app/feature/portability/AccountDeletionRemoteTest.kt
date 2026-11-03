package com.kipu.app.feature.portability
import com.kipu.app.core.network.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import org.junit.Test
import org.junit.Assert.*
class AccountDeletionRemoteTest {
    @Test fun concurrentRequestIsRetryableOnClient() = runTest {
        val owner=UUID.randomUUID()
        val auth=mockk<AuthenticatedSessionProvider>()
        coEvery { auth.currentSession() } returns AuthenticatedSession(owner,"token")
        val http=HttpClient(MockEngine { respond("""{"status":"in_progress"}""",HttpStatusCode.Accepted,headersOf(HttpHeaders.ContentType,"application/json")) }) {
            install(ContentNegotiation) { json() }; defaultRequest { url("https://kipu.test/") }
        }
        try {
            val gateway=RemoteAccountDeletion(mockk(),mockk(),mockk(),auth,http)
            assertEquals(DeletionRemote.PENDING,gateway.delete(DeletionRequest(owner.toString(),UUID.randomUUID().toString(),"a".repeat(64))))
        } finally { http.close() }
    }
}
