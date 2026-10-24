package com.kipu.app.feature.budgets.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinatorImpl
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetAlert
import com.kipu.app.feature.budgets.domain.BudgetAlertKey
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetWriteResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineFirstBudgetRepositoryRoomTest {
    private val ownerId = UUID.randomUUID().toString()
    private val budgetId = UUID.randomUUID().toString()
    private val zone = ZoneId.of("America/Lima")
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), zone)
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstBudgetRepository
    private lateinit var alertLedger: RoomBudgetAlertLedger
    private lateinit var sessionCoordinator: SessionCoordinatorImpl

    @Before
    fun setUp() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionCoordinator = SessionCoordinatorImpl().also { it.setActiveOwner(ownerId) }
        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction { block() }
        }
        repository = OfflineFirstBudgetRepository(
            budgetDao = database.budgetDao(),
            alertLedgerDao = database.budgetAlertLedgerDao(),
            transactionRunner = transactionRunner,
            sessionCoordinator = sessionCoordinator,
            syncScheduler = object : BudgetSyncScheduler { override fun scheduleSync(userId: String) = Unit },
            clock = clock,
            zoneId = zone,
        )
        alertLedger = RoomBudgetAlertLedger(database.budgetAlertLedgerDao(), sessionCoordinator, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun renewalRetryInsertsEachPeriodOnlyOnce() = runBlocking {
        val budget = budget()
        val first = period(budget, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01"))
        assertEquals(BudgetWriteResult.OK, repository.create(budget, first))

        val next = period(budget, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-01"))
        repeat(2) {
            assertEquals(BudgetWriteResult.OK, repository.applyRenewal(ownerId, null, budget.revision, listOf(next, next)))
        }

        assertEquals(2, repository.listPeriods(ownerId, budgetId).size)
    }

    @Test
    fun renewalReplayWithBudgetRevisionAndNewPeriodIsIdempotent() = runBlocking {
        val budget = budget()
        val first = period(budget, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01"))
        assertEquals(BudgetWriteResult.OK, repository.create(budget, first))

        val revised = budget.copy(limitMinor = 20_000L, revision = budget.revision + 1)
        val next = period(revised, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-01"))
        repeat(2) {
            assertEquals(
                BudgetWriteResult.OK,
                repository.applyRenewal(ownerId, revised, budget.revision, listOf(next)),
            )
        }

        assertEquals(revised, repository.getBudget(ownerId, budgetId))
        assertEquals(listOf(first.id, next.id), repository.listPeriods(ownerId, budgetId).map { it.id })
        assertEquals(4, database.budgetDao().pendingOutboxCount(ownerId))
    }

    @Test
    fun alertDedupeAndBudgetDeleteLeaveFinancialRowsUntouched() = runBlocking {
        val budget = budget()
        val period = period(budget, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01"))
        assertEquals(BudgetWriteResult.OK, repository.create(budget, period))
        val key = BudgetAlertKey(ownerId, budgetId, period.id, 8000)
        assertTrue(alertLedger.tryRecord(key))
        assertFalse(alertLedger.tryRecord(key))
        val notifier = BudgetCenterAlertNotifier(database.appNotificationDao(), sessionCoordinator, clock)
        val alert = BudgetAlert(key, budget.name, 8000, 8000, period.limitMinor, budget.currency)
        notifier.notify(alert)
        notifier.notify(alert)
        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM app_notifications WHERE user_id=? AND id=? AND notification_type='BUDGET_ALERT'",
            arrayOf(ownerId, key.dedupeKey),
        ).use { it.moveToFirst(); assertEquals(1L, it.getLong(0)) }

        val db = database.openHelper.writableDatabase
        db.execSQL("""INSERT INTO accounts (
            id,user_id,creation_operation_id,alias,type,currency,initial_balance_minor_units,
            opened_at,is_archived,remote_revision,created_at,updated_at
        ) VALUES ('account','$ownerId','account-op','Prueba','SAVINGS','PEN',0,1000,0,1,1000,1000)""")
        db.execSQL("""INSERT INTO transactions (
            id,user_id,type,amount_minor,currency_code,source_account_id,operation_kind,
            occurred_at,status,sync_status,created_at,updated_at
        ) VALUES ('movement','$ownerId','EXPENSE',1250,'PEN','account','STANDARD',2000,'ACTIVE','SYNCED',2000,2000)""")
        db.execSQL("""INSERT INTO ledger_entries (
            id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at
        ) VALUES ('entry','$ownerId','movement','account','SOURCE',-1250,'PEN',2000)""")

        assertEquals(BudgetWriteResult.OK, repository.delete(ownerId, budgetId))

        assertNull(repository.getBudget(ownerId, budgetId))
        assertEquals(BudgetWriteResult.ALREADY_EXISTS, repository.create(budget, period))
        assertTrue(repository.listPeriods(ownerId, budgetId).isEmpty())
        assertEquals(0, database.budgetAlertLedgerDao().countForBudget(ownerId, budgetId))
        assertEquals(budget.revision, database.budgetDao().getTombstone(ownerId, budgetId)?.lastRevision)
        assertEquals(1, database.budgetDao().pendingOutboxCount(ownerId))
        db.query("SELECT COUNT(*) FROM accounts WHERE id='account'").use { it.moveToFirst(); assertEquals(1L, it.getLong(0)) }
        db.query("SELECT COUNT(*) FROM transactions WHERE id='movement'").use { it.moveToFirst(); assertEquals(1L, it.getLong(0)) }
        db.query("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE id='entry'").use {
            it.moveToFirst(); assertEquals(-1250L, it.getLong(0))
        }
    }

    private fun budget() = Budget(
        id = budgetId,
        ownerId = ownerId,
        name = "Comida",
        scope = BudgetScope.Category("category-id"),
        limitMinor = 100_00L,
        currency = "PEN",
        schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
        createdOn = LocalDate.parse("2026-10-01"),
    )

    private fun period(budget: Budget, start: LocalDate, end: LocalDate) = BudgetPeriod(
        id = BudgetPeriod.idFor(budget.id, BudgetInterval(start, end)),
        budgetId = budget.id,
        ownerId = ownerId,
        interval = BudgetInterval(start, end),
        limitMinor = budget.limitMinor,
        scope = budget.scope,
    )
}
