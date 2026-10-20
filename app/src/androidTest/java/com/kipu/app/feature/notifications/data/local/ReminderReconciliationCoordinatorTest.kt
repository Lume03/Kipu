package com.kipu.app.feature.notifications.data.local

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReconciliationCoordinatorTest {
    @Test
    fun sharedCoordinatorSerializesSuspendingCriticalSections() = runBlocking {
        val coordinator = ReminderReconciliationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondAttempted = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val first = async {
            coordinator.exclusive {
                order += "first-enter"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first-exit"
            }
        }
        firstEntered.await()
        val second = async {
            secondAttempted.complete(Unit)
            coordinator.exclusive {
                order += "second-enter"
                secondEntered.complete(Unit)
            }
        }
        secondAttempted.await()
        assertFalse("Second critical section must wait for the first", secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertTrue(secondEntered.isCompleted)
        assertEquals(listOf("first-enter", "first-exit", "second-enter"), order)
    }
}
