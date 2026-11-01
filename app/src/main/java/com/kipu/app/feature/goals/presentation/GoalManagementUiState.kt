package com.kipu.app.feature.goals.presentation

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.goals.domain.GoalProgress
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalStatus

data class GoalListItemState(
    val goal: Goal,
    val progress: GoalProgress,
)

data class GoalDisplaySections(val active: List<GoalListItemState>, val paused: List<GoalListItemState>, val completed: List<GoalListItemState>)

fun GoalManagementUiState.displaySections(): GoalDisplaySections {
    val visible = goals.filterNot { it.goal.id in removedGoalIds }
    val completed = visible.filter { it.goal.status == com.kipu.app.feature.goals.domain.model.GoalStatus.COMPLETED }
    val remaining = visible - completed.toSet()
    val paused = remaining.filter { it.goal.id in pausedGoalIds || it.goal.status != com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE }
    return GoalDisplaySections(remaining - paused.toSet(), paused, completed)
}


data class GoalCurrencyTotals(val target: Long, val reserved: Long)

internal fun goalTotals(goals: List<GoalListItemState>): Map<Currency, GoalCurrencyTotals> =
    goals.filter { it.goal.status == GoalStatus.ACTIVE }.groupBy { it.goal.currency }.mapValues { (_, entries) ->
        GoalCurrencyTotals(
            target = entries.fold(0L) { total, item -> Math.addExact(total, item.goal.target.minorUnits) },
            reserved = entries.fold(0L) { total, item -> Math.addExact(total, item.progress.reserved.minorUnits) },
        )
    }

/** Totales por moneda de las metas activas visibles; el ViewModel los recalcula al cambiar metas o preferencias. */
internal fun GoalManagementUiState.withTotals(): GoalManagementUiState = copy(activeTotals = goalTotals(displaySections().active))

data class GoalManagementUiState(
    val userId: String? = null,
    val goals: List<GoalListItemState> = emptyList(),
    val backingAccounts: List<GoalBackingAccount> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasPremiumAccess: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val accountOptions: List<com.kipu.app.feature.accounts.domain.model.Account> = emptyList(),
    val pausedGoalIds: Set<String> = emptySet(),
    val removedGoalIds: Set<String> = emptySet(),
    val activeTotals: Map<Currency, GoalCurrencyTotals> = emptyMap(),
) {
    val activeGoalCount: Int get() = goals.count { it.goal.status == com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE }
    val freeGoalLimitReached: Boolean get() = !hasPremiumAccess && activeGoalCount >= FREE_GOAL_LIMIT

    companion object {
        const val FREE_GOAL_LIMIT = FreePlanLimits.DEFAULT_GOALS
    }
}
