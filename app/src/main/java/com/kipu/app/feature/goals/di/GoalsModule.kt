package com.kipu.app.feature.goals.di

import com.kipu.app.feature.goals.data.OfflineFirstGoalRepository
import com.kipu.app.feature.goals.domain.GoalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GoalsModule {
    @Binds
    abstract fun goalRepository(implementation: OfflineFirstGoalRepository): GoalRepository
}
