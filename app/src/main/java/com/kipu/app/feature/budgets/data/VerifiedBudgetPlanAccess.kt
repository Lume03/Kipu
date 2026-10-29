package com.kipu.app.feature.budgets.data

import com.kipu.app.feature.budgets.domain.BudgetPlanAccess
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import javax.inject.Inject

class VerifiedBudgetPlanAccess @Inject constructor(
    private val planAccess: VerifiedPlanAccess,
) : BudgetPlanAccess {
    override suspend fun isPremiumVerified(ownerId: String): Boolean = planAccess.isPremiumVerified(ownerId)
}
