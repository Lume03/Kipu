package com.kipu.app.feature.movements.data.local

import com.kipu.app.feature.movements.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionMappersTest {
    @Test fun syncedDebtPrincipalWithoutCategoryStillMapsToHistory() {
        val transaction = row().copy(operationKind = "DEBT_PAYMENT", syncStatus = "SYNCED").toDomain()
        assertEquals(MovementType.EXPENSE, transaction.type)
        assertEquals("cash", transaction.sourceAccountId)
    }

    @Test fun previouslyPulledIncomeWithDestinationOnlyCanBeRead() {
        val transaction = row().copy(type = "INCOME", operationKind = "DEBT_PAYMENT",
            sourceAccountId = null, destinationAccountId = "cash", syncStatus = "SYNCED").toDomain()
        assertEquals("cash", transaction.sourceAccountId)
        assertEquals(null, transaction.destinationAccountId)
    }

    @Test fun ordinaryExpenseStillRequiresCategory() {
        assertThrows(IllegalArgumentException::class.java) { row().toDomain() }
    }

    @Test fun debtInterestStillRequiresCategory() {
        assertThrows(IllegalArgumentException::class.java) { row().copy(operationKind = "DEBT_AMORTIZATION").toDomain() }
    }

    private fun row() = TransactionEntity(id = "fixture", userId = "owner", type = "EXPENSE", amountMinor = 1000,
        currencyCode = "PEN", sourceAccountId = "cash", occurredAt = 100, createdAt = 100, updatedAt = 100)
}
