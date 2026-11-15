package com.kipu.app.core.sync

import android.os.SystemClock
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/** Shared throttle for foreground, navigation and the visible-session refresh tick. */
@Singleton
class ForegroundSyncCoordinator @Inject constructor(
    private val movements: MovementSyncScheduler,
    private val categories: CategorySyncScheduler,
) {
    private var lastOwner: String? = null
    private var lastRequest: Long? = null

    @Synchronized
    fun requestSync(owner: String, nowMillis: Long = SystemClock.elapsedRealtime()) {
        val previous = lastRequest
        if (owner == lastOwner && previous != null && nowMillis - previous < MIN_INTERVAL_MILLIS) return
        lastOwner = owner
        lastRequest = nowMillis
        // Movement worker pulls SharedChangeFeed; categories and merchant rules use snapshots.
        // Both schedulers use REPLACE, so a stale retry never holds a new refresh hostage.
        movements.scheduleSync(owner)
        categories.scheduleSync(owner)
    }

    companion object { const val MIN_INTERVAL_MILLIS = 30_000L }
}
