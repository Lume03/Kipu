package com.kipu.app.feature.budgets.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseEligibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ownerId = "expense-eligibility-owner"
    private val accountId = "expense-eligibility-account"
    private val occurredAt = 1_800_000_000_000L
    private val zone = ZoneId.of("America/Lima")
    private lateinit var database: KipuDatabase
    private lateinit var source: MovementLocalDataSource

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val movementDao = database.movementDao()
        source = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        database.accountDao().insert(
            AccountEntity(
                id = accountId,
                userId = ownerId,
                creationOperationId = "expense-eligibility-account-op",
                alias = "Efectivo",
                type = "CASH",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
        )
        database.movementDao().insertTransactions(
            listOf(
                expense("standard", 1_200L),
                expense("pending", 8_000L, status = "PENDING"),
                expense(
                    id = "card-purchase",
                    amountMinor = 2_500L,
                    sourceAccountId = null,
                    operationKind = "CARD_PURCHASE",
                    cardId = "eligible-credit-card",
                    installmentCount = 1,
                ),
                expense("voided", 3_000L, status = "VOIDED"),
                TransactionEntity(
                    id = "transfer",
                    userId = ownerId,
                    type = "TRANSFER",
                    amountMinor = 4_000L,
                    currencyCode = "PEN",
                    sourceAccountId = accountId,
                    destinationAccountId = "other-account",
                    occurredAt = occurredAt,
                    status = "CONFIRMED",
                    syncStatus = "SYNCED",
                    operationKind = "STANDARD",
                ),
                expense("debt-principal", 5_000L, operationKind = "DEBT_PAYMENT"),
                TransactionEntity(
                    id = "goal-funding",
                    userId = ownerId,
                    type = "TRANSFER",
                    amountMinor = 6_000L,
                    currencyCode = "PEN",
                    sourceAccountId = accountId,
                    destinationAccountId = "goal-backing-account",
                    occurredAt = occurredAt,
                    status = "ACTIVE",
                    syncStatus = "PENDING",
                    operationKind = "GOAL_FUNDING",
                ),
                expense("usd-expense", 7_000L, currencyCode = "USD"),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queryIncludesStandardAndSingleCardPurchaseButExcludesNonOperatingTransactions() = runBlocking {
        val result = source.queryExpenseConsumption(
            ownerId,
            ExpenseConsumptionQuery(
                currency = "PEN",
                fromInclusive = occurredAt - 1_000L,
                toExclusive = occurredAt + 1_000L,
                zoneId = zone,
            ),
        )

        assertEquals(3_700L, result.amountMinor)
        val cardRows = database.movementDao().getExpensesInPeriod(
            userId = ownerId,
            currencyCode = "PEN",
            fromInclusive = occurredAt - 1_000L,
            toExclusive = occurredAt + 1_000L,
        ).filter { it.operationKind == "CARD_PURCHASE" }
        assertEquals(1, cardRows.size)
    }

    private fun expense(
        id: String,
        amountMinor: Long,
        currencyCode: String = "PEN",
        status: String = "CONFIRMED",
        operationKind: String = "STANDARD",
        sourceAccountId: String? = accountId,
        cardId: String? = null,
        installmentCount: Int? = null,
    ) = TransactionEntity(
        id = id,
        userId = ownerId,
        type = "EXPENSE",
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        sourceAccountId = sourceAccountId,
        categoryId = "food",
        occurredAt = occurredAt,
        status = status,
        syncStatus = "SYNCED",
        cardId = cardId,
        operationKind = operationKind,
        installmentCount = installmentCount,
    )
}
