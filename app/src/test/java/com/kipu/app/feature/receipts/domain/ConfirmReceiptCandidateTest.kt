package com.kipu.app.feature.receipts.domain

import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmReceiptCandidateTest {
    @Test fun keepsStableIdempotencyKeyAndDuplicateOverrideWhilePreservingReceiptReference() = runTest {
        val owner = UUID.randomUUID().toString()
        val candidate = newReceiptCandidate(owner, "img", YapeReceiptFields(2000, 100, "A", "operation-1234"), false)
        val store = FakeStore(candidate)
        val register = mockk<RegisterTransaction>()
        val submitted = mutableListOf<RegisterTransactionCommand>()
        coEvery { register(any()) } coAnswers {
            submitted += firstArg<RegisterTransactionCommand>()
            RegisterTransactionResult.ValidationError("test", "retry")
        }
        val confirm = ConfirmReceiptCandidate(store, register)

        confirm(owner, candidate.id, command(owner, ignoreDuplicate = false))
        confirm(owner, candidate.id, command(owner, ignoreDuplicate = true))

        assertEquals(2, submitted.size)
        assertEquals(submitted.first().idempotencyKey, submitted.last().idempotencyKey)
        assertTrue(!submitted.first().ignoreSimilarityWarning)
        assertTrue(submitted.last().ignoreSimilarityWarning)
        assertEquals("operation-1234", store.current?.reference)
        assertEquals(candidate.referenceFingerprint, store.current?.referenceFingerprint)
        assertEquals(false, store.current?.isIncome)
    }

    private fun command(owner: String, ignoreDuplicate: Boolean) = RegisterTransactionCommand(
        idempotencyKey = UUID.randomUUID().toString(), userId = owner, type = MovementType.EXPENSE,
        amountMinor = 2500, currency = "PEN", sourceAccountId = UUID.randomUUID().toString(),
        occurredAt = 200, merchantProvisionalText = "Edited counterparty", note = "not receipt reference",
        ignoreSimilarityWarning = ignoreDuplicate,
    )

    private class FakeStore(candidate: ReceiptCandidate) : ReceiptCandidateStore {
        var current: ReceiptCandidate? = candidate
        override suspend fun import(ownerId: String, imageFingerprint: String, fields: YapeReceiptFields, isIncome: Boolean?) =
            error("not used")
        override fun observe(ownerId: String) = kotlinx.coroutines.flow.emptyFlow<List<ReceiptCandidate>>()
        override suspend fun get(ownerId: String, candidateId: String) = current?.takeIf { it.ownerId == ownerId && it.id == candidateId }
        override suspend fun edit(ownerId: String, candidateId: String, edit: ReceiptCandidateEdit): ReceiptCandidate? {
            val before = get(ownerId, candidateId) ?: return null
            return before.copy(amountMinor = edit.amountMinor, occurredAt = edit.occurredAt,
                recipient = edit.recipient, isIncome = edit.isIncome ?: before.isIncome).also { current = it }
        }
        override suspend fun markConfirmed(ownerId: String, candidateId: String) = false
        override suspend fun discard(ownerId: String, candidateId: String) = false
    }
}
