package com.kipu.app.feature.accounts.data.sync

import android.app.Application
import androidx.room.Room
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.SessionCoordinatorImpl
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class InstrumentOwnerIsolationTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"
    private fun command(id: String) = InstrumentSyncOutboxEntity(
        operationId = id, userId = owner, commandType = "SET_ARCHIVED", aggregateType = "ACCOUNT", aggregateId = id,
        payloadJson = Json.encodeToString(SetArchivedRequestDto(id, id, "ACCOUNT", true)), payloadHash = "hash",
        createdAt = 1, updatedAt = 1,
    )
    private suspend fun fixture(test: suspend (KipuDatabase, SessionCoordinatorImpl, WorkerParameters) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val coordinator = SessionCoordinatorImpl()
            coordinator.setActiveOwner(owner)
            val params = mockk<WorkerParameters>(relaxed = true)
            every { params.inputData } returns workDataOf(SyncInstrumentCommandsWorker.KEY_USER_ID to owner)
            db.instrumentSyncDao().insert(command("1"))
            db.instrumentSyncDao().insert(command("2"))
            test(db, coordinator, params)
        } finally { db.close() }
    }

    @Test fun `owner switches after first request so no B token is sent and no A response is acknowledged`() = runBlocking {
        fixture { db, coordinator, params ->
            val sent = mutableListOf<String?>()
            val sessions = object : AuthenticatedSessionProvider {
                override suspend fun currentSession() = AuthenticatedSession(UUID.fromString(coordinator.currentOwner!!.verifiedUserId),
                    if (coordinator.currentOwner!!.verifiedUserId == owner) "token-A" else "token-B")
                override suspend fun refreshSession() = currentSession()
            }
            val client = HttpClient(MockEngine { request ->
                sent += request.headers[HttpHeaders.Authorization]
                coordinator.setActiveOwner(other)
                respond("""{"status":"APPLIED"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
                install(ContentNegotiation) { json() }
                defaultRequest { url("https://kipu.test/") }
            }
            try {
                val worker = SyncInstrumentCommandsWorker(RuntimeEnvironment.getApplication(), params, db.instrumentSyncDao(),
                    db.cardDao(), db.accountDao(), db, FinancialInstrumentsApi(client, sessions), db.movementDao(), coordinator)
                assertTrue(runCatching { worker.doWork() }.exceptionOrNull() is CancellationException)
                assertEquals(listOf("Bearer token-A"), sent)
                assertEquals("IN_FLIGHT", db.instrumentSyncDao().getByOperationId(owner, "1")!!.state)
                assertEquals("PENDING", db.instrumentSyncDao().getByOperationId(owner, "2")!!.state)
            } finally { client.close() }
        }
    }

    @Test fun `owner switch inside acknowledgment rolls back Room confirmation`() = runBlocking {
        fixture { db, coordinator, params ->
            val dao = spyk(db.instrumentSyncDao())
            coEvery { dao.markSynced(owner, any(), any()) } coAnswers {
                db.instrumentSyncDao().markSynced(firstArg(), secondArg(), thirdArg())
                coordinator.setActiveOwner(other)
                1
            }
            val api = mockk<FinancialInstrumentsApi>()
            coEvery { api.setArchived(any(), owner) } returns FinancialApiResponse.Success(CommandResponseDto("APPLIED"))
            val worker = SyncInstrumentCommandsWorker(RuntimeEnvironment.getApplication(), params, dao,
                db.cardDao(), db.accountDao(), db, api, db.movementDao(), coordinator)
            assertTrue(runCatching { worker.doWork() }.exceptionOrNull() is CancellationException)
            assertEquals("IN_FLIGHT", db.instrumentSyncDao().getByOperationId(owner, "1")!!.state)
            assertEquals("PENDING", db.instrumentSyncDao().getByOperationId(owner, "2")!!.state)
            coVerify(exactly = 1) { api.setArchived(any(), owner) }
        }
    }

    @Test fun `all instrument commands reject mismatched session without HTTP`() = runBlocking {
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession() = AuthenticatedSession(UUID.fromString(other), "token-B")
            override suspend fun refreshSession() = currentSession()
        }
        val client = HttpClient(MockEngine { error("No B request is allowed") })
        try {
            val api = FinancialInstrumentsApi(client, sessions)
            val results = listOf(
                api.createAccount(mockk(), owner), api.registerCard(mockk(), owner),
                api.updateAppearance(mockk(), owner), api.setArchived(mockk(), owner),
                api.deleteUnusedCard(mockk(), owner), api.recordOpeningAdjustment(mockk(), owner),
                api.registerCreditPurchase(mockk(), owner), api.allocateCreditPayment(mockk(), owner),
                api.updatePersonalTea(mockk(), owner), api.updateCreditCardTerms(mockk(), owner),
            )
            results.forEach { assertEquals(401, (it as FinancialApiResponse.Error).statusCode) }
        } finally { client.close() }
    }
}
