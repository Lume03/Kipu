package com.kipu.app.feature.plans.domain.model

data class FreePlanLimits(
    val policyVersion: Int = 1,
    val instruments: Int = DEFAULT_INSTRUMENTS,
    val customCategories: Int = DEFAULT_CUSTOM_CATEGORIES,
    val debts: Int = DEFAULT_DEBTS,
    val goals: Int = DEFAULT_GOALS,
    val budgets: Int = DEFAULT_BUDGETS,
) {
    companion object {
        const val DEFAULT_INSTRUMENTS = 4
        const val DEFAULT_CUSTOM_CATEGORIES = 5
        const val DEFAULT_DEBTS = 2
        const val DEFAULT_GOALS = 2
        const val DEFAULT_BUDGETS = 2
    }
}
