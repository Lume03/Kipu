package com.kipu.app.feature.movements.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.remote.MovementApiResponse
import com.kipu.app.feature.movements.data.remote.RefundTransactionResponseDto
import com.kipu.app.feature.movements.data.remote.RemoteErrorDto
import com.kipu.app.feature.movements.domain.RegisterRefundTransaction
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementRefundSyncTest {
    private val userId = UUID.randomUUID().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var movementDao: MovementDao
    private lateinit var accountDao: AccountDao
    private val movementApi: MovementApi = mockk(relaxed = true)
    private val financialApi: FinancialInstrumentsApi = mockk()
    private val session: SessionCoordinator = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        movementDao = database.movementDao()
        accountDao = database.accountDao()
        every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to userId)
        every { session.currentOwner } returns LocalOwner(userId)
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(emptyList(), 0L, false),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun serverRejectsExcessWithoutHidingRefundAndLeavesPurchaseAndLinkRecoverable() = runTest {
        val fixture = committedRefund()
        coEvery { movementApi.registerRefund(any()) } returns MovementApiResponse.Success(
            RefundTransactionResponseDto(
                status = "REJECTED",
                error = RemoteErrorDto(code = "REFUND_EXCEEDS_REMAINDER", retryable = false),
            ),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Retry"))
        val rejected = movementDao.getTransactionById(userId, fixture.refundId)
        assertEquals("FAILED_PERMANENT", rejected?.syncStatus)
        assertEquals("SYNCED", movementDao.getTransactionById(userId, fixture.purchaseId)?.syncStatus)
        assertEquals(fixture.purchaseId, database.transactionLinkDao().getByRefundId(userId, fixture.refundId)?.sourceTransactionId)
        assertEquals("FAILED_PERMANENT", movementDao.getRefundOutboxForTransaction(userId, fixture.refundId)?.state)
        assertEquals("REFUND_EXCEEDS_REMAINDER", movementDao.getRefundOutboxForTransaction(userId, fixture.refundId)?.lastErrorCode)
        assertEquals("REJECTED", movementDao.getReceipt(userId, fixture.idempotencyKey)?.status)
        coVerify(exactly = 1) { movementApi.registerRefund(match { it.idempotencyKey == fixture.idempotencyKey }) }
    }

    @Test
    fun uncertainNetworkRetryReusesImmutableCommandAndServerDuplicateDoesNotDuplicateLink() = runTest {
        val fixture = committedRefund()
        val originalOutbox = movementDao.getRefundOutboxForTransaction(userId, fixture.refundId)!!
        coEvery { movementApi.registerRefund(any()) } returns MovementApiResponse.NetworkFailure(IOException("timeout after request"))

        val firstResult = worker().doWork()
        val afterFailure = movementDao.getRefundOutboxForTransaction(userId, fixture.refundId)!!
        assertTrue(firstResult.javaClass.simpleName.contains("Retry"))
        assertEquals("RETRY", afterFailure.state)
        assertEquals("NETWORK_ERROR", afterFailure.lastErrorCode)
        assertEquals(originalOutbox.payload, afterFailure.payload)
        assertEquals(originalOutbox.idempotencyKey, afterFailure.idempotencyKey)

        movementDao.updateOutboxResult(userId, afterFailure.id, "PENDING", null, null)
        coEvery { movementApi.registerRefund(any()) } returns MovementApiResponse.Success(
            RefundTransactionResponseDto(status = "DUPLICATE", transactionId = fixture.refundId),
        )

        val replayResult = worker().doWork()

        assertTrue(replayResult.javaClass.simpleName.contains("Success"))
        assertEquals("SYNCED", movementDao.getRefundOutboxForTransaction(userId, fixture.refundId)?.state)
        assertEquals("SYNCED", movementDao.getTransactionById(userId, fixture.refundId)?.syncStatus)
        assertEquals(originalOutbox.payload, movementDao.getRefundOutboxForTransaction(userId, fixture.refundId)?.payload)
        assertEquals(fixture.purchaseId, database.transactionLinkDao().getByRefundId(userId, fixture.refundId)?.sourceTransactionId)
        assertEquals("SYNCED", movementDao.getTransactionById(userId, fixture.purchaseId)?.syncStatus)
        assertEquals("CONFIRMED", movementDao.getTransactionById(userId, fixture.purchaseId)?.status)
        assertEquals(10_000L, movementDao.getTransactionById(userId, fixture.purchaseId)?.amountMinor)
        coVerify(exactly = 2) { movementApi.registerRefund(match { it.idempotencyKey == fixture.idempotencyKey }) }
    }

    private suspend fun committedRefund(): RefundFixture {
        val accountId = UUID.randomUUID().toString()
        val purchaseId = UUID.randomUUID().toString()
        val refundId = UUID.randomUUID().toString()
        val idempotencyKey = UUID.randomUUID().toString()
        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cash",
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
        val source = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
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
        return RefundFixture(purchaseId, refundId, idempotencyKey)
    }

    private fun worker() = SyncMovementsWorker(
        appContext = context,
        workerParams = params,
        movementDao = movementDao,
        api = movementApi,
        financialApi = financialApi,
        accountDao = accountDao,
        cardDao = database.cardDao(),
        database = database,
        sessionCoordinator = session,
    )

    private data class RefundFixture(val purchaseId: String, val refundId: String, val idempotencyKey: String)
}
