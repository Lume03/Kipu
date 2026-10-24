package com.kipu.app.feature.budgets.data

import com.kipu.app.feature.budgets.data.sync.BudgetMaintenanceScheduler
import com.kipu.app.feature.movements.domain.ExpenseConsumptionChangeListener
import javax.inject.Inject

class BudgetExpenseConsumptionChangeListener @Inject constructor(
    private val scheduler: BudgetMaintenanceScheduler,
) : ExpenseConsumptionChangeListener {
    override fun onExpenseConsumptionChanged(userId: String) {
        scheduler.scheduleAfterConsumptionChange(userId)
    }
}
