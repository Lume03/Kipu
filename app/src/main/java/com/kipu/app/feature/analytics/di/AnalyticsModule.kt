package com.kipu.app.feature.analytics.di

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.analytics.data.AnalyticsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides fun analyticsDao(database: KipuDatabase): AnalyticsDao = database.analyticsDao()
}
