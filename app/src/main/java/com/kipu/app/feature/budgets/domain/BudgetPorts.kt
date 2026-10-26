package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.flow.Flow

enum class BudgetWriteResult { OK, NOT_FOUND, REVISION_CONFLICT, ALREADY_EXISTS }

/**
 * Puerto de persistencia (lo implementa la capa de datos Room). Todas las operaciones estan
 * acotadas al propietario. Las que reciben varias entidades deben ser atomicas.
 */
interface BudgetRepository {
    fun observeSyncIssues(ownerId: String): Flow<Map<String, String>> = kotlinx.coroutines.flow.flowOf(emptyMap())
    fun observeBudgets(ownerId: String): Flow<List<Budget>>
    fun observePeriods(ownerId: String, budgetId: String): Flow<List<BudgetPeriod>>
    suspend fun getBudget(ownerId: String, budgetId: String): Budget?
    suspend fun listBudgets(ownerId: String): List<Budget>

    /** Periodos del presupuesto ordenados por inicio ascendente. */
    suspend fun listPeriods(ownerId: String, budgetId: String): List<BudgetPeriod>

    /** Crea presupuesto y su primer periodo atomicamente. */
    suspend fun create(budget: Budget, firstPeriod: BudgetPeriod): BudgetWriteResult

    /** Reemplaza el presupuesto si la revision almacenada es [expectedRevision]. */
    suspend fun update(budget: Budget, expectedRevision: Long): BudgetWriteResult

    /**
     * Aplica de forma atomica y sin duplicar: guarda [budget] (si no es null, con control de
     * revision [expectedRevision]) e inserta cada periodo solo si no existe ya el id.
     * Idempotente ante reintentos.
     */
    suspend fun applyRenewal(
        ownerId: String,
        budget: Budget?,
        expectedRevision: Long,
        newPeriods: List<BudgetPeriod>,
    ): BudgetWriteResult

    /** Cambia el limite snapshot de un periodo aun vigente. */
    suspend fun updatePeriodLimit(ownerId: String, periodId: String, limitMinor: Long): BudgetWriteResult

    /** Borra presupuesto y periodos (el data layer registra tombstone de sync). Nunca toca movimientos. */
    suspend fun delete(ownerId: String, budgetId: String): BudgetWriteResult
}

/** Verificacion y expansion de categorias propias (arbol categoria/subcategoria). */
interface BudgetCategoryLookup {
    /** true si la categoria existe, es del propietario (o de sistema) y esta activa. */
    suspend fun isAvailable(ownerId: String, categoryId: String): Boolean

    /** La categoria y todas sus descendientes, como conjunto de ids. */
    suspend fun idsIncludingDescendants(ownerId: String, categoryId: String): Set<String>
}

/** Estado del plan. Free = no verificado Premium. */
fun interface BudgetPlanAccess {
    suspend fun isPremiumVerified(ownerId: String): Boolean
}

/** Consumo neto de gasto de un alcance en un intervalo, en moneda del presupuesto. */
fun interface BudgetConsumptionReader {
    suspend fun consumedMinor(
        ownerId: String,
        scope: BudgetScope,
        currency: String,
        interval: BudgetInterval,
        zone: java.time.ZoneId,
    ): Long
}

/** Clave de deduplicacion: propietario + presupuesto + periodo + umbral. */
data class BudgetAlertKey(
    val ownerId: String,
    val budgetId: String,
    val periodId: String,
    val thresholdBps: Int,
) {
    val dedupeKey: String get() = "BUDGET_ALERT:$ownerId:$budgetId:$periodId:$thresholdBps"
}

data class BudgetAlert(
    val key: BudgetAlertKey,
    val budgetName: String,
    val percentBps: Long,
    val consumedMinor: Long,
    val limitMinor: Long,
    val currency: String,
)

/** Registro persistente de alertas ya emitidas. */
interface BudgetAlertLedger {
    suspend fun emittedThresholds(ownerId: String, budgetId: String, periodId: String): Set<Int>

    /** Registro atomico: true solo la primera vez que se registra la clave. */
    suspend fun tryRecord(key: BudgetAlertKey): Boolean
}

/** Entrega al centro de avisos (tipo BUDGET_ALERT, referencia BUDGET). Debe deduplicar por `key.dedupeKey`. */
fun interface BudgetAlertNotifier {
    suspend fun notify(alert: BudgetAlert)
}
