package com.kipu.app.feature.capture.domain

import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import io.mockk.*
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CaptureRecoveryRegressionTest {
    private fun transaction(c: CaptureCandidateRecord) = Transaction(
        "existing-movement", c.ownerId, MovementType.EXPENSE, c.amountMinor!!, c.currency!!,
        sourceAccountId = c.accountId, categoryId = c.categoryId, occurredAt = c.occurredAt!!,
    )

    @Test fun `committed receipt reconciles before similarity and never invokes ledger again`() = runTest {
        val candidate = captureCandidate()
        val store = FakeCaptureCandidateStore(candidate)
        val register = mockk<RegisterTransaction>()
        val credit = mockk<ConfirmCreditPurchase>()
        val tx = transaction(candidate)
        val confirm = testConfirmCaptureCandidate(store, FakeCaptureAccessGate(), register, credit,
            { _, _, _ -> null }, Clock.systemUTC(), emptySet(), receipts = CaptureRegistrationReceipts { owner, key ->
                assertEquals(candidate.ownerId, owner)
                assertEquals(CaptureResolutionKey.of(owner, candidate.id), key)
                tx
            })
        assertEquals(CaptureConfirmationResult.Registered(tx.id, true), confirm(ConfirmCaptureCommand.fromCandidate(candidate)!!))
        assertEquals(CaptureCandidateStatus.CONFIRMED, store.get(candidate.ownerId, candidate.id)!!.status)
        coVerify(exactly = 0) { register(any()) }
    }

    @Test fun `wallet lookup completes before final automatic access gate`() = runTest {
        val candidate = captureCandidate().copy(instrumentKind = CaptureInstrumentKind.WALLET, cardId = null)
        val store = FakeCaptureCandidateStore(candidate)
        val gate = FakeCaptureAccessGate()
        val register = mockk<RegisterTransaction>()
        val confirm = testConfirmCaptureCandidate(store, gate, register, mockk(),
            { _, _, _ -> gate.decision = CaptureAccessDecision.PremiumRequired; "YAPE" }, Clock.systemUTC(), emptySet())
        val result = confirm(ConfirmCaptureCommand.fromCandidate(candidate)!!, CaptureConfirmationMode.AUTOMATIC)
        assertTrue(result is CaptureConfirmationResult.AccessDenied)
        assertEquals(CaptureCandidateStatus.PENDING, store.get(candidate.ownerId, candidate.id)!!.status)
        coVerify(exactly = 0) { register(any()) }
    }

    @Test fun `each failed resolved effect survives restart while completed effects are not repeated`() = runTest {
        for (failedFlag in listOf(CaptureFollowup.LEARN, CaptureFollowup.EXPECTED_PAYMENT, CaptureFollowup.REVIEW_NOTICE)) {
            val candidate = captureCandidate().copy(status = CaptureCandidateStatus.CONFIRMED,
                resolvedTransactionId = "tx", followupsPending = CaptureFollowup.RESOLVED)
            val store = FakeCaptureCandidateStore(candidate)
            var failing = true
            val calls = mutableMapOf<Int, Int>()
            val listeners = listOf(CaptureFollowup.LEARN, CaptureFollowup.EXPECTED_PAYMENT, CaptureFollowup.REVIEW_NOTICE).map { flag ->
                object : CaptureCandidateResolvedListener {
                    override val followupFlag = flag
                    override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) {
                        calls[flag] = (calls[flag] ?: 0) + 1
                        if (flag == failedFlag && failing) error("transient")
                    }
                }
            }.toSet()
            assertFalse(CaptureResolutionFollowups(store, listeners, emptySet()).process(candidate.ownerId, candidate.id))
            assertEquals(failedFlag, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
            failing = false
            assertTrue(CaptureResolutionFollowups(store, listeners, emptySet()).process(candidate.ownerId, candidate.id))
            assertEquals(0, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
            for (flag in calls.keys) assertEquals(if (flag == failedFlag) 2 else 1, calls[flag])
        }
    }

    @Test fun `resolved replay retries followups without another registration`() = runTest {
        val candidate = captureCandidate().copy(status = CaptureCandidateStatus.AUTO_REGISTERED,
            resolvedTransactionId = "tx", followupsPending = CaptureFollowup.EXPECTED_PAYMENT)
        val store = FakeCaptureCandidateStore(candidate)
        var calls = 0
        val listener = object : CaptureCandidateResolvedListener {
            override val followupFlag = CaptureFollowup.EXPECTED_PAYMENT
            override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) { calls++ }
        }
        val register = mockk<RegisterTransaction>()
        val confirm = testConfirmCaptureCandidate(store, FakeCaptureAccessGate(), register, mockk(),
            { _, _, _ -> null }, Clock.systemUTC(), setOf(listener))
        assertEquals(CaptureConfirmationResult.Registered("tx", true), confirm(ConfirmCaptureCommand.fromCandidate(candidate)!!))
        assertEquals(1, calls)
        assertEquals(0, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
        coVerify(exactly = 0) { register(any()) }
    }

    @Test fun `explicit linked wallet selection sends parent account and channel to the existing use case`() = runTest {
        val candidate = captureCandidate().copy(instrumentKind = CaptureInstrumentKind.WALLET, accountId = "bank", cardId = null)
        val store = FakeCaptureCandidateStore(candidate)
        val register = mockk<RegisterTransaction>()
        coEvery { register(any()) } answers { RegisterTransactionResult.Success(transaction(candidate)) }
        val channels = object : CaptureWalletChannelResolver {
            override suspend fun channelFor(ownerId: String, accountId: String, provider: CaptureProvider): String? = error("unused")
            override suspend fun resolve(ownerId: String, selectedId: String, currency: String, provider: CaptureProvider): CaptureWalletPayment {
                assertEquals("wallet", selectedId)
                return CaptureWalletPayment("bank", "PLIN")
            }
        }
        val confirm = testConfirmCaptureCandidate(store, FakeCaptureAccessGate(), register, mockk(), channels, Clock.systemUTC(), emptySet())
        val command = ConfirmCaptureCommand.fromCandidate(candidate)!!.copy(selectedAccountId = "wallet")
        assertTrue(confirm(command) is CaptureConfirmationResult.Registered)
        coVerify { register(match { it.sourceAccountId == "bank" && it.paymentChannel == "PLIN" }) }
    }
}
