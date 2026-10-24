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
import com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepository
import com.kipu.app.feature.budgets.data.RoomBudgetCategoryLookup
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetPlanAccess
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus
import com.kipu.app.feature.budgets.domain.BudgetValidator
import com.kipu.app.feature.budgets.domain.CreateBudget
import com.kipu.app.feature.budgets.domain.CreateBudgetCommand
import com.kipu.app.feature.budgets.domain.DeleteBudget
import com.kipu.app.feature.budgets.domain.EditBudget
import com.kipu.app.feature.budgets.domain.EditBudgetCommand
import com.kipu.app.feature.budgets.domain.FinishBudget
import com.kipu.app.feature.budgets.domain.RenewBudgetPeriods
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
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
class BudgetCrudFinancialIsolationIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ownerId = UUID.randomUUID().toString()
    private val accountId = UUID.randomUUID().toString()
    private val movementId = UUID.randomUUID().toString()
    private val budgetId = UUID.randomUUID().toString()
    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.parse("2026-10-24")
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), zone)
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstBudgetRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val session = SessionCoordinatorImpl().also { it.setActiveOwner(ownerId) }
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
        database.categoryDao().insertCategory(
            CategoryEntity("food", ownerId, null, "CUSTOM", createdAt = 1L, updatedAt = 1L),
        )
        database.accountDao().insert(
            AccountEntity(
                id = accountId,
                userId = ownerId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta de prueba",
                type = "CASH",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 5_000L,
                openedAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        database.movementDao().insertTransaction(
            TransactionEntity(
                id = movementId,
                userId = ownerId,
                type = "EXPENSE",
                amountMinor = 1_250L,
                currencyCode = "PEN",
                sourceAccountId = accountId,
                categoryId = "food",
                occurredAt = 2_000L,
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                createdAt = 2_000L,
                updatedAt = 2_000L,
                operationKind = "STANDARD",
            ),
        )
        database.movementDao().insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = ownerId,
                    transactionId = movementId,
                    accountId = accountId,
                    role = "SOURCE",
                    signedAmountMinor = -1_250L,
                    currencyCode = "PEN",
                    createdAt = 2_000L,
                ),
            ),
        )
        BalanceProjectionStore(database.movementDao()).rebuildBalanceFromLedger(ownerId, accountId, "PEN")
        Unit
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createEditFinishAndConfirmedDeletePreserveFinancesAndTombstoneRejectsStaleReplay() = runBlocking {
        val financialBaseline = financialSnapshot()
        val validator = BudgetValidator(RoomBudgetCategoryLookup(database.categoryDao()))
        val created = CreateBudget(
            repository = repository,
            validator = validator,
            planAccess = BudgetPlanAccess { false },
        )(
            CreateBudgetCommand(
                id = budgetId,
                ownerId = ownerId,
                name = "Comida",
                scope = BudgetScope.Category("food"),
                limitMinor = 50_000L,
                currency = "PEN",
                schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
                today = today,
            ),
        ) as BudgetOutcome.Success
        val originalBudget = created.value
        val originalPeriod = repository.listPeriods(ownerId, budgetId).single()
        assertEquals(financialBaseline, financialSnapshot())

        val edited = EditBudget(repository, validator, RenewBudgetPeriods(repository))(
            EditBudgetCommand(ownerId, budgetId, today, limitMinor = 60_000L),
        ) as BudgetOutcome.Success
        assertEquals(60_000L, edited.value.limitMinor)
        assertEquals(60_000L, repository.listPeriods(ownerId, budgetId).single().limitMinor)
        assertEquals(financialBaseline, financialSnapshot())

        val finished = FinishBudget(repository)(ownerId, budgetId) as BudgetOutcome.Success
        assertEquals(BudgetStatus.FINISHED, finished.value.status)
        assertEquals(1, repository.listPeriods(ownerId, budgetId).size)
        assertEquals(financialBaseline, financialSnapshot())

        assertTrue(DeleteBudget(repository)(ownerId, budgetId) is BudgetOutcome.Success)
        assertEquals(null, repository.getBudget(ownerId, budgetId))
        assertTrue(repository.listPeriods(ownerId, budgetId).isEmpty())
        assertEquals(finished.value.revision, database.budgetDao().getTombstone(ownerId, budgetId)?.lastRevision)
        assertEquals(financialBaseline, financialSnapshot())

        // A delayed create replay carries the original identity and period; Room's tombstone wins.
        assertEquals(
            com.kipu.app.feature.budgets.domain.BudgetWriteResult.ALREADY_EXISTS,
            repository.create(originalBudget, originalPeriod),
        )
        assertEquals(null, repository.getBudget(ownerId, budgetId))
        assertEquals(financialBaseline, financialSnapshot())
    }

    private suspend fun financialSnapshot(): FinancialSnapshot {
        val db = database.openHelper.readableDatabase
        return FinancialSnapshot(
            account = database.accountDao().getById(ownerId, accountId),
            movement = database.movementDao().getTransactionById(ownerId, movementId),
            ledger = database.movementDao().getLedgerEntriesForTransaction(ownerId, movementId),
            projectedBalance = database.movementDao().getBalanceProjection(ownerId, accountId)?.balanceMinor,
            ledgerBalance = database.movementDao().calculateLedgerSumForAccount(ownerId, accountId),
            accountCount = scalarCount(db, "SELECT COUNT(*) FROM accounts WHERE user_id=?", ownerId),
            movementCount = scalarCount(db, "SELECT COUNT(*) FROM transactions WHERE user_id=?", ownerId),
            ledgerCount = scalarCount(db, "SELECT COUNT(*) FROM ledger_entries WHERE user_id=?", ownerId),
        )
    }

    private fun scalarCount(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String, owner: String): Long =
        db.query(query, arrayOf(owner)).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private data class FinancialSnapshot(
        val account: AccountEntity?,
        val movement: TransactionEntity?,
        val ledger: List<LedgerEntryEntity>,
        val projectedBalance: Long?,
        val ledgerBalance: Long?,
        val accountCount: Long,
        val movementCount: Long,
        val ledgerCount: Long,
    )
}
