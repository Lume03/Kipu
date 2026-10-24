package com.kipu.app.feature.budgets.domain

import com.kipu.app.feature.plans.domain.PlanQuotaPolicy
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import java.time.LocalDate

data class CreateBudgetCommand(
    val id: String,
    val ownerId: String,
    val name: String,
    val scope: BudgetScope,
    val limitMinor: Long,
    val currency: String,
    val schedule: BudgetSchedule,
    val thresholdsBps: List<Int> = Budget.DEFAULT_THRESHOLDS_BPS,
    /** Fecha civil de hoy en la zona del dispositivo. */
    val today: LocalDate,
)

/**
 * Alta de presupuesto. Free: maximo [FreePlanLimits.budgets] activos (la politica de planes
 * decide el limite); el que excede se rechaza con [BudgetError.FreeQuotaExceeded] sin tocar los existentes.
 */
class CreateBudget(
    private val repository: BudgetRepository,
    private val validator: BudgetValidator,
    private val planAccess: BudgetPlanAccess,
    private val quotaPolicy: PlanQuotaPolicy = PlanQuotaPolicy(),
    private val freeLimits: FreePlanLimits = FreePlanLimits(),
) {
    suspend operator fun invoke(command: CreateBudgetCommand): BudgetOutcome<Budget> {
        val errors = validator.validate(
            command.ownerId, command.name, command.scope, command.limitMinor,
            command.currency, command.schedule, command.thresholdsBps,
        )
        if (errors.isNotEmpty()) return BudgetOutcome.Rejected(errors)

        val premium = planAccess.isPremiumVerified(command.ownerId)
        val activeIds = repository.listBudgets(command.ownerId).filter { it.isActive }.map { it.id }
        val quota = quotaPolicy.evaluate(QuotaGroup.BUDGETS, activeIds, emptyList(), freeLimits, premium)
        if (!quota.isPremiumVerified && activeIds.size >= quota.limit) {
            return BudgetOutcome.Rejected(BudgetError.FreeQuotaExceeded(quota.limit))
        }

        val budget = Budget(
            id = command.id,
            ownerId = command.ownerId,
            name = command.name.trim(),
            scope = command.scope,
            limitMinor = command.limitMinor,
            currency = command.currency,
            schedule = command.schedule,
            thresholdsBps = command.thresholdsBps.sorted(),
            createdOn = command.today,
        )
        val interval = BudgetPeriodCalculator.firstPeriod(command.schedule, command.today)
        val period = BudgetPeriod(
            id = BudgetPeriod.idFor(budget.id, interval),
            budgetId = budget.id,
            ownerId = budget.ownerId,
            interval = interval,
            limitMinor = budget.limitMinor,
            scope = budget.scope,
        )
        return when (repository.create(budget, period)) {
            BudgetWriteResult.OK -> BudgetOutcome.Success(budget)
            BudgetWriteResult.ALREADY_EXISTS -> BudgetOutcome.Rejected(BudgetError.AlreadyExists)
            else -> BudgetOutcome.Rejected(BudgetError.ConcurrentModification)
        }
    }
}
