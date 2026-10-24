package com.kipu.app.feature.budgets.domain

import java.time.LocalDate

/**
 * Renovacion idempotente de vigencias. Crea los periodos faltantes entre el ultimo y [today], cada
 * uno con snapshot del limite y alcance vigentes. Los ids son deterministas y el repositorio los
 * inserta solo si faltan, asi que reintentar N veces deja un unico periodo por intervalo.
 *
 * Regla de cambios de alcance/calendario (documentada en data-model.md): un cambio pendiente
 * ([PendingBudgetChange]) se aplica al crear la primera vigencia que empieza en o despues de
 * `effectiveFrom`. Si el nuevo calendario es recurrente, esa primera vigencia empieza justo en la
 * frontera y termina al final natural del periodo del nuevo calendario que la contiene (puede ser
 * un periodo parcial); si es CUSTOM_RANGE, la vigencia es el rango. Los periodos previos no se tocan.
 * FINISHED no renueva. CUSTOM_RANGE no se renueva (fin natural).
 */
class RenewBudgetPeriods(private val repository: BudgetRepository) {

    suspend operator fun invoke(ownerId: String, budgetId: String, today: LocalDate): BudgetOutcome<List<BudgetPeriod>> {
        val budget = repository.getBudget(ownerId, budgetId) ?: return BudgetOutcome.Rejected(BudgetError.BudgetNotFound)
        if (!budget.isActive) return BudgetOutcome.Success(emptyList())

        val last = repository.listPeriods(ownerId, budgetId).lastOrNull()
        val created = mutableListOf<BudgetPeriod>()
        var current = budget
        var lastInterval: BudgetInterval? = last?.interval

        if (lastInterval == null) {
            // Presupuesto sin periodos (estado inconsistente): reconstruye la vigencia de hoy.
            val first = BudgetPeriodCalculator.firstPeriod(current.schedule, today)
            created += snapshot(current, first)
            lastInterval = first
        }

        while (true) {
            val boundary = lastInterval!!.endExclusive
            val pending = current.pendingChange?.takeIf { !it.effectiveFrom.isAfter(boundary) }
            val candidate: Budget
            val next: BudgetInterval?
            if (pending != null) {
                candidate = current.copy(
                    scope = pending.scope ?: current.scope,
                    schedule = pending.schedule ?: current.schedule,
                    pendingChange = null,
                    revision = current.revision + 1,
                )
                next = when (val s = pending.schedule) {
                    null -> BudgetPeriodCalculator.nextPeriod(candidate.schedule, lastInterval) // solo cambia el alcance
                    is BudgetSchedule.CustomRange -> BudgetPeriodCalculator.firstPeriod(s, boundary)
                    is BudgetSchedule.Recurring -> BudgetInterval(
                        boundary,
                        BudgetPeriodCalculator.periodContaining(s, boundary)!!.endExclusive,
                    )
                }
            } else {
                candidate = current
                next = BudgetPeriodCalculator.nextPeriod(current.schedule, lastInterval)
            }
            if (next == null || next.start.isAfter(today)) break
            current = candidate
            created += snapshot(current, next)
            lastInterval = next
        }

        if (created.isEmpty()) return BudgetOutcome.Success(emptyList())
        val result = repository.applyRenewal(
            ownerId = ownerId,
            budget = current.takeIf { it != budget },
            expectedRevision = budget.revision,
            newPeriods = created,
        )
        return when (result) {
            BudgetWriteResult.OK -> BudgetOutcome.Success(created)
            BudgetWriteResult.NOT_FOUND -> BudgetOutcome.Rejected(BudgetError.BudgetNotFound)
            else -> BudgetOutcome.Rejected(BudgetError.ConcurrentModification)
        }
    }

    /** Renueva todos los presupuestos activos del propietario. */
    suspend fun renewAll(ownerId: String, today: LocalDate): Map<String, BudgetOutcome<List<BudgetPeriod>>> =
        repository.listBudgets(ownerId).filter { it.isActive }.associate { it.id to invoke(ownerId, it.id, today) }

    private fun snapshot(budget: Budget, interval: BudgetInterval) = BudgetPeriod(
        id = BudgetPeriod.idFor(budget.id, interval),
        budgetId = budget.id,
        ownerId = budget.ownerId,
        interval = interval,
        limitMinor = budget.limitMinor,
        scope = budget.scope,
    )
}
