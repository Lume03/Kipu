package com.kipu.app.feature.goals.presentation

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.goals.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class GoalContributionChartDataTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val zone = ZoneId.of("America/Lima")
    private fun event(id: String, date: String, amount: Long = 10000L, type: GoalEventType = GoalEventType.ALLOCATION, voided: Boolean = false) =
        GoalEvent(id, "u", "g", type, Money(amount, Currency.PEN), "a", id,
            transactionVoided = voided, occurredAt = Instant.parse(date))
    private fun goal(date: LocalDate?) = Goal("g", "u", "Laptop", Money(100000, Currency.PEN),
        targetDate = date, status = GoalStatus.ACTIVE, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test fun yearHasTwelveMonthsIncludingEmptyMonthsAndExcludesReleasesAndVoids() {
        val events = listOf(event("a", "2026-10-25T20:00:00Z"),
            event("b", "2026-10-26T20:00:00Z", -5000, GoalEventType.RELEASE),
            event("v", "2026-10-26T20:00:00Z", voided = true), event("last", "2025-10-26T20:00:00Z"))
        val buckets = goalChartBuckets(events, GoalChartScale.MONTH, today, zone)
        assertEquals(12, buckets.size)
        assertEquals(10000L, buckets[9].minor)
        assertEquals(10000L, buckets.sumOf { it.minor })
        assertEquals(0L, buckets.first().minor)
    }
    @Test fun weeklyBucketsCoverEachDayOfTheSelectedMonthOnce() {
        val dates = (1..31).map { day -> event("d$day", "2026-10-${day.toString().padStart(2, '0')}T12:00:00Z", 100) }
        val buckets = goalChartBuckets(dates, GoalChartScale.WEEK, today, zone)
        assertEquals(today.withDayOfMonth(1), buckets.first().start)
        assertEquals(LocalDate.of(2026, 11, 1), buckets.last().endExclusive)
        assertEquals(3100L, buckets.sumOf { it.minor })
        buckets.zipWithNext().forEach { (a, b) -> assertEquals(a.endExclusive, b.start) }
    }
    @Test fun dailyBucketsUseLocalDateAndSupportWeeksCrossingYears() {
        val buckets = goalChartBuckets(listOf(event("near", "2027-01-01T02:00:00Z")), GoalChartScale.DAY, LocalDate.of(2027, 1, 1), zone)
        assertEquals(7, buckets.size)
        assertEquals(LocalDate.of(2026, 12, 28), buckets.first().start)
        assertEquals(10000L, buckets.first { it.start == LocalDate.of(2026, 12, 31) }.minor)
    }
    @Test fun suggestionUsesRemainingFundsAndRoundsMinorUnitsUp() {
        assertEquals(16667L, goalMonthlySuggestion(goal(LocalDate.of(2026, 12, 31)), 50000, today))
        assertEquals(50000L, goalMonthlySuggestion(goal(today), 50000, today))
    }
    @Test fun noSuggestionForUndatedPastCompletedOrFullyFundedGoal() {
        assertNull(goalMonthlySuggestion(goal(null), 0, today))
        assertNull(goalMonthlySuggestion(goal(today.minusDays(1)), 0, today))
        assertNull(goalMonthlySuggestion(goal(today).copy(status = GoalStatus.COMPLETED), 100000, today))
        assertNull(goalMonthlySuggestion(goal(today), 100000, today))
    }
}
