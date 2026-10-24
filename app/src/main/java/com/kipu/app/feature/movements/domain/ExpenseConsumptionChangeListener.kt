package com.kipu.app.feature.movements.domain

/** Invalidation hook for local changes that can alter the derived expense-consumption projection. */
fun interface ExpenseConsumptionChangeListener {
    fun onExpenseConsumptionChanged(userId: String)
}

object NoOpExpenseConsumptionChangeListener : ExpenseConsumptionChangeListener {
    override fun onExpenseConsumptionChanged(userId: String) = Unit
}
