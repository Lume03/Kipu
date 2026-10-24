package com.kipu.app.feature.movements

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinatorImpl
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.budgets.data.BudgetCenterAlertNotifier
import com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepository
import com.kipu.app.feature.budgets.data.RoomBudgetAlertLedger
import com.kipu.app.feature.budgets.data.RoomBudgetCategoryLookup
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetWriteResult
import com.kipu.app.feature.budgets.domain.EvaluateBudgetAlerts
import com.kipu.app.feature.budgets.domain.ExpenseBudgetConsumptionReader
import com.kipu.app.feature.budgets.domain.RenewBudgetPeriods
import com.kipu.app.feature.budgets.domain.RunBudgetMaintenance
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.RegisterRefundTransaction
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionError
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementRefundBudgetIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ownerId = UUID.randomUUID().toString()
    private val purchaseId = UUID.randomUUID().toString()
    private val accountId = UUID.randomUUID().toString()
    private val budgetId = UUID.randomUUID().toString()
    private val zone = ZoneId.of("America/Lima")
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), zone)
    private lateinit var database: KipuDatabase
    private lateinit var movementSource: MovementLocalDataSource
    private lateinit var budgetRepository: OfflineFirstBudgetRepository
    private lateinit var reader: ExpenseBudgetConsumptionReader
    private lateinit var session: SessionCoordinatorImpl

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val movementDao = database.movementDao()
        movementSource = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))

        session = SessionCoordinatorImpl().also { it.setActiveOwner(ownerId) }
        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction { block() }
        }
        budgetRepository = OfflineFirstBudgetRepository(
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
        reader = ExpenseBudgetConsumptionReader(
            expenses = object : ExpenseConsumptionRepository {
                override suspend fun queryConsumption(userId: String, query: ExpenseConsumptionQuery) =
                    movementSource.queryExpenseConsumption(userId, query)
            },
            categories = RoomBudgetCategoryLookup(database.categoryDao()),
        )

        database.accountDao().insert(
            AccountEntity(
                id = accountId,
                userId = ownerId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Efectivo",
                type = "CASH",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = at("2026-09-01"),
                createdAt = at("2026-09-01"),
                updatedAt = at("2026-09-01"),
            ),
        )
        database.categoryDao().insertCategories(
            listOf(
                CategoryEntity("food", ownerId, null, "CUSTOM", createdAt = 1L, updatedAt = 1L),
                CategoryEntity("restaurants", ownerId, "food", "CUSTOM", createdAt = 2L, updatedAt = 2L),
            ),
        )
        database.movementDao().insertTransaction(
            TransactionEntity(
                id = purchaseId,
                userId = ownerId,
                type = "EXPENSE",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                sourceAccountId = accountId,
                categoryId = "restaurants",
                occurredAt = at("2026-09-15"),
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                createdAt = at("2026-09-15"),
                updatedAt = at("2026-09-15"),
                operationKind = "STANDARD",
            ),
        )
        database.movementDao().insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = ownerId,
                    transactionId = purchaseId,
                    accountId = accountId,
                    role = "SOURCE",
                    signedAmountMinor = -10_000L,
                    currencyCode = "PEN",
                    createdAt = at("2026-09-15"),
                ),
            ),
        )

        val budget = Budget(
            id = budgetId,
            ownerId = ownerId,
            name = "Restaurantes",
            scope = BudgetScope.Category("restaurants"),
            limitMinor = 50_000L,
            currency = "PEN",
            schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
            thresholdsBps = listOf(2_000),
            createdOn = LocalDate.parse("2026-09-01"),
        )
        val september = period(budget, "2026-09-01", "2026-10-01")
        assertEquals(BudgetWriteResult.OK, budgetRepository.create(budget, september))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun crossMonthRefundUpdatesOriginalBudgetPeriodAndCashWithoutMutatingPurchaseOrDuplicatingAlert() = runBlocking {
        val alertLedger = RoomBudgetAlertLedger(database.budgetAlertLedgerDao(), session, clock)
        val notifier = BudgetCenterAlertNotifier(database.appNotificationDao(), session, clock)
        val evaluateAlerts = EvaluateBudgetAlerts(budgetRepository, reader, alertLedger, notifier)
        val maintenance = RunBudgetMaintenance(budgetRepository, RenewBudgetPeriods(budgetRepository), evaluateAlerts)
        val purchasePeriodResult = maintenance(ownerId, LocalDate.parse("2026-09-30"), zone)
        assertEquals(listOf(2_000), purchasePeriodResult.alerts.map { it.key.thresholdBps })
        assertTrue(maintenance(ownerId, LocalDate.parse("2026-09-30"), zone).alerts.isEmpty())
        assertEquals(1, activeBudgetAlertCount())

        maintenance(ownerId, LocalDate.parse("2026-10-01"), zone)
        maintenance(ownerId, LocalDate.parse("2026-10-01"), zone)
        assertEquals(2, budgetRepository.listPeriods(ownerId, budgetId).size)
        assertEquals(1, activeBudgetAlertCount())

        val purchaseBefore = database.movementDao().getTransactionById(ownerId, purchaseId)
        val originalLedgerBefore = database.movementDao().getLedgerEntriesForTransaction(ownerId, purchaseId)
        assertEquals(-10_000L, database.movementDao().calculateLedgerSumForAccount(ownerId, accountId))
        assertEquals(10_000L, consumed("2026-09-01", "2026-10-01"))

        val firstRefund = refund(amountMinor = 4_000L, refundId = "refund-partial", key = "refund-key-partial")
        assertTrue(firstRefund is RefundTransactionResult.Success)
        assertEquals(-6_000L, database.movementDao().calculateLedgerSumForAccount(ownerId, accountId))
        assertEquals(6_000L, consumed("2026-09-01", "2026-10-01"))
        assertEquals(0L, consumed("2026-10-01", "2026-11-01"))

        val replay = RegisterRefundTransaction(movementSource)(
            RefundTransactionCommand(
                idempotencyKey = "refund-key-partial",
                userId = ownerId,
                purchaseTransactionId = purchaseId,
                refundTransactionId = "refund-partial",
                amountMinor = 4_000L,
                currency = "PEN",
                occurredAt = at("2026-10-10"),
            ),
        )
        assertTrue(replay is RefundTransactionResult.Success && replay.isDuplicate)
        assertEquals(-6_000L, database.movementDao().calculateLedgerSumForAccount(ownerId, accountId))

        val excess = refund(amountMinor = 7_000L, refundId = "refund-excess", key = "refund-key-excess")
        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.REFUND_EXCEEDS_REMAINDER, 6_000L),
            excess,
        )
        assertEquals(null, database.movementDao().getTransactionById(ownerId, "refund-excess"))
        assertEquals(-6_000L, database.movementDao().calculateLedgerSumForAccount(ownerId, accountId))

        assertTrue(refund(amountMinor = 6_000L, refundId = "refund-complete", key = "refund-key-complete") is RefundTransactionResult.Success)
        assertEquals(10_000L, movementSource.getRefundedAmount(ownerId, purchaseId))
        assertEquals(0L, consumed("2026-09-01", "2026-10-01"))
        assertEquals(0L, consumed("2026-10-01", "2026-11-01"))
        assertEquals(0L, database.movementDao().calculateLedgerSumForAccount(ownerId, accountId))
        assertEquals(purchaseBefore, database.movementDao().getTransactionById(ownerId, purchaseId))
        assertEquals(originalLedgerBefore, database.movementDao().getLedgerEntriesForTransaction(ownerId, purchaseId))

        repeat(3) { maintenance(ownerId, LocalDate.parse("2026-10-10"), zone) }
        assertEquals(1, activeBudgetAlertCount())
    }

    private suspend fun refund(amountMinor: Long, refundId: String, key: String): RefundTransactionResult =
        RegisterRefundTransaction(movementSource)(
            RefundTransactionCommand(
                idempotencyKey = key,
                userId = ownerId,
                purchaseTransactionId = purchaseId,
                refundTransactionId = refundId,
                amountMinor = amountMinor,
                currency = "PEN",
                occurredAt = at("2026-10-10"),
            ),
        )

    private suspend fun consumed(start: String, endExclusive: String): Long = reader.consumedMinor(
        ownerId,
        BudgetScope.Category("restaurants"),
        "PEN",
        BudgetInterval(LocalDate.parse(start), LocalDate.parse(endExclusive)),
        zone,
    )

    private suspend fun activeBudgetAlertCount(): Int = database.appNotificationDao()
        .getActive(ownerId)
        .count { it.notificationType == BudgetCenterAlertNotifier.TYPE_BUDGET_ALERT }

    private fun period(budget: Budget, start: String, endExclusive: String): BudgetPeriod {
        val interval = BudgetInterval(LocalDate.parse(start), LocalDate.parse(endExclusive))
        return BudgetPeriod(
            id = BudgetPeriod.idFor(budget.id, interval),
            budgetId = budget.id,
            ownerId = ownerId,
            interval = interval,
            limitMinor = budget.limitMinor,
            scope = budget.scope,
        )
    }

    private fun at(date: String): Long = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}
