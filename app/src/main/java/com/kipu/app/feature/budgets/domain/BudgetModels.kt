package com.kipu.app.feature.budgets.domain

import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

/** Periodicidades aprobadas (G1, 2026-10-24). */
enum class BudgetPeriodType { DAILY, WEEKLY, BIWEEKLY, MONTHLY, CUSTOM_RANGE }

enum class BudgetStatus { ACTIVE, FINISHED }

/** Alcance de un presupuesto: total de gasto o una categoria (un padre incluye sus subcategorias). */
sealed interface BudgetScope {
    data object Total : BudgetScope
    data class Category(val categoryId: String) : BudgetScope
}

/** Calendario de un presupuesto. CUSTOM_RANGE usa fechas civiles inclusivas y no se renueva. */
sealed interface BudgetSchedule {
    val type: BudgetPeriodType

    data class Recurring(override val type: BudgetPeriodType) : BudgetSchedule {
        init { require(type != BudgetPeriodType.CUSTOM_RANGE) { "CUSTOM_RANGE requires CustomRange" } }
    }

    /** [start] y [end] inclusivos; end >= start. */
    data class CustomRange(val start: LocalDate, val end: LocalDate) : BudgetSchedule {
        override val type: BudgetPeriodType get() = BudgetPeriodType.CUSTOM_RANGE
    }
}

/**
 * Intervalo civil inclusivo-inicio / exclusivo-fin. La conversion a instantes usa la zona del
 * dispositivo inyectada por quien llama.
 */
data class BudgetInterval(val start: LocalDate, val endExclusive: LocalDate) {
    init { require(endExclusive.isAfter(start)) { "Interval end must be after start" } }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    /** Ultimo dia incluido (para mostrar). */
    val lastDay: LocalDate get() = endExclusive.minusDays(1)

    fun fromInclusiveMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun toExclusiveMillis(zone: ZoneId): Long = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Cambio de alcance/calendario pendiente: entra en vigor en [effectiveFrom] (inicio de la siguiente vigencia). */
data class PendingBudgetChange(
    val scope: BudgetScope?,
    val schedule: BudgetSchedule?,
    val effectiveFrom: LocalDate,
)

data class Budget(
    val id: String,
    val ownerId: String,
    val name: String,
    val scope: BudgetScope,
    val limitMinor: Long,
    val currency: String,
    val schedule: BudgetSchedule,
    val thresholdsBps: List<Int> = DEFAULT_THRESHOLDS_BPS,
    val status: BudgetStatus = BudgetStatus.ACTIVE,
    val revision: Long = 1L,
    val createdOn: LocalDate,
    val pendingChange: PendingBudgetChange? = null,
) {
    val isActive: Boolean get() = status == BudgetStatus.ACTIVE

    companion object {
        val DEFAULT_THRESHOLDS_BPS: List<Int> = listOf(5000, 8000, 10000)
        const val MAX_THRESHOLD_BPS: Int = 10000
        const val MAX_NAME_LENGTH: Int = 60
    }
}

/**
 * Snapshot de una vigencia. [limitMinor] y [scope] son los que rigieron ese periodo; no se
 * reescriben cuando el periodo ya cerro. El id es determinista (presupuesto + inicio + fin) para
 * que la renovacion sea idempotente.
 */
data class BudgetPeriod(
    val id: String,
    val budgetId: String,
    val ownerId: String,
    val interval: BudgetInterval,
    val limitMinor: Long,
    val scope: BudgetScope,
) {
    fun isClosedOn(today: LocalDate): Boolean = !today.isBefore(interval.endExclusive)

    companion object {
        fun idFor(budgetId: String, interval: BudgetInterval): String =
            "$budgetId:${interval.start}:${interval.endExclusive}"
    }
}

/** Consumo derivado de un periodo. Todo en unidad menor; [remainingMinor] puede ser negativo. */
data class BudgetConsumption(
    val limitMinor: Long,
    val consumedMinor: Long,
) {
    val remainingMinor: Long get() = limitMinor - consumedMinor
    val isExceeded: Boolean get() = consumedMinor > limitMinor

    /** Porcentaje en puntos basicos, redondeado hacia abajo (120 de 500 -> 2400). Puede superar 10000. */
    val percentBps: Long
        get() = if (limitMinor <= 0L || consumedMinor <= 0L) {
            0L
        } else {
            BigInteger.valueOf(consumedMinor).multiply(BigInteger.valueOf(10_000L))
                .divide(BigInteger.valueOf(limitMinor)).toLong()
        }

    /** Porcentaje entero redondeado hacia abajo (24 para 120 de 500). */
    val percentWhole: Long get() = percentBps / 100L
}

/** Errores tipados y explicables. */
sealed interface BudgetError {
    data object BlankName : BudgetError
    data class NameTooLong(val max: Int) : BudgetError
    data object LimitNotPositive : BudgetError
    data class InvalidCurrency(val code: String) : BudgetError
    data class UnsupportedCurrency(val code: String) : BudgetError
    data object InvertedRange : BudgetError
    data object RangeBeforeEffectiveDate : BudgetError
    data class ThresholdOutOfRange(val value: Int) : BudgetError
    data class DuplicateThreshold(val value: Int) : BudgetError
    data class CategoryNotAvailable(val categoryId: String) : BudgetError
    /** Free permite [limit] presupuestos activos; nunca se borra ni oculta ninguno. */
    data class FreeQuotaExceeded(val limit: Int) : BudgetError
    data object BudgetNotFound : BudgetError
    data object BudgetFinished : BudgetError
    data object AlreadyExists : BudgetError
    data object ConcurrentModification : BudgetError
}

sealed interface BudgetOutcome<out T> {
    data class Success<T>(val value: T) : BudgetOutcome<T>
    data class Rejected(val errors: List<BudgetError>) : BudgetOutcome<Nothing> {
        constructor(error: BudgetError) : this(listOf(error))
    }
}
