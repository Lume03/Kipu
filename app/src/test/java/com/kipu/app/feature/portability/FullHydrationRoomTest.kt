package com.kipu.app.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.sync.*
import com.kipu.app.feature.goals.data.sync.*
import com.kipu.app.feature.debts.data.sync.*
import com.kipu.app.feature.budgets.data.sync.*
import com.kipu.app.feature.budgets.data.remote.*
import com.kipu.app.feature.recurrences.data.sync.*
import com.kipu.app.feature.recurrences.data.remote.*
import com.kipu.app.feature.recurrences.data.repository.OfflineFirstRecurrenceRepository
import com.kipu.app.feature.categories.data.sync.*
import com.kipu.app.feature.categories.data.remote.*
import io.mockk.*
import java.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class FullHydrationRoomTest {
    private val owner = "5d88af4d-6bf2-4b75-8a43-ac7db0182fca"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KipuDatabase
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
    private val api = mockk<FinancialInstrumentsApi>()
    private val clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC)
    private val runner = object : DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = db.withTransaction { block() }
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun change(type: String, id: String, payload: String, sequence: Long = 1) =
        SyncChangeItemDto(sequence, type, id, 1L, "UPSERT", Json.parseToJsonElement(payload))
    private fun feed(vararg changes: SyncChangeItemDto) {
        coEvery { api.pullChanges(any(), any()) } answers {
            val cursor = firstArg<PullChangesRequestDto>().afterSequence
            FinancialApiResponse.Success(PullChangesResponseDto(changes.filter { it.sequence > cursor }, maxOf(cursor, changes.last().sequence)))
        }
    }
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun movementFullReplayIgnoresCheckpointAndDoesNotPostLedgerTwice() = runTest {
        feed(change("ACCOUNT", "cash", """{"id":"cash","alias":"Caja","type":"CASH","currency":"PEN","initial_balance_minor_units":0,"opened_at":"2026-11-01T00:00:00Z"}"""),
            change("TRANSACTION", "income", """{"id":"income","type":"INCOME","amount_minor":2000,"currency_code":"PEN","destination_account_id":"cash","occurred_at":"2026-11-01T00:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"entry","account_id":"cash","role":"DESTINATION","signed_amount_minor":2000,"currency_code":"PEN"}]}""", 2))
        db.movementDao().saveSyncCheckpoint(com.kipu.app.feature.movements.data.local.MovementSyncCheckpointEntity(owner, 100))
        val hydrate = MovementHydration(db.movementDao(), api, db.accountDao(), db.cardDao(), db, session)
        repeat(2) { assertTrue(hydrate.hydrateFully(owner)) }
        assertEquals(1, count("transactions")); assertEquals(1, count("ledger_entries"))
        assertEquals(2000L, db.movementDao().calculateLedgerSumForAccount(owner, "cash"))
        assertEquals(2000L, db.movementDao().getBalanceProjection(owner, "cash")?.balanceMinor)
        db.accountDao().updateAppearance(owner, "cash", "Edición local", null, null, null, 1)
        db.openHelper.writableDatabase.execSQL("INSERT INTO instrument_sync_outbox(operation_id,user_id,command_type,aggregate_type,aggregate_id,contract_version,payload_json,payload_hash,state,attempt_count,created_at,updated_at) VALUES('local',?,'UPDATE_APPEARANCE','ACCOUNT','cash',1,'{}','h','PENDING',0,1,1)", arrayOf(owner))
        assertTrue(hydrate.hydrateFully(owner))
        assertEquals("Edición local", db.accountDao().getById(owner, "cash")?.alias)
    }
    @Test fun emptyUnfinishedPageIsNotACompletedRestore() = runTest {
        coEvery { api.pullChanges(any(), any()) } returns FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 0, true))
        assertFalse(MovementHydration(db.movementDao(), api, db.accountDao(), db.cardDao(), db, session).hydrateFully(owner))
    }
    @Test fun goalsFullReplayKeepsOneGoalAndOneReserveEvent() = runTest {
        feed(change("GOAL", "goal", """{"id":"goal","user_id":"$owner","name":"Viaje","target_minor":5000,"currency_code":"PEN","status":"ACTIVE","revision":1,"created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-01T00:00:00Z"}"""),
            change("GOAL_EVENT", "event", """{"id":"event","user_id":"$owner","goal_id":"goal","event_type":"ADJUSTMENT","delta_minor":100,"currency_code":"PEN","occurred_at":"2026-11-01T00:00:00Z","created_at":"2026-11-01T00:00:00Z"}""", 2))
        val applier = GoalChangeFeedApplier(db, db.goalDao(), db.goalOutboxDao(), db.accountDao(), db.movementDao(), clock)
        val hydrate = GoalHydration(api, applier, db, mockk(relaxed = true), session)
        repeat(2) { assertTrue(hydrate.hydrateFully(owner)) }
        assertEquals(1, count("goals")); assertEquals(1, count("goal_events")); assertEquals(0, count("ledger_entries"))
    }
    @Test fun debtsFullReplayKeepsOneDebtAndReturnsHttpErrorAsFailure() = runTest {
        feed(change("DEBT", "debt", """{"id":"debt","user_id":"$owner","obligation_type":"PAYABLE","counterparty_name":"Ana","total_minor":1000,"currency_code":"PEN","opened_on":"2026-11-01","opening_mode":"HISTORICAL","status":"ACTIVE","revision":1,"created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-01T00:00:00Z"}"""))
        val applier = DebtChangeFeedApplier(db, db.debtDao(), db.movementDao(), clock)
        val hydrate = DebtHydration(db.debtDao(), api, applier, session)
        repeat(2) { assertTrue(hydrate.hydrateFully(owner)) }
        assertEquals(1, count("debts")); assertEquals(0, count("ledger_entries"))
        coEvery { api.pullChanges(any(), any()) } returns FinancialApiResponse.Error(403, null)
        assertFalse(hydrate.hydrateFully(owner))
    }
    @Test fun budgetSnapshotTwiceDoesNotDuplicateBudgetOrPeriod() = runTest {
        val remote = mockk<BudgetApi>()
        coEvery { remote.fetchTombstones(owner) } returns BudgetApiResponse.Success(emptyList())
        val budget = BudgetRemoteDto("b", owner, "Mensual", null, 10000, "PEN", "MONTHLY", null, null, listOf(8000), "ACTIVE", true, 1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z")
        coEvery { remote.fetchBudgets(owner) } returns BudgetApiResponse.Success(listOf(budget))
        coEvery { remote.fetchPeriods(owner) } returns BudgetApiResponse.Success(listOf(BudgetPeriodRemoteDto("p", owner, "b", "2026-11-01", "2026-12-01", 10000, null, "2026-11-01T00:00:00Z")))
        val hydrate = BudgetHydration(db.budgetDao(), runner, remote, ZoneOffset.UTC)
        repeat(2) { assertTrue(hydrate.hydrateFully(owner)) }
        assertEquals(1, count("budgets")); assertEquals(1, count("budget_periods")); assertEquals(0, count("ledger_entries"))
    }
    @Test fun categorySnapshotTwiceIsIdempotentAndReadFailurePropagates() = runTest {
        val remote = mockk<CategoriesApi>()
        coEvery { remote.fetchCategories() } returns CategoryApiResponse.Success(listOf(CategoryCatalogItemDto("cat", "Comida", null, owner, null, "USER", "EXPENSE", true, 1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z")))
        coEvery { remote.fetchCategoryPresentations() } returns CategoryApiResponse.Success(emptyList())
        coEvery { remote.fetchMerchantCatalog(any()) } returns CategoryApiResponse.Success(emptyList())
        val hydrate = CategoryHydration(db.categoryDao(), db.merchantCatalogDao(), context, remote)
        repeat(2) { assertTrue(hydrate.hydrateFully(owner)) }
        assertEquals(1, count("categories"))
        coEvery { remote.fetchCategories() } returns CategoryApiResponse.Error(503, null)
        try { hydrate.hydrateFully(owner); fail() } catch (_: java.io.IOException) { }
    }
}
