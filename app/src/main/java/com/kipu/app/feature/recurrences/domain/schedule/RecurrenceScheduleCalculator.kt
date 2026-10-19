package com.kipu.app.feature.recurrences.domain.schedule

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Deterministic recurrence sequence calculation anchored to the original start date. */
object RecurrenceScheduleCalculator {
    private const val MAX_DATE_YEAR = 9999

    fun occurrenceAt(anchor: LocalDate, frequency: String, intervalCount: Long, customIntervalUnit: String?, occurrenceIndex: Long): LocalDate {
        requireCommonDate(anchor)
        require(occurrenceIndex >= 0) { "Occurrence index cannot be negative" }
        val cadence = cadence(frequency, intervalCount, customIntervalUnit)
        return try {
            val date = when (cadence) {
                is Cadence.Days -> anchor.plusDays(Math.multiplyExact(cadence.amount, occurrenceIndex))
                is Cadence.Months -> monthOccurrence(anchor, Math.multiplyExact(cadence.amount, occurrenceIndex))
                is Cadence.Years -> yearOccurrence(anchor, Math.multiplyExact(cadence.amount, occurrenceIndex))
            }
            requireCommonDate(date)
            date
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("Recurrence date exceeds supported range", exception)
        } catch (exception: DateTimeException) {
            throw IllegalArgumentException("Recurrence date exceeds supported range", exception)
        }
    }

    fun isOccurrence(anchor: LocalDate, frequency: String, intervalCount: Long, customIntervalUnit: String?, candidate: LocalDate): Boolean {
        requireCommonDate(candidate)
        if (candidate < anchor) return false
        var index = estimateIndex(anchor, frequency, intervalCount, customIntervalUnit, candidate)
        while (index >= 0) {
            val date = try { occurrenceAt(anchor, frequency, intervalCount, customIntervalUnit, index) } catch (_: IllegalArgumentException) { return false }
            if (date == candidate) return true
            if (date < candidate) return false
            index--
        }
        return false
    }

    /** Returns the first due date strictly later than [after], or the anchor if it is later. */
    fun firstOccurrenceAfter(anchor: LocalDate, frequency: String, intervalCount: Long, customIntervalUnit: String?, after: LocalDate): LocalDate {
        requireCommonDate(anchor)
        requireCommonDate(after)
        if (anchor > after) return anchor
        var index = estimateIndex(anchor, frequency, intervalCount, customIntervalUnit, after)
        var candidate = occurrenceAt(anchor, frequency, intervalCount, customIntervalUnit, index)
        while (candidate <= after) {
            index = Math.addExact(index, 1L)
            candidate = occurrenceAt(anchor, frequency, intervalCount, customIntervalUnit, index)
        }
        return candidate
    }

    private fun estimateIndex(anchor: LocalDate, frequency: String, count: Long, unit: String?, date: LocalDate): Long {
        val cadence = cadence(frequency, count, unit)
        if (date <= anchor) return 0L
        return when (cadence) {
            is Cadence.Days -> ChronoUnit.DAYS.between(anchor, date) / cadence.amount
            is Cadence.Months -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(date)) / cadence.amount
            is Cadence.Years -> ChronoUnit.YEARS.between(anchor, date) / cadence.amount
        }
    }

    private fun cadence(frequency: String, intervalCount: Long, unit: String?): Cadence {
        require(intervalCount > 0) { "Interval count must be positive" }
        return when (frequency) {
            "WEEKLY" -> { require(intervalCount == 1L && unit == null); Cadence.Days(7L) }
            "BIWEEKLY" -> { require(intervalCount == 1L && unit == null); Cadence.Days(14L) }
            "MONTHLY" -> { require(intervalCount == 1L && unit == null); Cadence.Months(1L) }
            "ANNUALLY" -> { require(intervalCount == 1L && unit == null); Cadence.Years(1L) }
            "CUSTOM" -> when (unit) {
                "WEEK" -> { require(intervalCount in 1..5200); Cadence.Days(Math.multiplyExact(intervalCount, 7L)) }
                "MONTH" -> { require(intervalCount in 1..1200); Cadence.Months(intervalCount) }
                else -> throw IllegalArgumentException("Custom recurrence requires WEEK or MONTH")
            }
            else -> throw IllegalArgumentException("Unsupported recurrence frequency")
        }
    }

    private fun monthOccurrence(anchor: LocalDate, months: Long): LocalDate {
        val target = YearMonth.from(anchor).plusMonths(months)
        require(target.year <= MAX_DATE_YEAR) { "Recurrence date exceeds supported range" }
        return target.atDay(minOf(anchor.dayOfMonth, target.lengthOfMonth()))
    }

    private fun yearOccurrence(anchor: LocalDate, years: Long): LocalDate {
        val year = Math.addExact(anchor.year.toLong(), years)
        require(year in 1..MAX_DATE_YEAR.toLong()) { "Recurrence date exceeds supported range" }
        val target = YearMonth.of(year.toInt(), anchor.month)
        return target.atDay(minOf(anchor.dayOfMonth, target.lengthOfMonth()))
    }

    private fun requireCommonDate(date: LocalDate) {
        require(date.year in 1..MAX_DATE_YEAR) { "Date must be in 0001..9999" }
    }

    private sealed interface Cadence {
        val amount: Long
        data class Days(override val amount: Long) : Cadence
        data class Months(override val amount: Long) : Cadence
        data class Years(override val amount: Long) : Cadence
    }
}
