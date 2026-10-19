package com.kipu.app.feature.goals.presentation

import com.kipu.app.feature.goals.domain.GoalProgress
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount

data class GoalListItemState(
    val goal: Goal,
    val progress: GoalProgress,
)

data class GoalManagementUiState(
    val userId: String? = null,
    val goals: List<GoalListItemState> = emptyList(),
    val backingAccounts: List<GoalBackingAccount> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasPremiumAccess: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
) {
    val activeGoalCount: Int get() = goals.count { it.goal.status == com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE }
    val freeGoalLimitReached: Boolean get() = !hasPremiumAccess && activeGoalCount >= FREE_GOAL_LIMIT

    companion object {
        const val FREE_GOAL_LIMIT = 2
    }
}
