package com.kipu.app.feature.budgets.domain

import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lector delgado sobre [ExpenseConsumptionRepository] (gasto neto de EP-MOV). Un presupuesto de
 * categoria consulta el conjunto {categoria + descendientes}; el query intersecta por id de
 * transaccion, de modo que un gasto nunca se cuenta dos veces dentro de un alcance. El total
 * consulta sin filtro de categoria. Solo cuentan gastos en la moneda del presupuesto (G6).
 */
class ExpenseBudgetConsumptionReader(
    private val expenses: ExpenseConsumptionRepository,
    private val categories: BudgetCategoryLookup,
) : BudgetConsumptionReader {
    override suspend fun consumedMinor(
        ownerId: String,
        scope: BudgetScope,
        currency: String,
        interval: BudgetInterval,
        zone: ZoneId,
    ): Long {
        val categoryIds = when (scope) {
            BudgetScope.Total -> emptySet()
            is BudgetScope.Category -> categories.idsIncludingDescendants(ownerId, scope.categoryId)
        }
        val result = expenses.queryConsumption(
            ownerId,
            ExpenseConsumptionQuery(
                currency = currency,
                fromInclusive = interval.fromInclusiveMillis(zone),
                toExclusive = interval.toExclusiveMillis(zone),
                zoneId = zone,
                categoryIds = categoryIds,
            ),
        )
        // El neto puede quedar por debajo de cero si los reembolsos superan el gasto: no se muestra negativo.
        return result.amountMinor.coerceAtLeast(0L)
    }
}

data class BudgetPeriodConsumption(val period: BudgetPeriod, val consumption: BudgetConsumption)

/** Consulta de limite/consumido/restante/porcentaje de un periodo (vigente o historico). */
class GetBudgetConsumption(
    private val repository: BudgetRepository,
    private val reader: BudgetConsumptionReader,
) {
    /** Periodo vigente en [today]; si no hay (rango terminado), el ultimo periodo conocido. */
    suspend fun current(ownerId: String, budgetId: String, today: LocalDate, zone: ZoneId): BudgetOutcome<BudgetPeriodConsumption> {
        val budget = repository.getBudget(ownerId, budgetId) ?: return BudgetOutcome.Rejected(BudgetError.BudgetNotFound)
        val periods = repository.listPeriods(ownerId, budgetId)
        val period = periods.firstOrNull { today in it.interval } ?: periods.lastOrNull()
            ?: return BudgetOutcome.Rejected(BudgetError.BudgetNotFound)
        return BudgetOutcome.Success(forPeriod(budget.currency, period, zone))
    }

    /** Usa el limite y alcance snapshot del periodo, no la configuracion actual. */
    suspend fun forPeriod(currency: String, period: BudgetPeriod, zone: ZoneId): BudgetPeriodConsumption {
        val consumed = reader.consumedMinor(period.ownerId, period.scope, currency, period.interval, zone)
        return BudgetPeriodConsumption(period, BudgetConsumption(period.limitMinor, consumed))
    }
}
