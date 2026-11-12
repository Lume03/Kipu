package com.kipu.app.feature.accounts.presentation.detail

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CardCycleDateTest {
    @Test fun `closing day belongs to closing cycle across February`() {
        val closing = currentCardCycle(20, LocalDate.parse("2026-02-20"))
        assertEquals(LocalDate.parse("2026-01-21"), closing.consumptionStartDate)
        assertEquals(LocalDate.parse("2026-02-20"), closing.consumptionEndDate)
        assertEquals(LocalDate.parse("2026-02-21"), currentCardCycle(20, LocalDate.parse("2026-02-21")).consumptionStartDate)
    }
}
