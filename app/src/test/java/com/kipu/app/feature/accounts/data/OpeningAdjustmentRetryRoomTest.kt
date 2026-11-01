package com.kipu.app.feature.accounts.data

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.RecordOpeningAdjustment
import com.kipu.app.testing.CreditDatabaseFixture
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpeningAdjustmentRetryRoomTest {
    private lateinit var f: CreditDatabaseFixture
    private lateinit var scheduler: InstrumentSyncScheduler
    private lateinit var repository: OfflineFirstFinancialInstrumentsRepository
    private lateinit var account: Account
    private val date = Instant.parse("2026-06-25T12:00:00Z")
    @Before fun setUp() { f = CreditDatabaseFixture(); scheduler = mockk(relaxed = true)
        repository = OfflineFirstFinancialInstrumentsRepository(f.db, mockk(relaxed = true), f.db.accountDao(), f.db.cardDao(),
            f.db.financialMovementDao(), f.db.instrumentSyncDao(), f.session, scheduler)
        account = Account(AccountId.generate(), UserId(f.owner), "Ahorro", AccountType.SAVINGS, Currency.PEN,
            initialBalance = Money(10000, Currency.PEN), openedAt = date)
    }
    @After fun tearDown() = f.close()
    private suspend fun create() = repository.createLiquidAccount(account, f.operation()).getOrThrow()

    @Test fun `replayed opening adjustment remains successful after outbox is removed`() = runBlocking {
        create(); val operation = f.operation()
        repository.recordOpeningAdjustment(account.id, Money(20000, Currency.PEN), date, operation).getOrThrow()
        val before = f.db.financialMovementDao().observeByAccount(f.owner, account.id.value).first()
        f.db.openHelper.writableDatabase.execSQL("DELETE FROM instrument_sync_outbox WHERE operation_id = ?", arrayOf(operation.value))
        repository.recordOpeningAdjustment(account.id, Money(20000, Currency.PEN), date, operation).getOrThrow()
        assertEquals(before, f.db.financialMovementDao().observeByAccount(f.owner, account.id.value).first())
        assertEquals(20000L, f.db.financialMovementDao().getAccountBalance(f.owner, account.id.value))
    }
    @Test fun `operation reuse with changed amount date account or currency conflicts without mutation`() = runBlocking {
        create(); val operation = f.operation()
        repository.recordOpeningAdjustment(account.id, Money(20000, Currency.PEN), date, operation).getOrThrow()
        for ((id, amount, effective) in listOf(
            Triple(account.id, Money(20001, Currency.PEN), date),
            Triple(account.id, Money(20000, Currency.PEN), date.plusSeconds(1)),
            Triple(AccountId.generate(), Money(20000, Currency.PEN), date),
            Triple(account.id, Money(20000, Currency.USD), date))) {
            val result = repository.recordOpeningAdjustment(id, amount, effective, operation)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Idempotency conflict"))
        }
        assertEquals(3, f.db.financialMovementDao().observeByAccount(f.owner, account.id.value).first().size)
        assertEquals(2, f.db.instrumentSyncDao().getPendingCount(f.owner))
    }
    @Test fun `account creation and adjustment succeed when scheduling fails after commit`() = runBlocking {
        every { scheduler.scheduleSync(any()) } throws IllegalStateException("scheduler unavailable")
        create()
        repository.recordOpeningAdjustment(account.id, Money(20000, Currency.PEN), date, f.operation()).getOrThrow()
        assertEquals(20000L, f.db.financialMovementDao().getAccountBalance(f.owner, account.id.value))
        assertEquals(2, f.db.instrumentSyncDao().getPendingCount(f.owner))
    }
    @Test fun `scheduler cancellation is propagated`() = runBlocking {
        every { scheduler.scheduleSync(any()) } throws CancellationException("cancelled")
        try { create(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertNotNull(f.db.accountDao().getById(f.owner, account.id.value))
    }
    @Test fun `currency mismatch rejected by use case and repository without effects`() = runBlocking {
        create()
        assertTrue(RecordOpeningAdjustment(repository)(account.id, Money(20000, Currency.USD), date).isFailure)
        assertTrue(repository.recordOpeningAdjustment(account.id, Money(20000, Currency.USD), date, f.operation()).isFailure)
        assertEquals(1, f.db.financialMovementDao().observeByAccount(f.owner, account.id.value).first().size)
        assertEquals(1, f.db.instrumentSyncDao().getPendingCount(f.owner))
        assertEquals(10000L, f.db.financialMovementDao().getAccountBalance(f.owner, account.id.value))
    }
}
