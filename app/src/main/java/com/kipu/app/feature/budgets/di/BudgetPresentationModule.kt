package com.kipu.app.feature.budgets.di

import com.kipu.app.feature.budgets.presentation.BudgetCategoryOptionsSource
import com.kipu.app.feature.budgets.presentation.RepositoryBudgetCategoryOptionsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BudgetPresentationModule {
    @Binds
    abstract fun categoryOptions(implementation: RepositoryBudgetCategoryOptionsSource): BudgetCategoryOptionsSource
}
