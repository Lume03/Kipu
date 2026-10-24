package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.domain.RegisterRefundTransaction
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementRefundRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userId = UUID.randomUUID().toString()
    private val accountId = UUID.randomUUID().toString()
    private val purchaseId = UUID.randomUUID().toString()
    private val refundId = UUID.randomUUID().toString()
    private val idempotencyKey = UUID.randomUUID().toString()
    private val databaseName = "refund-${UUID.randomUUID()}.db"
    private lateinit var database: KipuDatabase

    @Before
    fun setUp() {
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun atomicCommitSurvivesRoomRestartWithLinkLedgerReceiptAndPendingOutbox() = runTest {
        val movementDao = database.movementDao()
        val source = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        database.accountDao().insert(
            AccountEntity(
                id = accountId,
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
        movementDao.insertTransaction(
            TransactionEntity(
                id = purchaseId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                sourceAccountId = accountId,
                categoryId = "food",
                occurredAt = 1_000L,
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                createdAt = 1_000L,
            ),
        )
        movementDao.insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    transactionId = purchaseId,
                    accountId = accountId,
                    role = "SOURCE",
                    signedAmountMinor = -10_000L,
                    currencyCode = "PEN",
                    createdAt = 1_000L,
                ),
            ),
        )

        val result = RegisterRefundTransaction(source).invoke(
            RefundTransactionCommand(
                idempotencyKey = idempotencyKey,
                userId = userId,
                purchaseTransactionId = purchaseId,
                refundTransactionId = refundId,
                amountMinor = 4_000L,
                currency = "PEN",
                occurredAt = 2_000L,
            ),
        )
        assertTrue(result is RefundTransactionResult.Success)
        assertEquals(-6_000L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        database.close()

        database = openDatabase()
        val reopenedDao = database.movementDao()
        val refund = reopenedDao.getTransactionById(userId, refundId)
        val link = database.transactionLinkDao().getByRefundId(userId, refundId)
        val outbox = reopenedDao.getRefundOutboxForTransaction(userId, refundId)
        val receipt = reopenedDao.getReceipt(userId, idempotencyKey)

        assertEquals("REFUND", refund?.operationKind)
        assertEquals(purchaseId, link?.sourceTransactionId)
        assertEquals(refundId, link?.targetTransactionId)
        assertEquals(4_000L, reopenedDao.getLedgerEntriesForTransaction(userId, refundId).single().signedAmountMinor)
        assertEquals("PENDING", outbox?.state)
        assertEquals("CREATE_REFUND", outbox?.commandType)
        assertEquals(refundId, receipt?.transactionId)
    }

    private fun openDatabase() = Room.databaseBuilder(context, KipuDatabase::class.java, databaseName)
        .allowMainThreadQueries()
        .build()
}
