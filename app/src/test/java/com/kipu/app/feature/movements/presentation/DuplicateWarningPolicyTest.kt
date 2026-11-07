package com.kipu.app.feature.movements.presentation

import com.kipu.app.feature.movements.domain.model.MovementType
import org.junit.Assert.*
import org.junit.Test

class DuplicateWarningPolicyTest {
    @Test fun proposedTypeHasPriorityForEveryMovementType() {
        MovementType.entries.forEach { type -> assertEquals(type, duplicateWarningType(type, MovementType.EXPENSE)) }
    }
    @Test fun missingProposalFallsBackToExistingThenExpense() {
        MovementType.entries.forEach { type -> assertEquals(type, duplicateWarningType(null, type)) }
        assertEquals(MovementType.EXPENSE, duplicateWarningType(null, null))
    }
    @Test fun amountsPreserveDirection() {
        assertEquals("+", duplicateAmountSign(MovementType.INCOME))
        assertEquals("−", duplicateAmountSign(MovementType.EXPENSE))
        assertEquals("", duplicateAmountSign(MovementType.TRANSFER))
    }
}
