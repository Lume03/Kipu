package com.kipu.app.feature.goals.presentation

import com.kipu.app.feature.goals.domain.model.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal enum class GoalChartScale(val label: String) { DAY("Día"), WEEK("Semana"), MONTH("Mes") }
internal data class GoalChartBucket(val start: LocalDate, val endExclusive: LocalDate, val minor: Long)

internal fun goalChartBuckets(events: List<GoalEvent>, scale: GoalChartScale, anchor: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()): List<GoalChartBucket> {
    val contributions = events.filter { !it.transactionVoided && it.type in setOf(GoalEventType.ALLOCATION, GoalEventType.DIRECT_DEPOSIT) }
    val ranges = when (scale) {
        GoalChartScale.MONTH -> (1..12).map { month -> LocalDate.of(anchor.year, month, 1).let { it to it.plusMonths(1) } }
        GoalChartScale.DAY -> {
            val first = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (0..6).map { first.plusDays(it.toLong()) to first.plusDays(it + 1L) }
        }
        GoalChartScale.WEEK -> {
            val first = YearMonth.from(anchor).atDay(1)
            val end = first.plusMonths(1)
            generateSequence(first) { it.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).takeIf { date -> date < end } }
                .map { it to minOf(it.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), end) }.toList()
        }
    }
    return ranges.map { (start, end) -> GoalChartBucket(start, end, contributions.asSequence()
        .filter { it.occurredAt.atZone(zone).toLocalDate().let { date -> date >= start && date < end } }
        .fold(0L) { sum, event -> Math.addExact(sum, event.delta.minorUnits) }) }
}

internal fun goalMonthlySuggestion(goal: Goal, reservedMinor: Long, today: LocalDate): Long? {
    val date = goal.targetDate ?: return null
    if (date < today || goal.status != GoalStatus.ACTIVE) return null
    val remaining = (goal.target.minorUnits - reservedMinor).coerceAtLeast(0)
    if (remaining == 0L) return null
    val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(date)) + 1
    return remaining / months + if (remaining % months == 0L) 0L else 1L
}
