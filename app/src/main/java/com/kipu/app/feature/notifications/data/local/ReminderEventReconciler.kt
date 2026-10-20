package com.kipu.app.feature.notifications.data.local

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderEventReconciler @Inject constructor(
    private val eventDao: LocalReminderEventDao,
) {
    suspend fun retainCurrentDate(userId: String, sourceId: String, dueDate: String, now: Instant) {
        eventDao.tombstoneReplacedEvents(userId, sourceId, dueDate, now)
    }

    suspend fun invalidateSource(userId: String, sourceId: String, now: Instant) {
        eventDao.tombstoneSource(userId, sourceId, now)
    }
}