package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetConsumption
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus

/** Cupo Free de presupuestos activos. Premium no tiene tope. */
data class BudgetQuotaState(
    val activeCount: Int,
    val limit: Int,
    val isPremium: Boolean,
) {
    val isAtLimit: Boolean get() = !isPremium && activeCount >= limit
}

/** Resumen de un presupuesto para la lista. Los importes provienen de los casos de uso. */
data class BudgetListItem(
    val id: String,
    val name: String,
    val scopeLabel: String,
    val currency: String,
    val status: BudgetStatus,
    val periodType: BudgetPeriodType,
    val interval: BudgetInterval?,
    val consumption: BudgetConsumption?,
    val hasPendingChange: Boolean,
    val thresholdsBps: List<Int> = Budget.DEFAULT_THRESHOLDS_BPS,
    val syncIssue: String? = null,
)

sealed interface BudgetListUiState {
    data object Loading : BudgetListUiState
    data class Empty(val quota: BudgetQuotaState) : BudgetListUiState
    data class Content(val items: List<BudgetListItem>, val quota: BudgetQuotaState) : BudgetListUiState
    data class Error(val message: String) : BudgetListUiState
}

/** Una fila del historial: limite y alcance snapshot del periodo. */
data class BudgetPeriodRow(
    val periodId: String,
    val interval: BudgetInterval,
    val scopeLabel: String,
    val consumption: BudgetConsumption,
    val isCurrent: Boolean,
)

data class BudgetDetailContent(
    val budget: Budget,
    val scopeLabel: String,
    val current: BudgetPeriodRow?,
    val history: List<BudgetPeriodRow>,
    val pendingChangeLabel: String?,
    val isWorking: Boolean = false,
    val actionError: String? = null,
) {
    val isActive: Boolean get() = budget.isActive
}

sealed interface BudgetDetailUiState {
    data object Loading : BudgetDetailUiState
    data class Content(val content: BudgetDetailContent) : BudgetDetailUiState
    data object NotFound : BudgetDetailUiState
    data class Error(val message: String) : BudgetDetailUiState
}

sealed interface BudgetDetailEvent {
    data object Deleted : BudgetDetailEvent
    data object Finished : BudgetDetailEvent
}

/** Opcion del selector de alcance (solo categorias de gasto activas). */
data class BudgetCategoryOption(
    val id: String,
    val name: String,
    val parentId: String?,
    val parentName: String?,
) {
    val isSubcategory: Boolean get() = parentId != null
    val label: String get() = if (parentName != null) "$parentName > $name" else name
}

internal fun BudgetScope.categoryIdOrNull(): String? = (this as? BudgetScope.Category)?.categoryId
