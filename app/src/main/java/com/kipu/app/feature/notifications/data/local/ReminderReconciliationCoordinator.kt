package com.kipu.app.feature.notifications.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes reminder reconciliation and delivery across debt and recurrence sources. */
@Singleton
class ReminderReconciliationCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
