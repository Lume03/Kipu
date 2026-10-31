package com.kipu.app.feature.accounts.data.sync

import androidx.work.*
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.testing.CreditDatabaseFixture
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreditScheduleSyncWorkerTest {
    private lateinit var f: CreditDatabaseFixture
    private val api = mockk<FinancialInstrumentsApi>()
    @Before fun setUp() { f = CreditDatabaseFixture() }
    @After fun tearDown() = f.close()
    private fun worker(): SyncInstrumentCommandsWorker {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns workDataOf(SyncInstrumentCommandsWorker.KEY_USER_ID to f.owner)
        return SyncInstrumentCommandsWorker(f.context, params, f.db.instrumentSyncDao(), f.db.cardDao(), f.db.accountDao(),
            f.db, api, f.db.movementDao(), f.session)
    }
    private suspend fun assertMismatchDoesNotBlockIndependent(interestMismatch: Boolean) {
        val operation = f.operation()
        val terms = if (interestMismatch) PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, 6, 4500, PurchaseRateSource.USER_ENTERED)
            else PurchaseTerms.interestFree(6)
        ConfirmCreditPurchase(f.repository)(f.cardId, Money(120000, Currency.PEN), "Comercio", Instant.parse("2026-06-26T12:00:00Z"),
            6, operation, f.categoryId, terms = terms).getOrThrow()
        val originalCommand = requireNotNull(f.db.instrumentSyncDao().getByOperationId(f.owner, operation.value))
        val request = Json.decodeFromString<CreditCommandRequestDto>(originalCommand.payloadJson)
        val original = f.db.creditDao().getInstallmentsForTransaction(f.owner, request.transaction.id)
        val independentRequest = request.copy(idempotencyKey = "independent", transaction = request.transaction.copy(id = "payment-independent",
            type = "TRANSFER", operationKind = "CARD_PAYMENT", cardId = "other-card", installmentCount = 1))
        f.db.movementDao().insertTransaction(TransactionEntity("payment-independent", f.owner, "TRANSFER", 100, "PEN",
            occurredAt = 1, status = "ACTIVE", syncStatus = "PENDING", createdAt = 1))
        val independent = originalCommand.copy(operationId = "independent", commandType = "PAY_CREDIT_CARD", aggregateId = "other-card",
            payloadJson = Json.encodeToString(independentRequest), createdAt = originalCommand.createdAt + 1)
        f.db.instrumentSyncDao().insert(independent)
        f.db.instrumentSyncDao().insert(independent.copy(operationId = "dependent", predecessorOperationId = originalCommand.operationId,
            aggregateId = originalCommand.aggregateId, createdAt = originalCommand.createdAt + 2))
        val remote = original.map { row -> CreditInstallmentResponseDto("remote-${row.installmentNumber}", row.installmentNumber,
            LocalDate.ofEpochDay(row.dueDate).minusMonths(if (interestMismatch) 0 else 1).toString(), row.principalMinor,
            if (interestMismatch) 0 else row.interestMinor) }
        coEvery { api.registerCreditPurchase(any(), f.owner) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto("DUPLICATE", transactionId = request.transaction.id, installments = remote))
        coEvery { api.allocateCreditPayment(any(), f.owner) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto("APPLIED", transactionId = independentRequest.transaction.id))
        val start = System.currentTimeMillis() * 1000L
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        val failed = requireNotNull(f.db.instrumentSyncDao().getByOperationId(f.owner, operation.value))
        assertEquals("ERROR", failed.state)
        assertEquals("INSTALLMENT_RECONCILIATION_FAILED", failed.errorCode)
        assertTrue(requireNotNull(failed.nextAttemptAt) >= start + 30000000L)
        assertNull(failed.leaseExpiresAt)
        assertEquals(1, failed.attemptCount)
        assertEquals("PENDING", f.db.movementDao().getTransactionById(f.owner, request.transaction.id)?.syncStatus)
        assertEquals(original, f.db.creditDao().getInstallmentsForTransaction(f.owner, request.transaction.id))
        assertEquals("SYNCED", f.db.instrumentSyncDao().getByOperationId(f.owner, "independent")?.state)
        assertEquals("SYNCED", f.db.movementDao().getTransactionById(f.owner, "payment-independent")?.syncStatus)
        assertEquals("PENDING", f.db.instrumentSyncDao().getByOperationId(f.owner, "dependent")?.state)
        coVerify(exactly = 1) { api.registerCreditPurchase(any(), f.owner) }
        coVerify(exactly = 1) { api.allocateCreditPayment(any(), f.owner) }
        // A second invocation before next_attempt_at neither spins nor strands deferred work.
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        coVerify(exactly = 1) { api.registerCreditPurchase(any(), f.owner) }
        assertEquals(original, f.db.creditDao().getInstallmentsForTransaction(f.owner, request.transaction.id))
    }
    @Test fun `legacy interest mismatch preserves local obligation and allows independent commands`() = runBlocking {
        assertMismatchDoesNotBlockIndependent(true)
    }
    @Test fun `legacy inclusive closing mismatch preserves local dates and allows independent commands`() = runBlocking {
        assertMismatchDoesNotBlockIndependent(false)
    }
}
