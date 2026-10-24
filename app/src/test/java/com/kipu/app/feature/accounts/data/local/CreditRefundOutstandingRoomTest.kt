package com.kipu.app.feature.accounts.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.TransactionLinkEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreditRefundOutstandingRoomTest {
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
    fun tearDown() = database.close()

    @Test
    fun cardOutstandingInstallmentSubtractsValidLinkedRefundAndPayment() = runBlocking {
        val purchase = transaction("purchase", "EXPENSE", "CARD_PURCHASE", amount = 10_000L)
        val payment = transaction("payment", "TRANSFER", "CARD_PAYMENT", amount = 2_000L)
        val refund = transaction("refund", "INCOME", "REFUND", amount = 3_000L)
        database.movementDao().insertTransactions(listOf(purchase, payment, refund))
        val installment = CreditInstallmentEntity(
            id = "installment",
            userId = userId,
            transactionId = purchase.id,
            installmentNumber = 1,
            dueDate = 1L,
            principalMinor = 10_000L,
            interestMinor = 0L,
            status = "PENDING",
            revision = 1L,
            createdAt = 1L,
            updatedAt = 1L,
        )
        database.creditDao().insertInstallments(listOf(installment))
        database.creditDao().insertAllocations(
            listOf(
                CreditPaymentAllocationEntity(
                    id = "allocation",
                    userId = userId,
                    paymentTransactionId = payment.id,
                    installmentId = installment.id,
                    allocatedMinor = 2_000L,
                    createdAt = 1L,
                ),
            ),
        )
        database.transactionLinkDao().insert(
            TransactionLinkEntity(userId, purchase.id, refund.id, "REFUND", 1L),
        )

        assertEquals(5_000L, database.creditDao().getOutstandingPrincipalForCard(userId, cardId))
        assertEquals(5_000L, database.creditDao().observeOutstandingPrincipalForCard(userId, cardId).first())
        assertEquals(5_000L, database.creditDao().getOutstandingInstallmentsForCard(userId, cardId).single().outstandingMinor)
    }

    private fun transaction(id: String, type: String, operationKind: String, amount: Long) = TransactionEntity(
        id = id,
        userId = userId,
        type = type,
        amountMinor = amount,
        currencyCode = "PEN",
        occurredAt = 1L,
        status = "ACTIVE",
        syncStatus = "SYNCED",
        createdAt = 1L,
        cardId = cardId,
        operationKind = operationKind,
        installmentCount = if (operationKind == "CARD_PURCHASE") 1 else null,
    )
}
