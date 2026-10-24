package com.kipu.app.feature.budgets.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCategoryLookup(
    private val tree: Map<String, String?> = mapOf(
        "alimentacion" to null,
        "restaurantes" to "alimentacion",
        "supermercado" to "alimentacion",
        "transporte" to null,
    ),
    private val unavailable: Set<String> = emptySet(),
) : BudgetCategoryLookup {
    override suspend fun isAvailable(ownerId: String, categoryId: String) =
        categoryId in tree && categoryId !in unavailable

    override suspend fun idsIncludingDescendants(ownerId: String, categoryId: String): Set<String> {
        val result = mutableSetOf(categoryId)
        var changed = true
        while (changed) {
            changed = false
            tree.forEach { (id, parent) -> if (parent in result && result.add(id)) changed = true }
        }
        return result
    }
}

class FakeBudgetRepository : BudgetRepository {
    val budgets = MutableStateFlow<Map<String, Budget>>(emptyMap())
    val periods = MutableStateFlow<Map<String, BudgetPeriod>>(emptyMap())
    var applyRenewalCalls = 0

    override fun observeBudgets(ownerId: String): Flow<List<Budget>> =
        budgets.map { m -> m.values.filter { it.ownerId == ownerId } }

    override fun observePeriods(ownerId: String, budgetId: String): Flow<List<BudgetPeriod>> =
        periods.map { m -> m.values.filter { it.budgetId == budgetId && it.ownerId == ownerId }.sortedBy { it.interval.start } }

    override suspend fun getBudget(ownerId: String, budgetId: String) =
        budgets.value[budgetId]?.takeIf { it.ownerId == ownerId }

    override suspend fun listBudgets(ownerId: String) = budgets.value.values.filter { it.ownerId == ownerId }

    override suspend fun listPeriods(ownerId: String, budgetId: String) =
        periods.value.values.filter { it.budgetId == budgetId && it.ownerId == ownerId }.sortedBy { it.interval.start }

    override suspend fun create(budget: Budget, firstPeriod: BudgetPeriod): BudgetWriteResult {
        if (budget.id in budgets.value) return BudgetWriteResult.ALREADY_EXISTS
        budgets.value = budgets.value + (budget.id to budget)
        periods.value = periods.value + (firstPeriod.id to firstPeriod)
        return BudgetWriteResult.OK
    }

    override suspend fun update(budget: Budget, expectedRevision: Long): BudgetWriteResult {
        val current = budgets.value[budget.id] ?: return BudgetWriteResult.NOT_FOUND
        if (current.revision != expectedRevision) return BudgetWriteResult.REVISION_CONFLICT
        budgets.value = budgets.value + (budget.id to budget)
        return BudgetWriteResult.OK
    }

    override suspend fun applyRenewal(
        ownerId: String,
        budget: Budget?,
        expectedRevision: Long,
        newPeriods: List<BudgetPeriod>,
    ): BudgetWriteResult {
        applyRenewalCalls++
        if (budget != null) {
            val r = update(budget, expectedRevision)
            if (r != BudgetWriteResult.OK) return r
        }
        newPeriods.forEach { p -> if (p.id !in periods.value) periods.value = periods.value + (p.id to p) }
        return BudgetWriteResult.OK
    }

    override suspend fun updatePeriodLimit(ownerId: String, periodId: String, limitMinor: Long): BudgetWriteResult {
        val p = periods.value[periodId] ?: return BudgetWriteResult.NOT_FOUND
        periods.value = periods.value + (periodId to p.copy(limitMinor = limitMinor))
        return BudgetWriteResult.OK
    }

    override suspend fun delete(ownerId: String, budgetId: String): BudgetWriteResult {
        if (getBudget(ownerId, budgetId) == null) return BudgetWriteResult.NOT_FOUND
        budgets.value = budgets.value - budgetId
        periods.value = periods.value.filterValues { it.budgetId != budgetId }
        return BudgetWriteResult.OK
    }
}

class FakePlanAccess(var premium: Boolean = false) : BudgetPlanAccess {
    override suspend fun isPremiumVerified(ownerId: String) = premium
}

/** Gasto neto en memoria que imita el query real: un gasto cuenta una vez por alcance. */
class FakeConsumptionReader(private val categories: BudgetCategoryLookup = FakeCategoryLookup()) : BudgetConsumptionReader {
    data class Expense(val categoryId: String, val date: LocalDate, val amountMinor: Long, val currency: String = "PEN")

    val expenses = mutableListOf<Expense>()

    override suspend fun consumedMinor(
        ownerId: String,
        scope: BudgetScope,
        currency: String,
        interval: BudgetInterval,
        zone: java.time.ZoneId,
    ): Long {
        val ids = (scope as? BudgetScope.Category)?.let { categories.idsIncludingDescendants(ownerId, it.categoryId) }
        return expenses.filter {
            it.currency == currency && it.date in interval && (ids == null || it.categoryId in ids)
        }.sumOf { it.amountMinor }
    }
}

class FakeAlertLedger : BudgetAlertLedger {
    val recorded = mutableSetOf<BudgetAlertKey>()
    override suspend fun emittedThresholds(ownerId: String, budgetId: String, periodId: String) =
        recorded.filter { it.ownerId == ownerId && it.budgetId == budgetId && it.periodId == periodId }
            .map { it.thresholdBps }.toSet()

    override suspend fun tryRecord(key: BudgetAlertKey) = recorded.add(key)
}

class FakeNotifier : BudgetAlertNotifier {
    val sent = mutableListOf<BudgetAlert>()
    override suspend fun notify(alert: BudgetAlert) {
        sent += alert
    }
}
