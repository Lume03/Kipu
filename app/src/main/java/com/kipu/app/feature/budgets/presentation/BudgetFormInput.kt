package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetError
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class BudgetScopeKind { TOTAL, CATEGORY }

enum class BudgetFormField { NAME, LIMIT, CATEGORY, RANGE_START, RANGE_END, THRESHOLDS }

/** Valores crudos del formulario (texto tal como lo escribe la persona). */
data class BudgetFormInput(
    val name: String = "",
    val limitText: String = "",
    val currency: String = "PEN",
    val scopeKind: BudgetScopeKind = BudgetScopeKind.TOTAL,
    val categoryId: String? = null,
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val rangeStartText: String = "",
    val rangeEndText: String = "",
    val thresholdsText: String = DEFAULT_THRESHOLDS_TEXT,
    val iconId: String? = null,
) {
    companion object {
        const val DEFAULT_THRESHOLDS_TEXT = "50, 80, 100"

        fun from(budget: Budget): BudgetFormInput {
            val schedule = budget.schedule
            return BudgetFormInput(
                name = budget.name,
                limitText = BigDecimal.valueOf(budget.limitMinor, 2).toPlainString(),
                currency = budget.currency,
                scopeKind = if (budget.scope is BudgetScope.Category) BudgetScopeKind.CATEGORY else BudgetScopeKind.TOTAL,
                categoryId = (budget.scope as? BudgetScope.Category)?.categoryId,
                periodType = schedule.type,
                rangeStartText = (schedule as? BudgetSchedule.CustomRange)?.start?.toString().orEmpty(),
                rangeEndText = (schedule as? BudgetSchedule.CustomRange)?.end?.toString().orEmpty(),
                thresholdsText = budget.thresholdsBps.sorted().joinToString(", ") { (it / 100).toString() },
            )
        }
    }
}

/** Valores ya interpretados (sintaxis). Las reglas de negocio las valida el dominio. */
data class ParsedBudgetForm(
    val name: String,
    val limitMinor: Long,
    val currency: String,
    val scope: BudgetScope,
    val schedule: BudgetSchedule,
    val thresholdsBps: List<Int>,
)

sealed interface BudgetFormParseResult {
    data class Valid(val value: ParsedBudgetForm) : BudgetFormParseResult
    data class Invalid(val errors: Map<BudgetFormField, String>) : BudgetFormParseResult
}

/** Interpretacion de texto del formulario: importe, fechas, umbrales. Sin reglas financieras. */
object BudgetFormParser {
    fun parse(input: BudgetFormInput): BudgetFormParseResult {
        val errors = linkedMapOf<BudgetFormField, String>()

        if (input.name.isBlank()) errors[BudgetFormField.NAME] = BudgetTexts.error(BudgetError.BlankName)

        val limit = parseLimitMinor(input.limitText)
        if (limit == null) errors[BudgetFormField.LIMIT] = "Escribe un límite válido, por ejemplo 500 o 500.50."
        else if (limit <= 0L) errors[BudgetFormField.LIMIT] = BudgetTexts.error(BudgetError.LimitNotPositive)

        val scope = when (input.scopeKind) {
            BudgetScopeKind.TOTAL -> BudgetScope.Total
            BudgetScopeKind.CATEGORY -> input.categoryId?.let { BudgetScope.Category(it) }
                ?: run {
                    errors[BudgetFormField.CATEGORY] = "Elige una categoría de gasto."
                    null
                }
        }

        val schedule: BudgetSchedule? = if (input.periodType == BudgetPeriodType.CUSTOM_RANGE) {
            val start = parseDate(input.rangeStartText)
            val end = parseDate(input.rangeEndText)
            if (start == null) errors[BudgetFormField.RANGE_START] = "Elige la fecha de inicio."
            if (end == null) errors[BudgetFormField.RANGE_END] = "Elige la fecha de fin."
            if (start != null && end != null && end.isBefore(start)) {
                errors[BudgetFormField.RANGE_END] = BudgetTexts.error(BudgetError.InvertedRange)
            }
            if (start != null && end != null) BudgetSchedule.CustomRange(start, end) else null
        } else {
            BudgetSchedule.Recurring(input.periodType)
        }

        val thresholds = parseThresholdsBps(input.thresholdsText)
        if (thresholds == null) {
            errors[BudgetFormField.THRESHOLDS] = "Escribe porcentajes enteros entre 1 y 100 separados por coma."
        } else {
            thresholds.filter { it < 1 || it > Budget.MAX_THRESHOLD_BPS }.firstOrNull()?.let {
                errors[BudgetFormField.THRESHOLDS] = BudgetTexts.error(BudgetError.ThresholdOutOfRange(it))
            }
            thresholds.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
                errors[BudgetFormField.THRESHOLDS] = BudgetTexts.error(BudgetError.DuplicateThreshold(it))
            }
        }

        if (errors.isNotEmpty() || limit == null || scope == null || schedule == null || thresholds == null) {
            return BudgetFormParseResult.Invalid(errors)
        }
        return BudgetFormParseResult.Valid(
            ParsedBudgetForm(input.name.trim(), limit, input.currency, scope, schedule, thresholds),
        )
    }

    fun parseLimitMinor(raw: String): Long? = runCatching {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        BigDecimal(normalized).setScale(2, java.math.RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }.getOrNull()

    fun parseDate(raw: String): LocalDate? =
        try { LocalDate.parse(raw.trim()) } catch (_: DateTimeParseException) { null }

    /** "50, 80, 100" -> [5000, 8000, 10000]. Vacio = umbrales por defecto. Null si no es entero. */
    fun parseThresholdsBps(raw: String): List<Int>? {
        if (raw.isBlank()) return Budget.DEFAULT_THRESHOLDS_BPS
        val parts = raw.split(',', ';', ' ').map { it.trim().removeSuffix("%").trim() }.filter { it.isNotEmpty() }
        val percents = parts.map { it.toIntOrNull() ?: return null }
        return percents.map { it.coerceIn(-1_000_000, 1_000_000) * 100 }
    }
}

/** Asigna un error de dominio al campo que debe mostrarlo (null = error general). */
internal fun BudgetError.field(): BudgetFormField? = when (this) {
    BudgetError.BlankName, is BudgetError.NameTooLong -> BudgetFormField.NAME
    BudgetError.LimitNotPositive -> BudgetFormField.LIMIT
    BudgetError.InvertedRange -> BudgetFormField.RANGE_END
    BudgetError.RangeBeforeEffectiveDate -> BudgetFormField.RANGE_START
    is BudgetError.ThresholdOutOfRange, is BudgetError.DuplicateThreshold -> BudgetFormField.THRESHOLDS
    is BudgetError.CategoryNotAvailable -> BudgetFormField.CATEGORY
    else -> null
}
