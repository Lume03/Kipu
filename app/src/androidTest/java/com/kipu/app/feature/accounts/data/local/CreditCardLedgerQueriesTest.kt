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

        assertEquals(listOf(nextDue, nextDue, laterDue), outstanding.map { it.installment.dueDate })
        assertEquals(4_500L, outstanding
            .filter { it.installment.dueDate == nextDue }
            .sumOf { it.outstandingMinor })
    }

    @Test
    fun cardLedgerQueryIncludesPostedPurchasesAndPaymentsWithMerchantReference() = runTest {
        val merchantId = UUID.randomUUID().toString()
        val purchase = cardMovement(
            id = UUID.randomUUID().toString(),
            kind = "CREDIT_PURCHASE",
            occurredAt = 1_800_000_000_000L,
            merchantId = merchantId,
        )
        val payment = cardMovement(
            id = UUID.randomUUID().toString(),
            kind = "CARD_PAYMENT",
            occurredAt = 1_800_000_000_001L,
        )
        val rejected = cardMovement(
            id = UUID.randomUUID().toString(),
            kind = "CREDIT_PURCHASE",
            occurredAt = 1_800_000_000_002L,
            status = "VOIDED",
        )
        database.financialMovementDao().insertAll(listOf(purchase, payment, rejected))

        val rows = database.financialMovementDao().observeByCard(userId, cardId).first()

        assertEquals(listOf("CARD_PAYMENT", "CREDIT_PURCHASE"), rows.map { it.kind })
        assertEquals(merchantId, rows.last().merchantId)
    }

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

    private fun cardMovement(
        id: String,
        kind: String,
        occurredAt: Long,
        merchantId: String? = null,
        status: String = "POSTED",
    ) = FinancialMovementEntity(
        id = id,
        operationId = id,
        operationSequence = 0,
        userId = userId,
        kind = kind,
        amountMinorUnits = 2_500L,
        currency = "PEN",
        cardId = cardId,
        effectiveAt = occurredAt,
        status = status,
        merchantId = merchantId,
        createdAt = occurredAt,
    )
}
