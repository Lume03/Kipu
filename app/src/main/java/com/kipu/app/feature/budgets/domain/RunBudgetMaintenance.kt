package com.kipu.app.feature.budgets.domain

import java.time.LocalDate
import java.time.ZoneId

data class BudgetMaintenanceResult(
    val renewals: Map<String, BudgetOutcome<List<BudgetPeriod>>>,
    val alerts: List<BudgetAlert>,
)

/** Shared entry point for app/screen activation and periodic local maintenance. */
class RunBudgetMaintenance(
    private val repository: BudgetRepository,
    private val renewPeriods: RenewBudgetPeriods,
    private val evaluateAlerts: EvaluateBudgetAlerts,
) {
    suspend operator fun invoke(ownerId: String, today: LocalDate, zone: ZoneId): BudgetMaintenanceResult {
        val renewals = renewPeriods.renewAll(ownerId, today)
        val alerts = mutableListOf<BudgetAlert>()
        for (budget in repository.listBudgets(ownerId).filter(Budget::isActive)) {
            alerts += evaluateAlerts(ownerId, budget.id, today, zone)
        }
        return BudgetMaintenanceResult(renewals, alerts)
    }
}
