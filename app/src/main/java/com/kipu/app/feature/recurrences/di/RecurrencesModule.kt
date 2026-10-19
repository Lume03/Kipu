package com.kipu.app.feature.recurrences.di

import com.kipu.app.feature.recurrences.data.repository.OfflineFirstRecurrenceRepository
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecurrencesModule {
    @Binds
    abstract fun recurrenceRepository(implementation: OfflineFirstRecurrenceRepository): RecurrenceRepository
}
