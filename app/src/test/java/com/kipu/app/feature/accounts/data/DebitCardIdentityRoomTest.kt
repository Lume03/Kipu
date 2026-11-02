package com.kipu.app.feature.accounts.data

import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.testing.CreditDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebitCardIdentityRoomTest {
    private lateinit var f: CreditDatabaseFixture
    private val debit = CardId("61000000-0000-4000-8000-000000000011")
    @Before fun setup() { f = CreditDatabaseFixture(); runBlocking {
        f.db.cardDao().insert(CardEntity(debit.value, f.owner, "debit-op", null, "Débito", "DEBIT", "PEN", "VISA", "BCP", "1234", createdAt = 1, updatedAt = 1))
    } }
    @After fun teardown() = f.close()
    @Test fun `updates local debit and queues command`() = runBlocking {
        val op = f.operation()
        f.repository.updateDebitCardIdentity(debit, "9876", op).getOrThrow()
        assertEquals("9876", f.db.cardDao().getById(f.owner, debit.value)?.lastFourDigits)
        val queued = f.db.instrumentSyncDao().getByOperationId(f.owner, op.value)!!
        assertEquals("UPDATE_DEBIT_CARD_IDENTITY", queued.commandType)
        assertTrue(queued.payloadJson.contains("9876"))
    }
    @Test fun `rejects invalid digits and credit without mutation`() = runBlocking {
        for (digits in listOf("123", "１２３４", "1234567890123456", "12a4"))
            assertTrue(f.repository.updateDebitCardIdentity(debit, digits, f.operation()).isFailure)
        assertTrue(f.repository.updateDebitCardIdentity(f.cardId, "9999", f.operation()).isFailure)
        assertEquals("1234", f.db.cardDao().getById(f.owner, debit.value)?.lastFourDigits)
        assertEquals(0, f.db.instrumentSyncDao().getPendingCount(f.owner))
    }
}
