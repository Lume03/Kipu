package com.kipu.app.feature.accounts.data

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.data.local.*
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.domain.CreditPaymentAllocator
import com.kipu.app.feature.accounts.domain.DuePrincipalInstallment
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.*
import com.kipu.app.feature.movements.data.local.*
import com.kipu.app.testing.CreditDatabaseFixture
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreditPurchaseContractRoomTest {
    private lateinit var f: CreditDatabaseFixture
    private val terms = PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, 6, 4500, PurchaseRateSource.USER_ENTERED)
    private val instant = Instant.parse("2026-06-26T03:00:00Z") // June 25 in Lima
    @Before fun setUp() { f = CreditDatabaseFixture() }
    @After fun tearDown() = f.close()
    private suspend fun purchase(operation: OperationId = f.operation(), accepted: PurchaseTerms = terms): String {
        ConfirmCreditPurchase(f.repository)(f.cardId, Money(120000, Currency.PEN), "Tienda", instant,
            accepted.installmentsCount, operation, f.categoryId, terms = accepted).getOrThrow()
        return requireNotNull(requireNotNull(f.db.movementDao().getReceipt(f.owner, operation.value)).transactionId)
    }
    @Test fun `confirmed purchase persists exact terms principal-only ledger and simulator schedule`() = runBlocking {
        val id = purchase()
        val card = f.repository.observeCardById(f.cardId).first() as CreditCard
        val preview = SimulateInstallments()(PurchaseCandidate("preview", f.cardId, Money(120000, Currency.PEN), "Tienda", instant), card, terms = terms)
        val rows = f.db.creditDao().getInstallmentsForTransaction(f.owner, id).sortedBy { it.installmentNumber }
        assertEquals(6, rows.size)
        rows.zip(preview.schedule).forEach { (row, expected) ->
            assertEquals(expected.principalPortion.minorUnits, row.principalMinor)
            assertEquals(expected.interestPortion.minorUnits, row.interestMinor)
            assertEquals(expected.dueDate.toEpochDay(), row.dueDate)
            assertEquals(expected.billingClosingDate?.toEpochDay(), row.billingClosingDate)
            assertEquals(terms.purchaseType.name, row.purchaseType)
            assertEquals(terms.financing.name, row.financing)
            assertEquals(terms.installmentsCount, row.installmentsCount)
            assertEquals(terms.teaBps, row.teaBps)
            assertEquals(terms.rateSource?.name, row.rateSource)
            assertEquals(terms.roundingPolicy.name, row.roundingPolicy)
        }
        val ledger = f.db.movementDao().getLedgerEntriesForTransaction(f.owner, id)
        assertEquals(-120000L, ledger.single().signedAmountMinor)
        assertEquals(120000L, f.db.movementDao().getTransactionById(f.owner, id)?.amountMinor)
        assertEquals(120000L, f.db.creditDao().getOutstandingBalanceForCard(f.owner, f.cardId.value))
    }
    @Test fun `estimated interest does not reduce available credit or inflate utilization or FIFO payments`() = runBlocking {
        val id = purchase()
        val summary = ObserveFinancialDashboard(f.repository)().first().creditCards.single()
        assertEquals(120000L, summary.debt.minorUnits)
        assertEquals(380000L, summary.availableCredit.minorUnits)
        assertEquals(24.0, summary.utilizationPercentage, 0.00001)
        val source = AccountId.generate()
        val account = Account(source, UserId(f.owner), "Ahorro", AccountType.SAVINGS, Currency.PEN,
            initialBalance = Money(200000, Currency.PEN), openedAt = instant)
        f.repository.createLiquidAccount(account, f.operation()).getOrThrow()
        val firstPrincipal = f.db.creditDao().getInstallmentsForTransaction(f.owner, id).first().principalMinor
        f.repository.payCreditCard(f.cardId, source, Money(firstPrincipal, Currency.PEN), instant, f.operation()).getOrThrow()
        val balances = f.db.creditDao().observeInstallmentBalancesForCard(f.owner, f.cardId.value).first()
        assertTrue(balances.first().installment.interestMinor > 0)
        assertEquals(firstPrincipal, balances.first().paidMinor)
        assertEquals(0L, balances.first().outstandingMinor)
        assertEquals(0L, balances[1].paidMinor)
        assertEquals(120000L - firstPrincipal, f.repository.observeCardDebt(f.cardId).first().minorUnits)
        f.repository.payCreditCard(f.cardId, source, Money(120001L - firstPrincipal, Currency.PEN), instant, f.operation()).also {
            assertTrue(it.isFailure)
        }
        f.repository.payCreditCard(f.cardId, source, Money(120000L - firstPrincipal, Currency.PEN), instant, f.operation()).getOrThrow()
        assertEquals(0L, f.repository.observeCardDebt(f.cardId).first().minorUnits)
        assertTrue(f.db.creditDao().getInstallmentsForTransaction(f.owner, id).all { it.status == "PAID" })
    }
    @Test fun `idempotent retry does not duplicate schedule ledger or outbox`() = runBlocking {
        val operation = f.operation()
        val id = purchase(operation)
        assertEquals(id, purchase(operation))
        assertEquals(6, f.db.creditDao().getInstallmentsForTransaction(f.owner, id).size)
        assertEquals(1, f.db.movementDao().getLedgerEntriesForTransaction(f.owner, id).size)
        assertEquals(1, f.db.instrumentSyncDao().getPendingCount(f.owner))
        val request = Json.decodeFromString<CreditCommandRequestDto>(requireNotNull(f.db.instrumentSyncDao().getByOperationId(f.owner, operation.value)).payloadJson)
        assertEquals(4500, request.transaction.teaBps)
        assertEquals("WITH_INTEREST", request.transaction.financing)
        assertEquals("FIRST_INSTALLMENT_RESIDUAL", request.transaction.roundingPolicy)
    }
    @Test fun `reusing operation with changed terms fails without altering accepted purchase`() = runBlocking {
        val operation = f.operation(); val id = purchase(operation)
        val result = ConfirmCreditPurchase(f.repository)(f.cardId, Money(120000, Currency.PEN), "Tienda", instant,
            6, operation, f.categoryId, terms = PurchaseTerms.interestFree(6))
        assertTrue(result.isFailure)
        assertTrue(f.db.creditDao().getInstallmentsForTransaction(f.owner, id).all { it.teaBps == 4500 })
    }
    @Test fun `editing personal card rate leaves previously accepted obligation unchanged`() = runBlocking {
        val id = purchase()
        val before = f.db.creditDao().getInstallmentsForTransaction(f.owner, id)
        f.db.cardDao().updatePersonalTea(f.owner, f.cardId.value, 9900, 2)
        assertEquals(before, f.db.creditDao().getInstallmentsForTransaction(f.owner, id))
    }
    @Test fun `direct purchase stores one interest free installment`() = runBlocking {
        val id = purchase(accepted = PurchaseTerms.interestFree())
        val row = f.db.creditDao().getInstallmentsForTransaction(f.owner, id).single()
        assertEquals("DIRECT", row.purchaseType); assertEquals("INTEREST_FREE", row.financing)
        assertNull(row.teaBps); assertEquals(0L, row.interestMinor)
        assertEquals(120000L, row.principalMinor)
    }
    @Test fun `FIFO payment excludes estimated interest and statuses reverse on void then refunds apply once`() = runBlocking {
        val id = purchase()
        val dao = f.db.creditDao()
        val rows = dao.getOutstandingInstallmentsForCard(f.owner, f.cardId.value)
        val paymentMinor = 30000L
        val allocations = CreditPaymentAllocator.allocate(paymentMinor, rows.map {
            DuePrincipalInstallment(it.installment.id, it.installment.dueDate, it.purchaseOccurredAt, it.installment.installmentNumber, it.outstandingMinor)
        })
        val payment = TransactionEntity("payment", f.owner, "TRANSFER", paymentMinor, "PEN", occurredAt = instant.toEpochMilli(),
            cardId = f.cardId.value, operationKind = "CARD_PAYMENT", status = "ACTIVE", syncStatus = "PENDING", createdAt = 1)
        f.db.movementDao().insertTransaction(payment)
        dao.insertAllocations(allocations.mapIndexed { index, a -> CreditPaymentAllocationEntity("alloc$index", f.owner, payment.id, a.installmentId, a.allocatedMinor, 1) })
        dao.refreshInstallmentStatuses(f.owner)
        assertEquals(listOf("PAID", "PARTIAL", "PENDING", "PENDING", "PENDING", "PENDING"),
            dao.getInstallmentsForTransaction(f.owner, id).sortedBy { it.installmentNumber }.map { it.status })
        assertEquals(90000L, dao.getOutstandingBalanceForCard(f.owner, f.cardId.value))
        f.db.openHelper.writableDatabase.execSQL("UPDATE transactions SET status='VOIDED' WHERE id='payment'")
        dao.refreshInstallmentStatuses(f.owner)
        assertTrue(dao.getInstallmentsForTransaction(f.owner, id).all { it.status == "PENDING" })
        assertEquals(120000L, dao.getOutstandingBalanceForCard(f.owner, f.cardId.value))
        val refund = payment.copy(id = "refund", type = "INCOME", operationKind = "REFUND", amountMinor = 30000)
        f.db.movementDao().insertTransaction(refund)
        f.db.transactionLinkDao().insert(TransactionLinkEntity(f.owner, id, refund.id, "REFUND", 1))
        dao.refreshInstallmentStatuses(f.owner)
        assertEquals(90000L, dao.getOutstandingBalanceForCard(f.owner, f.cardId.value))
        val balances = dao.observeInstallmentBalancesForCard(f.owner, f.cardId.value).first()
        assertEquals(30000L, balances.sumOf { it.refundedMinor })
        assertEquals(listOf("PAID", "PARTIAL"), balances.take(2).map { it.installment.status })
        f.db.openHelper.writableDatabase.execSQL("UPDATE transactions SET status='VOIDED' WHERE id='refund'")
        assertEquals(120000L, dao.getOutstandingBalanceForCard(f.owner, f.cardId.value))
        val overflow = runCatching { CreditPaymentAllocator.allocate(120001, rows.map {
            DuePrincipalInstallment(it.installment.id, it.installment.dueDate, it.purchaseOccurredAt, it.installment.installmentNumber, it.outstandingMinor)
        }) }
        assertTrue(overflow.exceptionOrNull() is IllegalArgumentException)
    }
}
