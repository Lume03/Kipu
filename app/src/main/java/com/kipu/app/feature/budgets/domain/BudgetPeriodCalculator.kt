package com.kipu.app.feature.budgets.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Calculo puro de vigencias sobre fechas civiles (inclusivo-inicio / exclusivo-fin).
 * Determinista: las mismas fechas producen siempre el mismo intervalo, base de la renovacion idempotente.
 */
object BudgetPeriodCalculator {
    /** Periodo que contiene [date]; null si es CUSTOM_RANGE y la fecha cae fuera del rango. */
    fun periodContaining(schedule: BudgetSchedule, date: LocalDate): BudgetInterval? = when (schedule) {
        is BudgetSchedule.CustomRange -> customInterval(schedule).takeIf { date in it }
        is BudgetSchedule.Recurring -> when (schedule.type) {
            BudgetPeriodType.DAILY -> BudgetInterval(date, date.plusDays(1))
            BudgetPeriodType.WEEKLY -> {
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                BudgetInterval(monday, monday.plusDays(7))
            }
            BudgetPeriodType.BIWEEKLY -> {
                val first = date.withDayOfMonth(1)
                if (date.dayOfMonth <= 15) {
                    BudgetInterval(first, first.withDayOfMonth(16))
                } else {
                    BudgetInterval(first.withDayOfMonth(16), first.plusMonths(1))
                }
            }
            BudgetPeriodType.MONTHLY -> {
                val first = date.withDayOfMonth(1)
                BudgetInterval(first, first.plusMonths(1))
            }
            BudgetPeriodType.CUSTOM_RANGE -> error("unreachable: Recurring never has CUSTOM_RANGE")
        }
    }

    /** Primer periodo de un calendario ancla en [anchor]: el que contiene la fecha, o el rango completo. */
    fun firstPeriod(schedule: BudgetSchedule, anchor: LocalDate): BudgetInterval = when (schedule) {
        is BudgetSchedule.CustomRange -> customInterval(schedule)
        is BudgetSchedule.Recurring -> periodContaining(schedule, anchor)!!
    }

    /** Siguiente vigencia contigua; null para CUSTOM_RANGE (fin natural, no se renueva). */
    fun nextPeriod(schedule: BudgetSchedule, current: BudgetInterval): BudgetInterval? = when (schedule) {
        is BudgetSchedule.CustomRange -> null
        is BudgetSchedule.Recurring -> periodContaining(schedule, current.endExclusive)
    }

    /** Periodos posteriores a [last] cuyo inicio es <= [today], en orden. */
    fun missingPeriods(schedule: BudgetSchedule, last: BudgetInterval, today: LocalDate): List<BudgetInterval> {
        val result = mutableListOf<BudgetInterval>()
        var next = nextPeriod(schedule, last)
        while (next != null && !next.start.isAfter(today)) {
            result += next
            next = nextPeriod(schedule, next)
        }
        return result
    }

    private fun customInterval(s: BudgetSchedule.CustomRange) = BudgetInterval(s.start, s.end.plusDays(1))
}
