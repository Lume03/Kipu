package com.kipu.app.qa

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.budgets.data.remote.BudgetApi
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.debts.data.remote.DebtApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access for explicitly opted-in acceptance on the owner's connected device. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlanningSyncVerificationAccess {
    fun database(): KipuDatabase
    fun session(): SessionCoordinator
    fun financialApi(): FinancialInstrumentsApi
    fun movementApi(): MovementApi
    fun budgetApi(): BudgetApi
    fun debtApi(): DebtApi
    fun historyQuery(): com.kipu.app.feature.movements.domain.QueryMovementHistory
}
