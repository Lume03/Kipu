package com.kipu.app.feature.budgets.data.sync

interface BudgetSyncScheduler {
    fun scheduleSync(userId: String)
}
