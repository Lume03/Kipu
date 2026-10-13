package com.kipu.app.feature.accounts.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.movements.data.local.TransactionEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditCardLedgerQueriesTest {
    private lateinit var database: KipuDatabase
    private val userId = UUID.randomUUID().toString()
    private val cardId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun nextPaymentSuggestionSumsUnpaidPrincipalForEarliestDueDate() = runTest {
        val nextDue = LocalDate.parse("2026-11-10").toEpochDay()
        val laterDue = LocalDate.parse("2026-12-10").toEpochDay()
        val firstPurchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_000L,
            operationKind = "CARD_PURCHASE",
        )
        val secondPurchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_001L,
            operationKind = "CARD_PURCHASE",
        )
        val payment = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_002L,
            operationKind = "CARD_PAYMENT",
            type = "TRANSFER",
        )
        val rejectedPurchase = transaction(
            id = UUID.randomUUID().toString(),
            occurredAt = 1_800_000_000_003L,
            operationKind = "CARD_PURCHASE",
            syncStatus = "FAILED_PERMANENT",
        )
        database.movementDao().insertTransactions(listOf(firstPurchase, secondPurchase, payment, rejectedPurchase))

        val firstInstallment = installment(firstPurchase.id, "first", nextDue, principal = 2_500L, status = "PARTIAL")
        val secondInstallment = installment(secondPurchase.id, "second", nextDue, principal = 3_000L)
        val futureInstallment = installment(secondPurchase.id, "future", laterDue, principal = 5_000L, number = 2)
        val rejectedInstallment = installment(rejectedPurchase.id, "rejected", LocalDate.parse("2026-10-10").toEpochDay(), 8_000L)
        database.creditDao().insertInstallments(
            listOf(firstInstallment, secondInstallment, futureInstallment, rejectedInstallment),
        )
        database.creditDao().insertAllocation(
            CreditPaymentAllocationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                paymentTransactionId = payment.id,
                installmentId = firstInstallment.id,
                allocatedMinor = 1_000L,
                createdAt = 1_800_000_000_004L,
            ),
        )

        val outstanding = database.creditDao().getOutstandingInstallmentsForCard(userId, cardId)

        assertEquals(3, outstanding.size)
        assertEquals(nextDue, outstanding.first().installment.dueDate)
        assertEquals(4_500L, outstanding.takeWhile { it.installment.dueDate == nextDue }.sumOf { it.outstandingMinor })
        assertEquals(5_000L, outstanding.last().outstandingMinor)
    }

    @Test
    fun cardMovementQueryReturnsPostedMovementsNewestFirst() = runTest {
        val purchase = cardMovement(UUID.randomUUID().toString(), "CREDIT_PURCHASE", 1_800_000_000_000L)
        val payment = cardMovement(UUID.randomUUID().toString(), "CARD_PAYMENT_CASH", 1_800_000_000_001L)
        val pending = cardMovement(UUID.randomUUID().toString(), "CREDIT_PURCHASE", 1_800_000_000_002L, status = "PENDING")
        database.financialMovementDao().insertRaw(purchase)
        database.financialMovementDao().insertRaw(payment)
        database.financialMovementDao().insertRaw(pending)

        val rows = database.financialMovementDao().observeByCard(userId, cardId).first()

        assertEquals(listOf(payment.id, purchase.id), rows.map { it.id })
    }

    private fun cardMovement(id: String, kind: String, occurredAt: Long, status: String = "POSTED") =
        FinancialMovementEntity(
            id = id,
            operationId = UUID.randomUUID().toString(),
            operationSequence = 0,
            userId = userId,
            kind = kind,
            amountMinorUnits = 2_500L,
            currency = "PEN",
            cardId = cardId,
            effectiveAt = occurredAt,
            status = status,
            createdAt = occurredAt,
        )

    private fun transaction(
        id: String,
        occurredAt: Long,
        operationKind: String,
        type: String = "EXPENSE",
        syncStatus: String = "PENDING",
        merchantId: String? = null,
    ) = TransactionEntity(
        id = id,
        userId = userId,
        type = type,
        amountMinor = 2_500L,
        currencyCode = "PEN",
        merchantId = merchantId,
        occurredAt = occurredAt,
        status = "ACTIVE",
        syncStatus = syncStatus,
        createdAt = occurredAt,
        cardId = cardId,
        operationKind = operationKind,
    )

    private fun installment(
        transactionId: String,
        suffix: String,
        dueDate: Long,
        principal: Long,
        status: String = "PENDING",
        number: Int = 1,
    ) = CreditInstallmentEntity(
        id = "${transactionId}_$suffix",
        userId = userId,
        transactionId = transactionId,
        installmentNumber = number,
        dueDate = dueDate,
        principalMinor = principal,
        interestMinor = 0L,
        status = status,
        revision = 1L,
        createdAt = 1_800_000_000_000L,
        updatedAt = 1_800_000_000_000L,
    )
}
