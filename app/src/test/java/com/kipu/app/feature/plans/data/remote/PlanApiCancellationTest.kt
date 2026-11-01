package com.kipu.app.feature.plans.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.feature.plans.domain.model.BillingPurchaseRequest
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.EphemeralPurchaseToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** M-22: cancelling a plan request must stop the caller instead of becoming a retryable failure. */
class PlanApiCancellationTest {
    private fun client(failure: () -> Throwable) = HttpClient(MockEngine { throw failure() }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val session = AuthenticatedSession(UUID.randomUUID(), "jwt")
    private val purchase = BillingPurchaseRequest("premium_monthly", EphemeralPurchaseToken("token"))

    @Test fun `verify purchase propagates cancellation`() {
        val api = VerifyPurchaseApi(client { CancellationException("cancelled") }, "https://kipu.test")
        assertThrows(CancellationException::class.java) { runBlocking { api.verify(session, purchase) } }
    }

    @Test fun `verify purchase still maps transport failures to retryable`() = runTest {
        val api = VerifyPurchaseApi(client { java.io.IOException("offline") }, "https://kipu.test")
        assertEquals(BillingVerificationResult.Retryable("UNAVAILABLE"), api.verify(session, purchase))
    }

    @Test fun `plan selection propagates cancellation`() {
        val api = PlanSelectionApi(client { CancellationException("cancelled") }, "https://kipu.test")
        assertThrows(CancellationException::class.java) { runBlocking { api.eligibility("jwt") } }
    }

    @Test fun `plan selection still maps transport failures to unavailable`() = runTest {
        val api = PlanSelectionApi(client { java.io.IOException("offline") }, "https://kipu.test")
        assertTrue(api.eligibility("jwt") is ApiResult.Failure)
    }
}
