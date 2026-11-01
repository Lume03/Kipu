package com.kipu.app.feature.budgets.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.ExpenseBudgetConsumptionReader
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionResult
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InactiveCategoryBudgetConsumptionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val owner = "inactive-budget-owner"
    private val zone = ZoneId.of("America/Lima")
    private val day = LocalDate.of(2026, 10, 15)
    private val interval = BudgetInterval(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
    private lateinit var database: KipuDatabase
    private lateinit var reader: ExpenseBudgetConsumptionReader

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        val movementDao = database.movementDao()
        val source = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        database.accountDao().insert(
            AccountEntity(
                id = "acc", userId = owner, creationOperationId = "acc-op", alias = "Efectivo", type = "CASH",
                currency = "PEN", presetId = null, color = null, icon = null, initialBalanceMinorUnits = 0L,
                openedAt = 1L, createdAt = 1L, updatedAt = 1L,
            ),
        )
        database.categoryDao().insertCategories(
            listOf(
                category("inactive-parent", parent = null, active = false),
                category("inactive-child", parent = "inactive-parent", active = false),
                category("other", parent = null, active = true),
            ),
        )
        val at = day.atStartOfDay(zone).toInstant().toEpochMilli()
        movementDao.insertTransactions(
            listOf(
                expense("e1", "inactive-parent", 1_000L, at),
                expense("e2", "inactive-child", 500L, at),
                expense("e3", "other", 7_000L, at),
            ),
        )
        reader = ExpenseBudgetConsumptionReader(
            object : ExpenseConsumptionRepository {
                override suspend fun queryConsumption(userId: String, query: ExpenseConsumptionQuery): ExpenseConsumptionResult =
                    source.queryExpenseConsumption(userId, query)
            },
            RoomBudgetCategoryLookup(database.categoryDao()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inactiveCategoryBudgetCountsOnlyItsOwnTreeAndOnlyTotalCountsEverything() = runBlocking {
        assertEquals(1_500L, consumed(BudgetScope.Category("inactive-parent")))
        assertEquals(500L, consumed(BudgetScope.Category("inactive-child")))
        assertEquals(7_000L, consumed(BudgetScope.Category("other")))
        assertEquals(8_500L, consumed(BudgetScope.Total))
    }

    @Test
    fun missingCategoryConsumesZeroNeverTotal() = runBlocking {
        assertEquals(0L, consumed(BudgetScope.Category("does-not-exist")))
    }

    private suspend fun consumed(scope: BudgetScope) = reader.consumedMinor(owner, scope, "PEN", interval, zone)

    private fun category(id: String, parent: String?, active: Boolean) =
        CategoryEntity(id, owner, parent, "CUSTOM", active, 1L, 1L, 1L)

    private fun expense(id: String, categoryId: String, amountMinor: Long, at: Long) = TransactionEntity(
        id = id,
        userId = owner,
        type = "EXPENSE",
        amountMinor = amountMinor,
        currencyCode = "PEN",
        sourceAccountId = "acc",
        categoryId = categoryId,
        occurredAt = at,
        status = "CONFIRMED",
        syncStatus = "SYNCED",
        operationKind = "STANDARD",
    )
}
