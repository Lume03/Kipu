package com.kipu.app.feature.goals.presentation

import com.kipu.app.feature.goals.domain.GoalProgress
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount

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
) {
    val activeGoalCount: Int get() = goals.count { it.goal.status == com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE }
    val freeGoalLimitReached: Boolean get() = !hasPremiumAccess && activeGoalCount >= FREE_GOAL_LIMIT

    companion object {
        const val FREE_GOAL_LIMIT = FreePlanLimits.DEFAULT_GOALS
    }
}
