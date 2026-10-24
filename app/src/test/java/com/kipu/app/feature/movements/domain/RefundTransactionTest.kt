package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import java.math.BigInteger
import org.junit.Assert.assertEquals
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
}
