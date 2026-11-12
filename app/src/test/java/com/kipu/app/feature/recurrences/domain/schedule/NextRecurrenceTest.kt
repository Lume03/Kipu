package com.kipu.app.feature.recurrences.domain.schedule

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextRecurrenceTest {
    @Test fun `monthly 31 weekly and paused rules calculate correctly`() {
        fun date(value: String) = LocalDate.parse(value)
        assertEquals(date("2026-02-28"), RecurrenceScheduleCalculator.nextOccurrence(date("2026-01-31"), "MONTHLY", 1, null, true, null, date("2026-02-01")))
        assertEquals(date("2026-03-31"), RecurrenceScheduleCalculator.nextOccurrence(date("2026-01-31"), "MONTHLY", 1, null, true, null, date("2026-03-01")))
        assertEquals(date("2026-02-08"), RecurrenceScheduleCalculator.nextOccurrence(date("2026-02-01"), "WEEKLY", 1, null, true, null, date("2026-02-02")))
        assertNull(RecurrenceScheduleCalculator.nextOccurrence(date("2026-02-01"), "WEEKLY", 1, null, false, null, date("2026-02-02")))
        assertNull(RecurrenceScheduleCalculator.nextOccurrence(date("2026-02-01"), "WEEKLY", 1, null, true, date("2026-02-07"), date("2026-02-02")))
    }
}
