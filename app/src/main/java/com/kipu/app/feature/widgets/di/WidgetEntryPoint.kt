package com.kipu.app.feature.widgets.di

import android.content.Context
import com.kipu.app.feature.widgets.data.WidgetDataProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Glance widgets are created by the system, not by Hilt, so they reach the app graph through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun dataProvider(): WidgetDataProvider
}

internal fun widgetDataProvider(context: Context): WidgetDataProvider =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).dataProvider()
