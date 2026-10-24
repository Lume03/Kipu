package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.BudgetError
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Textos en espanol de presupuestos. Sin logica financiera: solo presentacion. */
internal object BudgetTexts {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun date(date: LocalDate): String = date.format(dateFormat)

    fun periodType(type: BudgetPeriodType): String = when (type) {
        BudgetPeriodType.DAILY -> "Diario"
        BudgetPeriodType.WEEKLY -> "Semanal (lun-dom)"
        BudgetPeriodType.BIWEEKLY -> "Quincenal (1-15 / 16-fin)"
        BudgetPeriodType.MONTHLY -> "Mensual"
        BudgetPeriodType.CUSTOM_RANGE -> "Rango personalizado"
    }

    fun periodTypeShort(type: BudgetPeriodType): String = when (type) {
        BudgetPeriodType.DAILY -> "Diario"
        BudgetPeriodType.WEEKLY -> "Semanal"
        BudgetPeriodType.BIWEEKLY -> "Quincenal"
        BudgetPeriodType.MONTHLY -> "Mensual"
        BudgetPeriodType.CUSTOM_RANGE -> "Rango"
    }

    fun interval(interval: BudgetInterval): String =
        if (interval.lastDay == interval.start) date(interval.start)
        else "${date(interval.start)} al ${date(interval.lastDay)}"

    fun schedule(schedule: BudgetSchedule): String = when (schedule) {
        is BudgetSchedule.Recurring -> periodType(schedule.type)
        is BudgetSchedule.CustomRange -> "Del ${date(schedule.start)} al ${date(schedule.end)}"
    }

    fun scopeTotal(): String = "Todos los gastos"

    fun scopeLabel(scope: BudgetScope, categoryName: (String) -> String?): String = when (scope) {
        BudgetScope.Total -> scopeTotal()
        is BudgetScope.Category -> categoryName(scope.categoryId) ?: "Categoría no disponible"
    }

    fun currencyNotice(currency: String): String = "Solo se cuentan gastos en $currency"

    fun thresholds(bps: List<Int>): String = bps.sorted().joinToString(", ") { "${it / 100} %" }

    fun error(error: BudgetError): String = when (error) {
        BudgetError.BlankName -> "Escribe un nombre para el presupuesto."
        is BudgetError.NameTooLong -> "El nombre admite hasta ${error.max} caracteres."
        BudgetError.LimitNotPositive -> "El límite debe ser mayor que cero."
        is BudgetError.InvalidCurrency -> "La moneda ${error.code} no es válida."
        is BudgetError.UnsupportedCurrency -> "Por ahora solo se admiten soles (PEN) y dólares (USD)."
        BudgetError.InvertedRange -> "La fecha de fin no puede ser anterior a la de inicio."
        BudgetError.RangeBeforeEffectiveDate ->
            "El nuevo rango debe empezar en o después del inicio del siguiente periodo."
        is BudgetError.ThresholdOutOfRange -> "Cada umbral debe estar entre 1 % y 100 %."
        is BudgetError.DuplicateThreshold -> "El umbral ${error.value / 100} % está repetido."
        is BudgetError.CategoryNotAvailable -> "La categoría elegida ya no está disponible."
        is BudgetError.FreeQuotaExceeded ->
            "El plan Free permite ${error.limit} presupuestos activos. No se borró ni ocultó ninguno."
        BudgetError.BudgetNotFound -> "El presupuesto ya no existe."
        BudgetError.BudgetFinished -> "El presupuesto está finalizado y ya no se puede editar."
        BudgetError.AlreadyExists -> "Este presupuesto ya existe."
        BudgetError.ConcurrentModification -> "El presupuesto cambió en otro lugar. Vuelve a intentarlo."
    }
}
