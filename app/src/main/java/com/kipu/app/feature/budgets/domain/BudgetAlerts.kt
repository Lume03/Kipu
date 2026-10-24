package com.kipu.app.feature.budgets.domain

import java.time.LocalDate
import java.time.ZoneId

/** Deteccion pura de cruces: umbrales alcanzados ahora y no emitidos antes en el periodo. */
object BudgetAlertDetector {
    fun crossings(thresholdsBps: Collection<Int>, consumption: BudgetConsumption, alreadyEmitted: Set<Int>): List<Int> =
        thresholdsBps.distinct().sorted().filter { it !in alreadyEmitted && consumption.percentBps >= it }
}

/**
 * Evalua el periodo vigente de un presupuesto ACTIVO y emite como maximo una alerta por
 * (propietario, presupuesto, periodo, umbral). Primero registra la clave de forma atomica en el
 * [BudgetAlertLedger] y solo entonces notifica, de modo que reintentos y reconsultas no duplican.
 * FINISHED no alerta. Los periodos cerrados no generan alertas nuevas.
 */
class EvaluateBudgetAlerts(
    private val repository: BudgetRepository,
    private val reader: BudgetConsumptionReader,
    private val ledger: BudgetAlertLedger,
    private val notifier: BudgetAlertNotifier,
) {
    suspend operator fun invoke(ownerId: String, budgetId: String, today: LocalDate, zone: ZoneId): List<BudgetAlert> {
        val budget = repository.getBudget(ownerId, budgetId)?.takeIf { it.isActive } ?: return emptyList()
        val period = repository.listPeriods(ownerId, budgetId).firstOrNull { today in it.interval } ?: return emptyList()

        val consumed = reader.consumedMinor(ownerId, period.scope, budget.currency, period.interval, zone)
        val consumption = BudgetConsumption(period.limitMinor, consumed)
        val emitted = ledger.emittedThresholds(ownerId, budgetId, period.id)

        val sent = mutableListOf<BudgetAlert>()
        for (threshold in BudgetAlertDetector.crossings(budget.thresholdsBps, consumption, emitted)) {
            val key = BudgetAlertKey(ownerId, budgetId, period.id, threshold)
            if (!ledger.tryRecord(key)) continue
            val alert = BudgetAlert(key, budget.name, consumption.percentBps, consumed, period.limitMinor, budget.currency)
            notifier.notify(alert)
            sent += alert
        }
        return sent
    }
}
