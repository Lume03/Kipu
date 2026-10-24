package com.kipu.app.feature.budgets.domain

import java.util.Currency

/** Validacion de alta/edicion. Devuelve todos los errores; lista vacia = valido. */
class BudgetValidator(private val categories: BudgetCategoryLookup) {

    suspend fun validate(
        ownerId: String,
        name: String,
        scope: BudgetScope,
        limitMinor: Long,
        currency: String,
        schedule: BudgetSchedule,
        thresholdsBps: List<Int>,
    ): List<BudgetError> = buildList {
        addAll(validateName(name))
        addAll(validateLimit(limitMinor))
        addAll(validateCurrency(currency))
        addAll(validateSchedule(schedule))
        addAll(validateThresholds(thresholdsBps))
        addAll(validateScope(ownerId, scope))
    }

    fun validateName(name: String): List<BudgetError> = when {
        name.isBlank() -> listOf(BudgetError.BlankName)
        name.trim().length > Budget.MAX_NAME_LENGTH -> listOf(BudgetError.NameTooLong(Budget.MAX_NAME_LENGTH))
        else -> emptyList()
    }

    fun validateLimit(limitMinor: Long): List<BudgetError> =
        if (limitMinor > 0L) emptyList() else listOf(BudgetError.LimitNotPositive)

    fun validateCurrency(code: String): List<BudgetError> {
        val iso = runCatching { Currency.getInstance(code) }.getOrNull()
            ?: return listOf(BudgetError.InvalidCurrency(code))
        return if (iso.currencyCode in SUPPORTED_CURRENCIES) emptyList() else listOf(BudgetError.UnsupportedCurrency(code))
    }

    fun validateSchedule(schedule: BudgetSchedule): List<BudgetError> =
        if (schedule is BudgetSchedule.CustomRange && schedule.end.isBefore(schedule.start)) {
            listOf(BudgetError.InvertedRange)
        } else {
            emptyList()
        }

    fun validateThresholds(thresholdsBps: List<Int>): List<BudgetError> {
        val errors = mutableListOf<BudgetError>()
        thresholdsBps.filter { it < 1 || it > Budget.MAX_THRESHOLD_BPS }
            .forEach { errors += BudgetError.ThresholdOutOfRange(it) }
        thresholdsBps.groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { errors += BudgetError.DuplicateThreshold(it) }
        return errors
    }

    suspend fun validateScope(ownerId: String, scope: BudgetScope): List<BudgetError> =
        if (scope is BudgetScope.Category && !categories.isAvailable(ownerId, scope.categoryId)) {
            listOf(BudgetError.CategoryNotAvailable(scope.categoryId))
        } else {
            emptyList()
        }

    companion object {
        /** Monedas que el motor de gasto soporta hoy (ExpenseConsumptionQuery). */
        val SUPPORTED_CURRENCIES = setOf("PEN", "USD")
    }
}
