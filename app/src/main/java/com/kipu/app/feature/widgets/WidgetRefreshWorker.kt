package com.kipu.app.feature.widgets

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic redraw of the widgets, so they stay right while the app is closed and when the day changes. */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refresher: WidgetRefresher,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        refresher.updateAll()
        return Result.success()
    }
}
