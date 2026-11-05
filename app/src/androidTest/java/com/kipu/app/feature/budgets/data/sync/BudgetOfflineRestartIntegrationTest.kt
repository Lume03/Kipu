package com.kipu.app.feature.budgets.data.sync

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
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
import com.kipu.app.feature.budgets.data.remote.BudgetApi
import com.kipu.app.feature.budgets.data.remote.BudgetSyncPayload
import com.kipu.app.feature.budgets.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BudgetOfflineRestartIntegrationTest {
    @Test
    fun offlineCreationSurvivesNewRoomInstanceAndSyncRetryWithoutLossOrDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "budget-restart-${UUID.randomUUID()}"
        val owner = UUID.randomUUID().toString()
        val id = UUID.randomUUID().toString()
        val zone = ZoneId.of("America/Lima")
        val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), zone)
        var database = Room.databaseBuilder(context, KipuDatabase::class.java, databaseName).build()
        val session = SessionCoordinatorImpl().also { it.setActiveOwner(owner) }
        val runner = object : DatabaseTransactionRunner {
            override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction { block() }
        }
        fun repository() = OfflineFirstBudgetRepository(database.budgetDao(), database.budgetAlertLedgerDao(), runner,
            session, object : BudgetSyncScheduler { override fun scheduleSync(userId: String) = Unit }, clock, zone)
        val budget = Budget(id, owner, "Offline", BudgetScope.Total, 50_000, "PEN",
            BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY), createdOn = LocalDate.parse("2026-11-05"))
        val interval = BudgetInterval(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-01"))
        val period = BudgetPeriod(BudgetPeriod.idFor(id, interval), id, owner, interval, budget.limitMinor, budget.scope)
        var client: HttpClient? = null
        try {
            assertEquals(BudgetWriteResult.OK, repository().create(budget, period))
            val commandsBefore = database.budgetDao().allOutbox(owner)
            assertEquals(2, commandsBefore.size)
            val payloads = commandsBefore.map { Json.decodeFromString<BudgetSyncPayload>(it.payloadJson) }
            val remoteBudget = payloads.firstNotNullOf { it.budget }
            val remotePeriod = payloads.firstNotNullOf { it.period }
            database.close()
            database = Room.databaseBuilder(context, KipuDatabase::class.java, databaseName).build()
            val restarted = repository()
            assertEquals(budget, restarted.getBudget(owner, id))
            assertEquals(listOf(period), restarted.listPeriods(owner, id))
            assertEquals(commandsBefore, database.budgetDao().allOutbox(owner))
            val writes = mutableListOf<String>()
            val wire = Json { ignoreUnknownKeys = true }
            val engine = MockEngine { request ->
                if (request.method.value == "POST") writes += request.url.encodedPath
                val body = when (request.url.encodedPath) {
                    "/rest/v1/budget_tombstones" -> "[]"
                    "/rest/v1/budgets" -> wire.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.kipu.app.feature.budgets.data.remote.BudgetRemoteDto.serializer()), listOf(remoteBudget))
                    "/rest/v1/budget_periods" -> wire.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.kipu.app.feature.budgets.data.remote.BudgetPeriodRemoteDto.serializer()), listOf(remotePeriod))
                    else -> error("Unexpected request: ${request.url}")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val http = HttpClient(engine) {
                install(ContentNegotiation) { json(wire) }
                defaultRequest { url("https://kipu.test/") }
            }
            client = http
            val provider = object : AuthenticatedSessionProvider {
                override suspend fun currentSession() = AuthenticatedSession(UUID.fromString(owner), "test-token")
                override suspend fun refreshSession() = currentSession()
            }
            val factory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    SyncBudgetCommandsWorker(appContext, workerParameters, database.budgetDao(), database.budgetAlertLedgerDao(),
                        runner, BudgetApi(http, provider), session, clock, zone)
            }
            repeat(2) {
                val worker = TestListenableWorkerBuilder<SyncBudgetCommandsWorker>(context)
                    .setInputData(workDataOf(SyncBudgetCommandsWorker.KEY_USER_ID to owner)).setWorkerFactory(factory).build()
                assertTrue(worker.doWork() is ListenableWorker.Result.Success)
            }
            assertEquals(listOf("/rest/v1/budgets", "/rest/v1/budget_periods"), writes)
            assertEquals(0, database.budgetDao().pendingOutboxCount(owner))
            assertEquals(budget, restarted.getBudget(owner, id))
            assertEquals(listOf(period.id), restarted.listPeriods(owner, id).map { it.id })
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM budgets WHERE user_id=?", arrayOf(owner)).use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        } finally {
            client?.close()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
