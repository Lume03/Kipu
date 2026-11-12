package com.kipu.app.feature.analytics.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsPreviousMonthTest {
    @Test fun `closed February compares all of January`() {
        val period = AnalyticsPeriod(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"), ZoneId.of("America/Lima"))
        assertEquals(LocalDate.parse("2026-01-31"), period.previous().endInclusive)
    }
}
