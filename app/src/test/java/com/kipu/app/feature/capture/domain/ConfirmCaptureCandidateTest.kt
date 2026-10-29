package com.kipu.app.feature.capture.domain

import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmCaptureCandidateTest {
    private val owner = "owner-1"
    private val candidate = captureCandidate(owner)
    private val key = CaptureResolutionKey.of(owner, candidate.id)
    private val testCard = CardId("61000000-0000-4000-8000-000000000010")
    private val store = FakeCaptureCandidateStore(candidate)
    private val gate = FakeCaptureAccessGate(owner)
    private val clock = Clock.fixed(Instant.ofEpochMilli(CAPTURE_NOW), ZoneOffset.UTC)
    private val registered = mutableListOf<RegisterTransactionCommand>()
    private val registerTransaction = mockk<RegisterTransaction> {
        coEvery { this@mockk.invoke(any()) } answers {
            val command = firstArg<RegisterTransactionCommand>()
            registered += command
            RegisterTransactionResult.Success(transaction(command))
        }
    }
    private val creditPurchase = mockk<ConfirmCreditPurchase> {
        coEvery { this@mockk.invoke(testCard, any(), any(), any(), any(), OperationId(key), any(), any(), any()) } returns Result.success(Unit)
    }
    private val resolvedCalls = mutableListOf<Pair<CaptureCandidateRecord, String>>()
    private val listener = object : CaptureCandidateResolvedListener {
        override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) {
            resolvedCalls += candidate to transactionId
        }
    }
    private val confirm = testConfirmCaptureCandidate(
        store, gate, registerTransaction, creditPurchase, { _, _, _ -> null }, clock, setOf(listener),
    )

    private fun transaction(command: RegisterTransactionCommand) = Transaction(
        id = "tx-${command.idempotencyKey}", userId = command.userId, type = command.type,
        amountMinor = command.amountMinor, currency = command.currency, occurredAt = command.occurredAt,
        sourceAccountId = command.sourceAccountId, categoryId = command.categoryId,
    )

    private fun command(record: CaptureCandidateRecord = candidate) = requireNotNull(ConfirmCaptureCommand.fromCandidate(record))

    private fun useOnly(record: CaptureCandidateRecord) {
        store.rows.value = mapOf((record.ownerId to record.id) to record)
    }

    @Test fun `debit confirmation registers through RegisterTransaction with the deterministic key`() = runTest {
        val result = confirm(command()) as CaptureConfirmationResult.Registered
        assertEquals(1, registered.size)
        assertEquals(key, registered.single().idempotencyKey)
        assertEquals(MovementType.EXPENSE, registered.single().type)
        assertEquals("account-1", registered.single().sourceAccountId)
        val stored = store.row(owner, candidate.id)!!
        assertEquals(CaptureCandidateStatus.CONFIRMED, stored.status)
        assertEquals(result.transactionId, stored.resolvedTransactionId)
        assertEquals(key, stored.resolutionKey)
        assertEquals(CAPTURE_NOW, stored.resolvedAt)
        assertNull(stored.evidenceSnippet)
        assertEquals(1, resolvedCalls.size)
    }

    @Test fun `second confirmation returns the existing movement without touching the ledger again`() = runTest {
        val first = confirm(command()) as CaptureConfirmationResult.Registered
        val second = confirm(command()) as CaptureConfirmationResult.Registered
        assertEquals(first.transactionId, second.transactionId)
        assertTrue(second.alreadyResolved)
        assertEquals(1, registered.size)
        assertEquals(1, resolvedCalls.size)
    }

    @Test fun `similar movement warning is returned and keeps the candidate pending`() = runTest {
        val existing = Transaction("tx-old", owner, MovementType.EXPENSE, 12_000L, "PEN", occurredAt = CAPTURE_NOW, sourceAccountId = "account-1", categoryId = "category-1")
        coEvery { registerTransaction(match { !it.ignoreSimilarityWarning }) } returns
            RegisterTransactionResult.SimilarTransactionWarning(existing, mockk(relaxed = true))
        val warning = confirm(command()) as CaptureConfirmationResult.SimilarTransactionWarning
        assertEquals("tx-old", warning.existingTransaction.id)
        assertEquals(CaptureCandidateStatus.PENDING, store.row(owner, candidate.id)!!.status)

        val accepted = confirm(command().copy(acceptSimilar = true))
        assertTrue(accepted is CaptureConfirmationResult.Registered)
        assertTrue(registered.last().ignoreSimilarityWarning)
        assertEquals(key, registered.last().idempotencyKey)
    }

    @Test fun `automatic mode never ignores a similarity warning`() = runTest {
        confirm(command().copy(acceptSimilar = true), CaptureConfirmationMode.AUTOMATIC)
        assertEquals(false, registered.single().ignoreSimilarityWarning)
        assertEquals(CaptureCandidateStatus.AUTO_REGISTERED, store.row(owner, candidate.id)!!.status)
    }

    @Test fun `user edits are registered and recorded as corrected fields`() = runTest {
        confirm(command().copy(amountMinor = 15_000L, categoryId = "category-2", merchantId = "merchant-2"))
        assertEquals(15_000L, registered.single().amountMinor)
        val stored = store.row(owner, candidate.id)!!
        assertEquals(listOf("AMOUNT", "MERCHANT", "CATEGORY"), stored.userCorrectedFields)
        assertEquals(15_000L, stored.amountMinor)
        assertEquals("category-2", stored.categoryId)
        val callbackRecord = resolvedCalls.single().first
        assertEquals(stored, callbackRecord.copy(followupsPending = stored.followupsPending))
        assertEquals(stored.followupsPending, callbackRecord.followupsPending and CaptureFollowup.LEARN.inv())
    }

    @Test fun `credit purchase uses ConfirmCreditPurchase with the key as operation and installments`() = runTest {
        val credit = candidate.asCredit()
        useOnly(credit)
        val installments = slot<Int>()
        coEvery { creditPurchase(testCard, any(), any(), any(), capture(installments), OperationId(key), any(), any(), any()) } returns
            Result.success(Unit)

        val result = confirm(command(credit).copy(creditMode = CreditMode.INSTALLMENTS, installmentCount = 6))
            as CaptureConfirmationResult.Registered
        assertEquals(6, installments.captured)
        coVerify { creditPurchase(testCard, any(), any(), any(), 6, OperationId(key), any(), any(), any()) }
        assertEquals(CaptureResolutionKey.creditTransactionId(owner, key), result.transactionId)
        assertTrue(registered.isEmpty())
        val stored = store.row(owner, credit.id)!!
        assertEquals(CreditMode.INSTALLMENTS, stored.creditMode)
        assertEquals(6, stored.installmentCount)
        assertEquals(listOf("CREDIT_MODE"), stored.userCorrectedFields)
    }

    @Test fun `direct credit purchase sends a single installment`() = runTest {
        val credit = candidate.asCredit()
        useOnly(credit)
        confirm(command(credit))
        coVerify { creditPurchase(testCard, any(), any(), any(), 1, OperationId(key), "category-1", "merchant-1", any()) }
    }

    @Test fun `installments outside 2 to 36 are rejected before the ledger`() = runTest {
        val credit = candidate.asCredit()
        useOnly(credit)
        val result = confirm(command(credit).copy(creditMode = CreditMode.INSTALLMENTS, installmentCount = 48))
        assertEquals("installments", (result as CaptureConfirmationResult.Rejected).field)
        coVerify(exactly = 0) { creditPurchase(testCard, any(), any(), any(), any(), OperationId(key), any(), any(), any()) }
    }

    @Test fun `switching from credit to debit drops installments`() = runTest {
        val credit = candidate.asCredit().copy(creditMode = CreditMode.INSTALLMENTS, installmentCount = 3)
        useOnly(credit)
        confirm(command(credit).copy(instrumentKind = CaptureInstrumentKind.DEBIT_ACCOUNT, accountId = "account-1", cardId = "card-1"))
        assertEquals(1, registered.size)
        val stored = store.row(owner, credit.id)!!
        assertNull(stored.creditMode)
        assertNull(stored.installmentCount)
        assertTrue("INSTRUMENT" in stored.userCorrectedFields)
        assertTrue("CREDIT_MODE" in stored.userCorrectedFields)
    }

    @Test fun `credit card is never confirmed automatically`() = runTest {
        val credit = candidate.asCredit()
        useOnly(credit)
        val result = confirm(command(credit), CaptureConfirmationMode.AUTOMATIC)
        assertTrue(result is CaptureConfirmationResult.Rejected)
        coVerify(exactly = 0) { creditPurchase(testCard, any(), any(), any(), any(), OperationId(key), any(), any(), any()) }
        assertEquals(CaptureCandidateStatus.PENDING, store.row(owner, credit.id)!!.status)
    }

    @Test fun `expired plan denies inbox confirmation but manual draft confirmation proceeds`() = runTest {
        gate.decision = CaptureAccessDecision.PremiumRequired
        val denied = confirm(command())
        assertEquals(CaptureConfirmationResult.AccessDenied(CaptureAccessDecision.PremiumRequired), denied)
        assertTrue(registered.isEmpty())

        val manual = confirm(command(), CaptureConfirmationMode.MANUAL_DRAFT)
        assertTrue(manual is CaptureConfirmationResult.Registered)
        assertEquals(key, registered.single().idempotencyKey)
    }

    @Test fun `ledger failure leaves the candidate pending with its evidence`() = runTest {
        coEvery { registerTransaction(any()) } returns RegisterTransactionResult.ValidationError("category", "La categoría es obligatoria")
        val result = confirm(command())
        assertEquals("category", (result as CaptureConfirmationResult.Rejected).field)
        val stored = store.row(owner, candidate.id)!!
        assertEquals(CaptureCandidateStatus.PENDING, stored.status)
        assertEquals(candidate.evidenceSnippet, stored.evidenceSnippet)
        assertTrue(resolvedCalls.isEmpty())
    }

    @Test fun `listener failure does not hide a successful registration`() = runTest {
        val failing = testConfirmCaptureCandidate(
            store, gate, registerTransaction, creditPurchase, { _, _, _ -> null }, clock,
            setOf(object : CaptureCandidateResolvedListener {
                override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) = error("boom")
            }),
        )
        assertTrue(failing(command()) is CaptureConfirmationResult.Registered)
    }

    @Test fun `discarded and foreign candidates are not registered`() = runTest {
        store.discard(owner, candidate.id, CAPTURE_NOW)
        assertEquals(CaptureConfirmationResult.Discarded, confirm(command()))
        assertTrue(confirm(command().copy(ownerId = "owner-2")) is CaptureConfirmationResult.AccessDenied)
        assertTrue(registered.isEmpty())
    }

    @Test fun `wallet expense carries the affiliated payment channel`() = runTest {
        val yape = candidate.copy(provider = CaptureProvider.YAPE, instrumentKind = CaptureInstrumentKind.WALLET, cardId = null)
        useOnly(yape)
        val walletConfirm = testConfirmCaptureCandidate(
            store, gate, registerTransaction, creditPurchase, { _, _, provider -> provider.name }, clock, emptySet(),
        )
        walletConfirm(command(yape))
        assertEquals("YAPE", registered.single().paymentChannel)
    }
}
