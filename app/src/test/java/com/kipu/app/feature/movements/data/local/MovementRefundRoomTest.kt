package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class MovementRefundRoomTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var source: MovementLocalDataSource
    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
        source = MovementLocalDataSource(database, dao, BalanceProjectionStore(dao))
        runBlocking {
            database.accountDao().insert(
                AccountEntity(
                    id = ACCOUNT_ID,
                    userId = userId,
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
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun partialRefundReducesPurchasePeriodAndDoesNotAddExpenseToRefundPeriod() = runBlocking {
        insertPurchaseAndRefund(purchaseAmount = 10_000L, refundAmount = 4_000L)

        assertEquals(6_000L, consumption(from = 1_000L, to = 2_000L, categories = setOf(CATEGORY_ID)))
        assertEquals(0L, consumption(from = 2_000L, to = 3_000L, categories = setOf(CATEGORY_ID)))
    }

    @Test
    fun fullRefundReducesPurchasePeriodToZero() = runBlocking {
        insertPurchaseAndRefund(purchaseAmount = 10_000L, refundAmount = 10_000L)

        assertEquals(0L, consumption(from = 1_000L, to = 2_000L, categories = emptySet()))
    }

    private suspend fun insertPurchaseAndRefund(purchaseAmount: Long, refundAmount: Long) {
        val purchaseId = UUID.randomUUID().toString()
        val refundId = UUID.randomUUID().toString()
        dao.insertTransaction(
            TransactionEntity(
                id = purchaseId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = purchaseAmount,
                currencyCode = "PEN",
                sourceAccountId = ACCOUNT_ID,
                categoryId = CATEGORY_ID,
                occurredAt = 1_500L,
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                operationKind = "STANDARD",
            ),
        )
        dao.insertTransaction(
            TransactionEntity(
                id = refundId,
                userId = userId,
                type = "INCOME",
                amountMinor = refundAmount,
                currencyCode = "PEN",
                sourceAccountId = ACCOUNT_ID,
                occurredAt = 2_500L,
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                operationKind = "REFUND",
            ),
        )
        database.transactionLinkDao().insert(
            TransactionLinkEntity(
                userId = userId,
                sourceTransactionId = purchaseId,
                targetTransactionId = refundId,
                linkType = "REFUND",
                createdAt = 2_500L,
            ),
        )
    }

    private suspend fun consumption(from: Long, to: Long, categories: Set<String>): Long =
        source.queryExpenseConsumption(
            userId,
            ExpenseConsumptionQuery(
                currency = "PEN",
                fromInclusive = from,
                toExclusive = to,
                zoneId = ZoneId.of("America/Lima"),
                categoryIds = categories,
            ),
        ).amountMinor

    private companion object {
        const val ACCOUNT_ID = "refund-test-account"
        const val CATEGORY_ID = "refund-test-category"
    }
}
