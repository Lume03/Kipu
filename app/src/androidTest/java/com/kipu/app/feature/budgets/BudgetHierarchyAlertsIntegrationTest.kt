package com.kipu.app.feature.budgets

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
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetWriteResult
import com.kipu.app.feature.budgets.domain.EvaluateBudgetAlerts
import com.kipu.app.feature.budgets.domain.ExpenseBudgetConsumptionReader
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
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
class BudgetHierarchyAlertsIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ownerId = UUID.randomUUID().toString()
    private val accountId = UUID.randomUUID().toString()
    private val zone = ZoneId.of("America/Lima")
    private val september = LocalDate.parse("2026-09-01")
    private val clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), zone)
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstBudgetRepository
    private lateinit var reader: ExpenseBudgetConsumptionReader
    private lateinit var session: SessionCoordinatorImpl

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        session = SessionCoordinatorImpl().also { it.setActiveOwner(ownerId) }
        val transactionRunner = object : DatabaseTransactionRunner {
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
        val movementDao = database.movementDao()
        val movementSource = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
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
                openedAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.categoryDao().insertCategories(
            listOf(
                CategoryEntity("food", ownerId, null, "CUSTOM", createdAt = 1L, updatedAt = 1L),
                CategoryEntity("restaurants", ownerId, "food", "CUSTOM", createdAt = 2L, updatedAt = 2L),
            ),
        )
        createBudget("restaurants-budget", BudgetScope.Category("restaurants"), listOf(5_000))
        createBudget("food-budget", BudgetScope.Category("food"), emptyList())
        createBudget("total-budget", BudgetScope.Total, emptyList())
        insertExpense("expense-49", 4_900L, "2026-09-10")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun childExpenseCountsOnceInEachMatchingScopeAndThresholdCrossingNotifiesOnlyOnce() = runBlocking {
        val getConsumption = GetBudgetConsumption(repository, reader)
        val budgetIds = listOf("restaurants-budget", "food-budget", "total-budget")
        budgetIds.forEach { budgetId ->
            val result = getConsumption.current(ownerId, budgetId, LocalDate.parse("2026-09-20"), zone)
            assertEquals(4_900L, (result as BudgetOutcome.Success).value.consumption.consumedMinor)
        }

        val ledger = RoomBudgetAlertLedger(database.budgetAlertLedgerDao(), session, clock)
        val notifier = BudgetCenterAlertNotifier(database.appNotificationDao(), session, clock)
        val evaluate = EvaluateBudgetAlerts(repository, reader, ledger, notifier)
        assertTrue(evaluate(ownerId, "restaurants-budget", LocalDate.parse("2026-09-20"), zone).isEmpty())

        insertExpense("expense-crossing", 200L, "2026-09-21")
        budgetIds.forEach { budgetId ->
            val result = getConsumption.current(ownerId, budgetId, LocalDate.parse("2026-09-21"), zone)
            assertEquals(5_100L, (result as BudgetOutcome.Success).value.consumption.consumedMinor)
        }

        val firstCrossing = evaluate(ownerId, "restaurants-budget", LocalDate.parse("2026-09-21"), zone)
        assertEquals(listOf(5_000), firstCrossing.map { it.key.thresholdBps })
        repeat(3) {
            assertTrue(evaluate(ownerId, "restaurants-budget", LocalDate.parse("2026-09-21"), zone).isEmpty())
        }
        val notifications = database.appNotificationDao().getActive(ownerId)
            .filter { it.notificationType == BudgetCenterAlertNotifier.TYPE_BUDGET_ALERT }
        assertEquals(1, notifications.size)
        assertEquals("restaurants-budget", notifications.single().referenceEntityId)
    }

    private suspend fun createBudget(id: String, scope: BudgetScope, thresholds: List<Int>) {
        val budget = Budget(
            id = id,
            ownerId = ownerId,
            name = id,
            scope = scope,
            limitMinor = 10_000L,
            currency = "PEN",
            schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
            thresholdsBps = thresholds,
            createdOn = september,
        )
        val interval = BudgetInterval(september, LocalDate.parse("2026-10-01"))
        val firstPeriod = BudgetPeriod(
            id = BudgetPeriod.idFor(id, interval),
            budgetId = id,
            ownerId = ownerId,
            interval = interval,
            limitMinor = budget.limitMinor,
            scope = scope,
        )
        assertEquals(BudgetWriteResult.OK, repository.create(budget, firstPeriod))
    }

    private suspend fun insertExpense(id: String, amountMinor: Long, date: String) {
        val occurredAt = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        database.movementDao().insertTransaction(
            TransactionEntity(
                id = id,
                userId = ownerId,
                type = "EXPENSE",
                amountMinor = amountMinor,
                currencyCode = "PEN",
                sourceAccountId = accountId,
                categoryId = "restaurants",
                occurredAt = occurredAt,
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationKind = "STANDARD",
            ),
        )
    }
}
