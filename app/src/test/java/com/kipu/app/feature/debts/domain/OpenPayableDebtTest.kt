package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDescriptionState
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPayableDebtTest {
    @Test
    fun `descriptive edit cannot change principal currency status or opening date`() {
        val original = DebtDescriptionState(
            debtId = "debt-1",
            counterpartyName = "Proveedor",
            principalMinor = 5_000L,
            currencyCode = "PEN",
            openedOn = LocalDate.parse("2026-10-01"),
            dueDate = null,
            notes = null,
            status = DebtLifecycleStatus.ACTIVE,
            revision = 1L,
        )
        val result = DebtDescriptionEditor.apply(
            original,
            DebtDescriptionPatch(
                counterpartyName = "Proveedor nuevo",
                dueDate = LocalDate.parse("2026-12-01"),
                notes = "Renovación del acuerdo",
                expectedRevision = 1L,
            ),
        )

        assertTrue(result is DebtDetailsEditResult.Applied)
        val edited = (result as DebtDetailsEditResult.Applied).state
        assertEquals(5_000L, edited.principalMinor)
        assertEquals("PEN", edited.currencyCode)
        assertEquals(LocalDate.parse("2026-10-01"), edited.openedOn)
        assertEquals(DebtLifecycleStatus.ACTIVE, edited.status)
        assertEquals("Proveedor nuevo", edited.counterpartyName)
        assertEquals(2L, edited.revision)
    }
}
