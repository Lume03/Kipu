package com.kipu.app.feature.budgets.di

import com.kipu.app.feature.budgets.data.BudgetCenterAlertNotifier
import com.kipu.app.feature.budgets.data.BudgetExpenseConsumptionChangeListener
import com.kipu.app.feature.budgets.data.OfflineFirstBudgetRepository
import com.kipu.app.feature.budgets.data.RoomBudgetAlertLedger
import com.kipu.app.feature.budgets.data.RoomBudgetCategoryLookup
import com.kipu.app.feature.budgets.data.VerifiedBudgetPlanAccess
import com.kipu.app.feature.budgets.data.sync.BudgetMaintenanceScheduler
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.budgets.data.sync.WorkManagerBudgetMaintenanceScheduler
import com.kipu.app.feature.budgets.data.sync.WorkManagerBudgetSyncScheduler
import com.kipu.app.feature.budgets.domain.BudgetAlertLedger
import com.kipu.app.feature.budgets.domain.BudgetAlertNotifier
import com.kipu.app.feature.budgets.domain.BudgetCategoryLookup
import com.kipu.app.feature.budgets.domain.BudgetPlanAccess
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.BudgetValidator
import com.kipu.app.feature.budgets.domain.CreateBudget
import com.kipu.app.feature.budgets.domain.DeleteBudget
import com.kipu.app.feature.budgets.domain.EditBudget
import com.kipu.app.feature.budgets.domain.EvaluateBudgetAlerts
import com.kipu.app.feature.budgets.domain.ExpenseBudgetConsumptionReader
import com.kipu.app.feature.budgets.domain.BudgetConsumptionReader
import com.kipu.app.feature.budgets.domain.FinishBudget
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import com.kipu.app.feature.budgets.domain.RenewBudgetPeriods
import com.kipu.app.feature.budgets.domain.RunBudgetMaintenance
import com.kipu.app.feature.movements.domain.ExpenseConsumptionChangeListener
import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
abstract class BudgetsModule {
    @Binds abstract fun bindBudgetRepository(implementation: OfflineFirstBudgetRepository): BudgetRepository
    @Binds abstract fun bindBudgetCategoryLookup(implementation: RoomBudgetCategoryLookup): BudgetCategoryLookup
    @Binds abstract fun bindBudgetPlanAccess(implementation: VerifiedBudgetPlanAccess): BudgetPlanAccess
    @Binds abstract fun bindBudgetAlertLedger(implementation: RoomBudgetAlertLedger): BudgetAlertLedger
    @Binds abstract fun bindBudgetAlertNotifier(implementation: BudgetCenterAlertNotifier): BudgetAlertNotifier
    @Binds abstract fun bindBudgetSyncScheduler(implementation: WorkManagerBudgetSyncScheduler): BudgetSyncScheduler
    @Binds abstract fun bindBudgetMaintenanceScheduler(implementation: WorkManagerBudgetMaintenanceScheduler): BudgetMaintenanceScheduler
    @Binds abstract fun bindExpenseConsumptionChangeListener(implementation: BudgetExpenseConsumptionChangeListener): ExpenseConsumptionChangeListener

    companion object {
        @Provides fun deviceZoneId(): ZoneId = ZoneId.systemDefault()

        @Provides fun budgetValidator(categories: BudgetCategoryLookup) = BudgetValidator(categories)

        @Provides
        fun budgetConsumptionReader(
            expenses: ExpenseConsumptionRepository,
            categories: BudgetCategoryLookup,
        ): BudgetConsumptionReader = ExpenseBudgetConsumptionReader(expenses, categories)

        @Provides fun renewBudgetPeriods(repository: BudgetRepository) = RenewBudgetPeriods(repository)

        @Provides
        fun createBudget(
            repository: BudgetRepository,
            validator: BudgetValidator,
            planAccess: BudgetPlanAccess,
        ) = CreateBudget(repository, validator, planAccess)

        @Provides
        fun editBudget(
            repository: BudgetRepository,
            validator: BudgetValidator,
            renew: RenewBudgetPeriods,
        ) = EditBudget(repository, validator, renew)

        @Provides fun finishBudget(repository: BudgetRepository) = FinishBudget(repository)
        @Provides fun deleteBudget(repository: BudgetRepository) = DeleteBudget(repository)

        @Provides
        fun getBudgetConsumption(repository: BudgetRepository, reader: BudgetConsumptionReader) =
            GetBudgetConsumption(repository, reader)

        @Provides
        fun evaluateBudgetAlerts(
            repository: BudgetRepository,
            reader: BudgetConsumptionReader,
            ledger: BudgetAlertLedger,
            notifier: BudgetAlertNotifier,
        ) = EvaluateBudgetAlerts(repository, reader, ledger, notifier)

        @Provides
        fun runBudgetMaintenance(
            repository: BudgetRepository,
            renew: RenewBudgetPeriods,
            alerts: EvaluateBudgetAlerts,
        ) = RunBudgetMaintenance(repository, renew, alerts)
    }
}
