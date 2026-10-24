package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundTransactionTest {
    @Test
    fun `refund income is excluded from operating income`() {
        val refund = Transaction(
            id = "refund-1",
            userId = "owner-1",
            type = MovementType.INCOME,
            amountMinor = 4_000L,
            currency = "PEN",
            sourceAccountId = "cash-1",
            categoryId = "food-1",
            operationKind = "REFUND",
            occurredAt = 1_729_785_600_000L,
            status = TransactionStatus.CONFIRMED,
        )

        assertEquals(emptyMap<String, BigInteger>(), MovementNetFlowCalculator.calculate(listOf(refund)))
    }

    @Test
    fun `partial refund leaves the original expense and records only the refund`() {
        val repository = InMemoryRefundRepository(purchase())
        val result = registerRefund(repository, amountMinor = 4_000L)

        assertTrue(result is RefundTransactionResult.Success)
        assertEquals(1, repository.committed.size)
        assertEquals(4_000L, repository.committed.single().amountMinor)
        assertEquals(MovementType.INCOME, repository.committed.single().type)
        assertEquals("REFUND", repository.committed.single().operationKind)
        assertEquals(10_000L, repository.purchase.amountMinor)
    }

    @Test
    fun `full refund is accepted when no earlier refund exists`() {
        val repository = InMemoryRefundRepository(purchase())
        val result = registerRefund(repository, amountMinor = 10_000L)

        assertTrue(result is RefundTransactionResult.Success)
        assertEquals(10_000L, repository.totalRefundedMinor)
    }

    @Test
    fun `sum of previous refunds bounds the remaining amount`() {
        val repository = InMemoryRefundRepository(purchase(), previouslyRefundedMinor = 4_000L)
        val result = registerRefund(repository, amountMinor = 7_000L)

        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.REFUND_EXCEEDS_REMAINDER, 6_000L),
            result,
        )
        assertTrue(repository.committed.isEmpty())
    }

    @Test
    fun `zero and negative refunds are rejected`() {
        val repository = InMemoryRefundRepository(purchase())

        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.INVALID_AMOUNT),
            registerRefund(repository, amountMinor = 0L),
        )
        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.INVALID_AMOUNT),
            registerRefund(repository, amountMinor = -1L, suffix = "negative"),
        )
    }

    @Test
    fun `refund currency must match the purchase`() {
        val repository = InMemoryRefundRepository(purchase())

        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.CURRENCY_MISMATCH),
            registerRefund(repository, amountMinor = 100L, currency = "USD"),
        )
        assertTrue(repository.committed.isEmpty())
    }

    @Test
    fun `purchase must belong to the requesting owner`() {
        val repository = InMemoryRefundRepository(purchase(userId = "owner-1"))

        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.OWNER_MISMATCH),
            registerRefund(repository, amountMinor = 100L, userId = "owner-2"),
        )
        assertTrue(repository.committed.isEmpty())
    }

    @Test
    fun `transfer and specialized purchase are not refundable`() {
        val transfer = purchase(type = MovementType.TRANSFER)
        val repository = InMemoryRefundRepository(transfer)

        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.PURCHASE_NOT_ELIGIBLE),
            registerRefund(repository, amountMinor = 100L),
        )
        assertTrue(repository.committed.isEmpty())
    }

    @Test
    fun `single payment credit card purchase is eligible but installments are not`() {
        val cardPurchase = purchase(
            sourceAccountId = null,
            cardId = "card-1",
            operationKind = "CARD_PURCHASE",
            installmentCount = 1,
        )
        val accepted = InMemoryRefundRepository(cardPurchase)
        assertTrue(registerRefund(accepted, amountMinor = 100L) is RefundTransactionResult.Success)
        assertEquals("card-1", accepted.committed.single().cardId)
        assertEquals(null, accepted.committed.single().sourceAccountId)

        val installments = InMemoryRefundRepository(cardPurchase.copy(installmentCount = 3))
        assertEquals(
            RefundTransactionResult.Rejected(RefundTransactionError.PURCHASE_NOT_ELIGIBLE),
            registerRefund(installments, amountMinor = 100L),
        )
    }

    @Test
    fun `exact replay returns the original result before checking the exhausted remainder`() {
        val repository = InMemoryRefundRepository(purchase())
        val command = command(amountMinor = 10_000L)
        val first = RegisterRefundTransaction(repository)(command)
        val second = RegisterRefundTransaction(repository)(command)

        assertTrue(first is RefundTransactionResult.Success)
        assertTrue(second is RefundTransactionResult.Success)
        assertTrue((second as RefundTransactionResult.Success).isDuplicate)
        assertEquals(1, repository.committed.size)
    }

    @Test
    fun `reusing an idempotency key with a different payload conflicts`() {
        val repository = InMemoryRefundRepository(purchase())
        val useCase = RegisterRefundTransaction(repository)
        useCase(command(amountMinor = 4_000L))

        val conflict = useCase(command(amountMinor = 5_000L))

        assertEquals(
            RefundTransactionResult.Conflict(RefundTransactionError.IDEMPOTENCY_CONFLICT),
            conflict,
        )
        assertEquals(1, repository.committed.size)
    }

    private fun registerRefund(
        repository: InMemoryRefundRepository,
        amountMinor: Long,
        currency: String = "PEN",
        userId: String = "owner-1",
        suffix: String = "default",
    ): RefundTransactionResult = RegisterRefundTransaction(repository)(
        command(amountMinor, currency, userId, suffix),
    )

    private fun command(
        amountMinor: Long,
        currency: String = "PEN",
        userId: String = "owner-1",
        suffix: String = "default",
    ) = RefundTransactionCommand(
        idempotencyKey = "key-$suffix",
        userId = userId,
        purchaseTransactionId = "purchase-1",
        refundTransactionId = "refund-$suffix",
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = 1_729_785_600_000L,
    )

    private fun purchase(
        userId: String = "owner-1",
        type: MovementType = MovementType.EXPENSE,
        sourceAccountId: String? = "cash-1",
        cardId: String? = null,
        operationKind: String? = null,
        installmentCount: Int? = null,
    ) = Transaction(
        id = "purchase-1",
        userId = userId,
        type = type,
        amountMinor = 10_000L,
        currency = "PEN",
        sourceAccountId = sourceAccountId,
        destinationAccountId = if (type == MovementType.TRANSFER) "cash-2" else null,
        categoryId = "category-1",
        merchantId = "merchant-1",
        operationKind = operationKind,
        installmentCount = installmentCount,
        cardId = cardId,
        occurredAt = 1_729_699_200_000L,
        status = TransactionStatus.CONFIRMED,
    )

    private class InMemoryRefundRepository(
        val purchase: Transaction,
        private var previouslyRefundedMinor: Long = 0L,
    ) : MovementRefundRepository {
        val committed = mutableListOf<Transaction>()
        private val receipts = mutableMapOf<String, Pair<String, Transaction>>()
        val totalRefundedMinor: Long get() = previouslyRefundedMinor + committed.sumOf { it.amountMinor }

        override suspend fun getPurchaseTransaction(userId: String, transactionId: String) = purchase

        override suspend fun getRefundedAmount(userId: String, purchaseTransactionId: String) = totalRefundedMinor

        override suspend fun findRefundReplay(
            userId: String,
            idempotencyKey: String,
            requestHash: String,
        ): RefundTransactionResult? {
            val stored = receipts[idempotencyKey] ?: return null
            return if (stored.first == requestHash) {
                RefundTransactionResult.Success(stored.second, isDuplicate = true)
            } else {
                RefundTransactionResult.Conflict(RefundTransactionError.IDEMPOTENCY_CONFLICT)
            }
        }

        override suspend fun commitRefundAtomic(
            command: RefundTransactionCommand,
            requestHash: String,
            refund: Transaction,
        ): RefundTransactionResult {
            committed += refund
            receipts[command.idempotencyKey] = requestHash to refund
            return RefundTransactionResult.Success(refund)
        }

        override suspend fun getRefundRejection(userId: String, refundTransactionId: String) = null

        override suspend fun voidRejectedRefund(userId: String, refundTransactionId: String): RefundTransactionResult =
            RefundTransactionResult.Rejected(RefundTransactionError.REFUND_NOT_REJECTED)
    }
}
