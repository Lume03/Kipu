package com.kipu.app.feature.goals.presentation

import com.kipu.app.feature.goals.domain.GoalProgress
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalEvent

data class GoalDetailUiState(
    val userId: String? = null,
    val goal: Goal? = null,
    val progress: GoalProgress? = null,
    val events: List<GoalEvent> = emptyList(),
    val backingAccounts: List<GoalBackingAccount> = emptyList(),
    val otherGoals: List<Goal> = emptyList(),
    val hasPremiumAccess: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
)
