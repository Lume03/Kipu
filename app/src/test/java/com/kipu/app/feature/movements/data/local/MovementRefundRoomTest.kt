package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.domain.RegisterRefundTransaction
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
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

    @Test
    fun refundCommitAtomicallyWritesMovementLinkLedgerProjectionReceiptAndOutbox() = runBlocking {
        val purchaseId = insertPurchase()
        val result = RegisterRefundTransaction(source).invoke(
            RefundTransactionCommand(
                idempotencyKey = UUID.randomUUID().toString(),
                userId = userId,
                purchaseTransactionId = purchaseId,
                refundTransactionId = UUID.randomUUID().toString(),
                amountMinor = 4_000L,
                currency = "PEN",
                occurredAt = 2_500L,
            ),
        )

        assertEquals(true, result is RefundTransactionResult.Success)
        val refund = (result as RefundTransactionResult.Success).transaction
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, refund.id).size)
        assertEquals(4_000L, dao.getBalanceProjection(userId, ACCOUNT_ID)?.balanceMinor)
        assertEquals(purchaseId, database.transactionLinkDao().getByRefundId(userId, refund.id)?.sourceTransactionId)
        val outbox = dao.getPendingOutboxForTransaction(userId, refund.id).single()
        assertEquals("CREATE_REFUND", outbox.commandType)
        assertEquals(1, outbox.contractVersion)
        val receipt = dao.getReceipt(userId, outbox.idempotencyKey)
        assertEquals("REFUND", receipt?.commandType)
        assertEquals("APPLIED", receipt?.status)
    }

    @Test
    fun rejectedRefundRemainsVisibleAndCanBeVoidedWithoutChangingPurchase() = runBlocking {
        val purchaseId = insertPurchase()
        val result = RegisterRefundTransaction(source).invoke(
            RefundTransactionCommand(
                idempotencyKey = UUID.randomUUID().toString(),
                userId = userId,
                purchaseTransactionId = purchaseId,
                refundTransactionId = UUID.randomUUID().toString(),
                amountMinor = 4_000L,
                currency = "PEN",
                occurredAt = 2_500L,
            ),
        ) as RefundTransactionResult.Success
        val outbox = dao.getPendingOutboxForTransaction(userId, result.transaction.id).single()
        dao.updateOutboxResult(userId, outbox.id, "FAILED_PERMANENT", null, "REFUND_EXCEEDS_REMAINDER")
        dao.updateTransactionSyncStatus(userId, result.transaction.id, "FAILED_PERMANENT")

        assertEquals("REFUND_EXCEEDS_REMAINDER", source.getRefundRejection(userId, result.transaction.id))
        val voided = source.voidRejectedRefund(userId, result.transaction.id) as RefundTransactionResult.Success

        assertEquals("VOIDED", dao.getTransactionById(userId, result.transaction.id)?.status)
        assertEquals("CONFIRMED", dao.getTransactionById(userId, purchaseId)?.status)
        assertEquals(0L, dao.getBalanceProjection(userId, ACCOUNT_ID)?.balanceMinor)
        assertEquals(10_000L, consumption(from = 1_000L, to = 2_000L, categories = emptySet()))
        assertEquals("VOIDED", voided.transaction.status.name)
    }

    private suspend fun insertPurchaseAndRefund(purchaseAmount: Long, refundAmount: Long) {
        val purchaseId = insertPurchase(purchaseAmount)
        val refundId = UUID.randomUUID().toString()
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

    private suspend fun insertPurchase(amountMinor: Long = 10_000L): String {
        val purchaseId = UUID.randomUUID().toString()
        dao.insertTransaction(
            TransactionEntity(
                id = purchaseId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = amountMinor,
                currencyCode = "PEN",
                sourceAccountId = ACCOUNT_ID,
                categoryId = CATEGORY_ID,
                occurredAt = 1_500L,
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                operationKind = "STANDARD",
            ),
        )
        return purchaseId
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
