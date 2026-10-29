package com.kipu.app.feature.recurrences.di

import com.kipu.app.feature.recurrences.data.repository.OfflineFirstRecurrenceRepository
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderScheduler
import com.kipu.app.feature.recurrences.data.sync.WorkManagerRecurrenceReminderScheduler
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.capture.domain.CaptureCandidateResolvedListener
import com.kipu.app.feature.recurrences.domain.MatchCapturedMovementToExpected
import dagger.multibindings.IntoSet
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecurrencesModule {
    @Binds @IntoSet abstract fun matchExpected(implementation: MatchCapturedMovementToExpected): CaptureCandidateResolvedListener

    @Binds
    abstract fun recurrenceRepository(implementation: OfflineFirstRecurrenceRepository): RecurrenceRepository

    @Binds
    abstract fun recurrenceReminderScheduler(implementation: WorkManagerRecurrenceReminderScheduler): RecurrenceReminderScheduler
}
