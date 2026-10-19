package com.kipu.app.feature.goals.presentation

import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount

data class GoalContributionUiState(
    val userId: String? = null,
    val goal: Goal? = null,
    val accounts: List<GoalBackingAccount> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
)
