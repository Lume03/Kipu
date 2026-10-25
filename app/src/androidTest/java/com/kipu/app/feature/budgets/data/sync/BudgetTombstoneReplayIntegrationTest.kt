package com.kipu.app.feature.budgets.data.sync

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.SessionCoordinatorImpl
import com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepository
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetWriteResult
import com.kipu.app.feature.budgets.data.remote.BudgetApi
import com.kipu.app.feature.budgets.data.remote.BudgetRemoteDto
import com.kipu.app.feature.budgets.data.remote.BudgetPeriodRemoteDto
import com.kipu.app.feature.budgets.data.remote.BudgetTombstoneRemoteDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetTombstoneReplayIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ownerId = UUID.randomUUID().toString()
    private val budgetId = UUID.randomUUID().toString()
    private val zone = ZoneId.of("America/Lima")
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), zone)
    private lateinit var database: KipuDatabase
    private lateinit var session: SessionCoordinatorImpl
    private lateinit var repository: OfflineFirstBudgetRepository
    private lateinit var transactionRunner: DatabaseTransactionRunner

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        session = SessionCoordinatorImpl().also { it.setActiveOwner(ownerId) }
        transactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction { block() }
        }
        repository = OfflineFirstBudgetRepository(
            budgetDao = database.budgetDao(),
            alertLedgerDao = database.budgetAlertLedgerDao(),
            transactionRunner = transactionRunner,
            sessionCoordinator = session,
            syncScheduler = object : BudgetSyncScheduler {
                override fun scheduleSync(userId: String) = Unit
            },
            clock = clock,
            zoneId = zone,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun staleRemoteBudgetAndPeriodReplayCannotResurrectTombstonedBudget() = runBlocking {
        val budget = Budget(
            id = budgetId,
            ownerId = ownerId,
            name = "Restaurantes",
            scope = BudgetScope.Total,
            limitMinor = 50_000L,
            currency = "PEN",
            schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
            createdOn = LocalDate.parse("2026-10-01"),
        )
        val interval = BudgetInterval(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01"))
        val period = BudgetPeriod(
            id = BudgetPeriod.idFor(budgetId, interval),
            budgetId = budgetId,
            ownerId = ownerId,
            interval = interval,
            limitMinor = budget.limitMinor,
            scope = budget.scope,
        )
        assertEquals(BudgetWriteResult.OK, repository.create(budget, period))
        assertEquals(BudgetWriteResult.OK, repository.delete(ownerId, budgetId))
        val deletedTombstone = database.budgetDao().getTombstone(ownerId, budgetId)
        assertTrue(deletedTombstone != null)

        val staleBudget = BudgetRemoteDto(
            id = budgetId,
            userId = ownerId,
            name = "Restaurantes antiguo",
            categoryId = null,
            limitMinor = 50_000L,
            currencyCode = "PEN",
            periodType = "MONTHLY",
            rangeStartOn = null,
            rangeEndOn = null,
            alertThresholdsBps = listOf(5_000, 8_000, 10_000),
            status = "ACTIVE",
            isActive = true,
            revision = 1L,
            createdAt = clock.instant().toString(),
            updatedAt = clock.instant().toString(),
        )
        val stalePeriod = BudgetPeriodRemoteDto(
            id = UUID.randomUUID().toString(),
            userId = ownerId,
            budgetId = budgetId,
            startsOn = "2026-10-01",
            endsOn = "2026-11-01",
            limitMinor = 50_000L,
            createdAt = clock.instant().toString(),
        )
        val remoteDeletedId = UUID.randomUUID().toString()
        val remoteActiveId = UUID.randomUUID().toString()
        val offsetTimestamp = "2026-10-24T19:53:56.490936+00:00"
        val remoteTombstone = BudgetTombstoneRemoteDto(remoteDeletedId, ownerId, 2L, offsetTimestamp)
        val activeRemote = staleBudget.copy(id = remoteActiveId, createdAt = offsetTimestamp, updatedAt = offsetTimestamp)
        val activePeriod = stalePeriod.copy(id = UUID.randomUUID().toString(), budgetId = remoteActiveId, createdAt = offsetTimestamp)
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            when (request.url.encodedPath) {
                "/rest/v1/budget_tombstones" -> jsonResponse(Json.encodeToString(listOf(remoteTombstone)))
                "/rest/v1/rpc/delete_budget_v1" -> jsonResponse(
                    """{"status":"APPLIED","budget_id":"$budgetId","deleted":true}""",
                )
                "/rest/v1/budgets" -> jsonResponse(Json.encodeToString(listOf(staleBudget, activeRemote)))
                "/rest/v1/budget_periods" -> jsonResponse(Json.encodeToString(listOf(stalePeriod, activePeriod)))
                else -> error("Unexpected request; test network must remain inside MockEngine: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
        }
        try {
            val sessions = object : AuthenticatedSessionProvider {
                private val current = AuthenticatedSession(UUID.fromString(ownerId), "offline-test-token")
                override suspend fun currentSession(): AuthenticatedSession = current
                override suspend fun refreshSession(): AuthenticatedSession = current
            }
            val api = BudgetApi(client, sessions)
            val factory = object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? = if (workerClassName == SyncBudgetCommandsWorker::class.java.name) {
                    SyncBudgetCommandsWorker(
                        appContext,
                        workerParameters,
                        database.budgetDao(),
                        database.budgetAlertLedgerDao(),
                        transactionRunner,
                        api,
                        session,
                        clock,
                        zone,
                    )
                } else {
                    null
                }
            }
            val worker = TestListenableWorkerBuilder<SyncBudgetCommandsWorker>(context)
                .setInputData(workDataOf(SyncBudgetCommandsWorker.KEY_USER_ID to ownerId))
                .setWorkerFactory(factory)
                .build()

            assertTrue(worker.doWork() is ListenableWorker.Result.Success)
            assertEquals(null, repository.getBudget(ownerId, budgetId))
            assertTrue(repository.listPeriods(ownerId, budgetId).isEmpty())
            assertEquals(deletedTombstone, database.budgetDao().getTombstone(ownerId, budgetId))
            val expectedTimestamp = Instant.parse("2026-10-24T19:53:56.490936Z").toEpochMilli()
            assertEquals(expectedTimestamp, database.budgetDao().getTombstone(ownerId, remoteDeletedId)?.deletedAt)
            assertEquals(expectedTimestamp, database.budgetDao().getBudget(ownerId, remoteActiveId)?.createdAt)
            assertEquals(expectedTimestamp, database.budgetDao().listPeriods(ownerId, remoteActiveId).single().createdAt)
            assertTrue(requests.contains("/rest/v1/budgets"))
            assertTrue(requests.contains("/rest/v1/budget_periods"))
        } finally {
            client.close()
        }
    }

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
