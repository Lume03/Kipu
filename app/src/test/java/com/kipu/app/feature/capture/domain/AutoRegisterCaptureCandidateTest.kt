package com.kipu.app.feature.capture.domain

import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRegisterCaptureCandidateTest {
    private val owner = "owner-1"
    private val store = FakeCaptureCandidateStore()
    private val gate = FakeCaptureAccessGate(owner)
    private val clock = Clock.fixed(Instant.ofEpochMilli(CAPTURE_NOW), ZoneOffset.UTC)
    private val registered = mutableListOf<RegisterTransactionCommand>()
    private val registerTransaction = mockk<RegisterTransaction> {
        coEvery { this@mockk.invoke(any()) } answers {
            val command = firstArg<RegisterTransactionCommand>()
            registered += command
            RegisterTransactionResult.Success(
                Transaction("tx-1", command.userId, command.type, command.amountMinor, command.currency,
                    sourceAccountId = command.sourceAccountId, categoryId = command.categoryId, occurredAt = command.occurredAt),
            )
        }
    }
    private val creditPurchase = mockk<ConfirmCreditPurchase>(relaxed = true)
    private val confirm = testConfirmCaptureCandidate(store, gate, registerTransaction, creditPurchase, { _, _, _ -> null }, clock, emptySet())
    private val autoRegister = AutoRegisterCaptureCandidate(store, confirm, clock)
    private val postProcessor = ScoreAndAutoRegisterPostProcessor(store, CaptureConfidenceScorer(), autoRegister, clock)

    private fun stored(record: CaptureCandidateRecord): CaptureCandidateRecord {
        store.rows.value = store.rows.value + ((record.ownerId to record.id) to record)
        return record
    }

    @Test fun `only a HIGH debit expense is registered automatically`() = runTest {
        val candidate = stored(captureCandidate(owner))
        assertEquals(AutoRegisterResult.Registered("tx-1"), autoRegister(candidate))
        val row = store.row(owner, candidate.id)!!
        assertEquals(CaptureCandidateStatus.AUTO_REGISTERED, row.status)
        assertEquals(CaptureResolutionKey.of(owner, candidate.id), registered.single().idempotencyKey)
        assertEquals(MovementType.EXPENSE, registered.single().type)
    }

    @Test fun `medium low income ambiguous and inferred candidates stay pending`() = runTest {
        listOf(
            captureCandidate(owner).copy(confidence = CaptureConfidenceLevel.MEDIUM),
            captureCandidate(owner).copy(confidence = CaptureConfidenceLevel.LOW),
            captureCandidate(owner).copy(direction = CaptureDirection.INCOME),
            captureCandidate(owner).copy(instrumentAmbiguous = true),
            captureCandidate(owner).copy(occurredAtInferred = true),
            captureCandidate(owner).copy(categoryId = null),
        ).forEach { candidate ->
            assertEquals(AutoRegisterResult.NotEligible, autoRegister(stored(candidate)))
            assertEquals(CaptureCandidateStatus.PENDING, store.row(owner, candidate.id)!!.status)
        }
        assertTrue(registered.isEmpty())
    }

    @Test fun `credit card is never registered automatically even when HIGH`() = runTest {
        val credit = stored(captureCandidate(owner).asCredit())
        assertEquals(AutoRegisterResult.NotEligible, autoRegister(credit))
        coVerify(exactly = 0) { creditPurchase(com.kipu.app.core.finance.domain.model.CardId(credit.cardId!!), any(), any(), any(), any(), com.kipu.app.core.finance.domain.model.OperationId(CaptureResolutionKey.of(owner, credit.id)), any(), any(), any()) }
        assertEquals(CaptureCandidateStatus.PENDING, store.row(owner, credit.id)!!.status)
    }

    @Test fun `plan expired right before saving keeps the candidate pending with a reason`() = runTest {
        val candidate = stored(captureCandidate(owner))
        gate.decision = CaptureAccessDecision.PremiumRequired
        assertEquals(AutoRegisterResult.AccessDenied, autoRegister(candidate))
        assertTrue(registered.isEmpty())
        val row = store.row(owner, candidate.id)!!
        assertEquals(CaptureCandidateStatus.PENDING, row.status)
        assertTrue(CaptureReason.AUTO_REGISTER_DENIED in row.confidenceReasons)
    }

    @Test fun `similar movement keeps the candidate pending as possible duplicate`() = runTest {
        val existing = Transaction("tx-old", owner, MovementType.EXPENSE, 12_000L, "PEN", occurredAt = CAPTURE_NOW, sourceAccountId = "account-1", categoryId = "category-1")
        coEvery { registerTransaction(any()) } returns RegisterTransactionResult.SimilarTransactionWarning(existing, mockk(relaxed = true))
        val candidate = stored(captureCandidate(owner))
        assertEquals(AutoRegisterResult.NeedsReview, autoRegister(candidate))
        val row = store.row(owner, candidate.id)!!
        assertEquals(CaptureCandidateStatus.PENDING, row.status)
        assertEquals(CaptureConfidenceLevel.MEDIUM, row.confidence)
        assertTrue(CaptureReason.POSSIBLE_DUPLICATE in row.confidenceReasons)
        // A rescoring never promotes it back to automatic registration.
        assertEquals(CaptureConfidenceLevel.MEDIUM, CaptureConfidenceScorer().score(row).level)
    }

    @Test fun `post processor persists the definitive confidence before auto registering`() = runTest {
        val low = stored(captureCandidate(owner).copy(confidence = CaptureConfidenceLevel.LOW, confidenceReasons = listOf(CaptureReason.DATE_INFERRED)))
        postProcessor.process(low)
        assertEquals(CaptureCandidateStatus.AUTO_REGISTERED, store.row(owner, low.id)!!.status)

        val inferred = stored(captureCandidate(owner).copy(occurredAtInferred = true, confidence = CaptureConfidenceLevel.LOW))
        postProcessor.process(inferred)
        val row = store.row(owner, inferred.id)!!
        assertEquals(CaptureCandidateStatus.PENDING, row.status)
        assertEquals(CaptureConfidenceLevel.MEDIUM, row.confidence)
        assertEquals(listOf(CaptureReason.DATE_INFERRED), row.confidenceReasons)
        assertEquals(1, registered.size)
    }
}
