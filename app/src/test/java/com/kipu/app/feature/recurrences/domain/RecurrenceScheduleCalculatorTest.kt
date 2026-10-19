package com.kipu.app.feature.recurrences.domain

import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceScheduleCalculatorTest {
    @Test
    fun monthlyEndOfMonthClampsWithoutDriftingFromOriginalAnchor() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 1, 31), RecurrenceScheduleCalculator.occurrenceAt(anchor, "MONTHLY", 1, null, 0))
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceScheduleCalculator.occurrenceAt(anchor, "MONTHLY", 1, null, 1))
        assertEquals(LocalDate.of(2026, 3, 31), RecurrenceScheduleCalculator.occurrenceAt(anchor, "MONTHLY", 1, null, 2))
        assertEquals(LocalDate.of(2026, 4, 30), RecurrenceScheduleCalculator.occurrenceAt(anchor, "MONTHLY", 1, null, 3))
    }

    @Test
    fun annualLeapDayReturnsToLeapDayWhenCalendarAllowsIt() {
        val anchor = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), RecurrenceScheduleCalculator.occurrenceAt(anchor, "ANNUALLY", 1, null, 1))
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceScheduleCalculator.occurrenceAt(anchor, "ANNUALLY", 1, null, 4))
    }

    @Test
    fun weeklyBiweeklyAndCustomIntervalsUseAnchorMultiples() {
        val anchor = LocalDate.of(2026, 1, 2)
        assertEquals(LocalDate.of(2026, 1, 16), RecurrenceScheduleCalculator.occurrenceAt(anchor, "BIWEEKLY", 1, null, 1))
        assertEquals(LocalDate.of(2026, 2, 13), RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 3, "WEEK", 2))
        assertEquals(LocalDate.of(2026, 4, 2), RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 3, "MONTH", 1))
    }

    @Test
    fun identifiesScheduleMembersAndFindsFirstDateAfterInclusiveBoundary() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertTrue(RecurrenceScheduleCalculator.isOccurrence(anchor, "MONTHLY", 1, null, LocalDate.of(2026, 3, 31)))
        assertFalse(RecurrenceScheduleCalculator.isOccurrence(anchor, "MONTHLY", 1, null, LocalDate.of(2026, 3, 30)))
        assertEquals(
            LocalDate.of(2026, 3, 31),
            RecurrenceScheduleCalculator.firstOccurrenceAfter(anchor, "MONTHLY", 1, null, LocalDate.of(2026, 2, 28)),
        )
    }

    @Test
    fun rejectsInvalidIntervalsAndDateOverflowInsteadOfSaturating() {
        val anchor = LocalDate.of(9999, 12, 31)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 0, "WEEK", 1)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            RecurrenceScheduleCalculator.occurrenceAt(anchor, "WEEKLY", 1, null, 1)
        }
    }

    @Test
    fun customIntervalsAcceptDocumentedWeekAndMonthBoundsAndRejectOutsideValues() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 1, 31), RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 1, "WEEK", 0))
        assertEquals(anchor, RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 5200, "WEEK", 0))
        assertEquals(LocalDate.of(2026, 1, 31), RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 1, "MONTH", 0))
        assertEquals(LocalDate.of(2126, 1, 31), RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", 1200, "MONTH", 1))
        for ((count, unit) in listOf(0L to "WEEK", 5201L to "WEEK", 0L to "MONTH", 1201L to "MONTH")) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                RecurrenceScheduleCalculator.occurrenceAt(anchor, "CUSTOM", count, unit, 1)
            }
        }
        assertFalse(RecurrenceScheduleCalculator.isOccurrence(anchor, "CUSTOM", 2, "WEEK", anchor.plusDays(8)))
        assertFalse(RecurrenceScheduleCalculator.isOccurrence(anchor, "CUSTOM", 2, "MONTH", LocalDate.of(2026, 3, 30)))
    }
}
