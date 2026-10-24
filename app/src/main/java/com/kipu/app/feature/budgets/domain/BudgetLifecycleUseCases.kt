package com.kipu.app.feature.budgets.domain

import java.time.LocalDate

/**
 * Edicion de un presupuesto. Campos nulos = sin cambio.
 *
 * Reglas:
 * - Nombre y umbrales: inmediatos.
 * - Limite: afecta el periodo vigente (su snapshot) y los futuros; los periodos cerrados conservan el suyo.
 * - Alcance y calendario: NO se aplican al periodo en curso; quedan como [PendingBudgetChange] y entran
 *   en vigor al iniciar la siguiente vigencia (ver [RenewBudgetPeriods]). Un nuevo CUSTOM_RANGE debe empezar
 *   en o despues de esa frontera. Si no hay vigencia en curso (rango terminado), el cambio aplica de inmediato
 *   creando una vigencia nueva desde hoy.
 */
data class EditBudgetCommand(
    val ownerId: String,
    val budgetId: String,
    val today: LocalDate,
    val name: String? = null,
    val limitMinor: Long? = null,
    val thresholdsBps: List<Int>? = null,
    val scope: BudgetScope? = null,
    val schedule: BudgetSchedule? = null,
)

class EditBudget(
    private val repository: BudgetRepository,
    private val validator: BudgetValidator,
    private val renew: RenewBudgetPeriods,
) {
    suspend operator fun invoke(c: EditBudgetCommand): BudgetOutcome<Budget> {
        var budget = repository.getBudget(c.ownerId, c.budgetId) ?: return BudgetOutcome.Rejected(BudgetError.BudgetNotFound)
        if (!budget.isActive) return BudgetOutcome.Rejected(BudgetError.BudgetFinished)

        val errors = buildList {
            c.name?.let { addAll(validator.validateName(it)) }
            c.limitMinor?.let { addAll(validator.validateLimit(it)) }
            c.thresholdsBps?.let { addAll(validator.validateThresholds(it)) }
            c.scope?.let { addAll(validator.validateScope(c.ownerId, it)) }
            c.schedule?.let { addAll(validator.validateSchedule(it)) }
        }
        if (errors.isNotEmpty()) return BudgetOutcome.Rejected(errors)

        // Pone al dia las vigencias antes de decidir cual es "la actual".
        (renew(c.ownerId, c.budgetId, c.today) as? BudgetOutcome.Rejected)?.let { return it }
        budget = repository.getBudget(c.ownerId, c.budgetId) ?: return BudgetOutcome.Rejected(BudgetError.BudgetNotFound)

        val periods = repository.listPeriods(c.ownerId, c.budgetId)
        val currentPeriod = periods.firstOrNull { c.today in it.interval }

        val scopeChange = c.scope?.takeIf { it != budget.scope }
        val scheduleChange = c.schedule?.takeIf { it != budget.schedule }

        var updated = budget.copy(
            name = c.name?.trim() ?: budget.name,
            limitMinor = c.limitMinor ?: budget.limitMinor,
            thresholdsBps = c.thresholdsBps?.sorted() ?: budget.thresholdsBps,
            revision = budget.revision + 1,
        )
        var immediatePeriod: BudgetPeriod? = null

        if (scopeChange != null || scheduleChange != null) {
            if (currentPeriod != null) {
                val effectiveFrom = currentPeriod.interval.endExclusive
                if (scheduleChange is BudgetSchedule.CustomRange && scheduleChange.start.isBefore(effectiveFrom)) {
                    return BudgetOutcome.Rejected(BudgetError.RangeBeforeEffectiveDate)
                }
                updated = updated.copy(pendingChange = PendingBudgetChange(scopeChange, scheduleChange, effectiveFrom))
            } else {
                updated = updated.copy(
                    scope = scopeChange ?: updated.scope,
                    schedule = scheduleChange ?: updated.schedule,
                    pendingChange = null,
                )
                val interval = BudgetPeriodCalculator.firstPeriod(updated.schedule, c.today)
                if (periods.none { it.interval == interval }) {
                    immediatePeriod = BudgetPeriod(
                        BudgetPeriod.idFor(updated.id, interval), updated.id, updated.ownerId,
                        interval, updated.limitMinor, updated.scope,
                    )
                }
            }
        }

        val write = if (immediatePeriod != null) {
            repository.applyRenewal(c.ownerId, updated, budget.revision, listOf(immediatePeriod))
        } else {
            repository.update(updated, budget.revision)
        }
        if (write != BudgetWriteResult.OK) return BudgetOutcome.Rejected(write.toError())

        // Solo el periodo en curso adopta el nuevo limite; los cerrados quedan intactos.
        if (c.limitMinor != null && currentPeriod != null && currentPeriod.limitMinor != c.limitMinor) {
            repository.updatePeriodLimit(c.ownerId, currentPeriod.id, c.limitMinor)
        }
        return BudgetOutcome.Success(updated)
    }
}

/** Finaliza: detiene renovacion y alertas; la historia sigue consultable. Idempotente. */
class FinishBudget(private val repository: BudgetRepository) {
    suspend operator fun invoke(ownerId: String, budgetId: String): BudgetOutcome<Budget> {
        val budget = repository.getBudget(ownerId, budgetId) ?: return BudgetOutcome.Rejected(BudgetError.BudgetNotFound)
        if (!budget.isActive) return BudgetOutcome.Success(budget)
        val finished = budget.copy(status = BudgetStatus.FINISHED, pendingChange = null, revision = budget.revision + 1)
        val write = repository.update(finished, budget.revision)
        return if (write == BudgetWriteResult.OK) BudgetOutcome.Success(finished) else BudgetOutcome.Rejected(write.toError())
    }
}

/** Eliminacion definitiva de presupuesto y periodos. La confirmacion es responsabilidad de la UI. Nunca toca movimientos. */
class DeleteBudget(private val repository: BudgetRepository) {
    suspend operator fun invoke(ownerId: String, budgetId: String): BudgetOutcome<Unit> =
        when (val write = repository.delete(ownerId, budgetId)) {
            BudgetWriteResult.OK -> BudgetOutcome.Success(Unit)
            else -> BudgetOutcome.Rejected(write.toError())
        }
}

private fun BudgetWriteResult.toError(): BudgetError = when (this) {
    BudgetWriteResult.NOT_FOUND -> BudgetError.BudgetNotFound
    BudgetWriteResult.ALREADY_EXISTS -> BudgetError.AlreadyExists
    else -> BudgetError.ConcurrentModification
}
