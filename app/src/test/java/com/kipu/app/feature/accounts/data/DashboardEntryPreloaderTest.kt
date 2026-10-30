package com.kipu.app.feature.accounts.data

import androidx.work.WorkInfo
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.presentation.SessionEntryCurtain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardEntryPreloaderTest {
    private fun work(state: WorkInfo.State, attempts: Int = 0): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { runAttemptCount } returns attempts
    }

    @Test
    fun `failed entry still requires hydration on retry after curtain dismissal`() = runTest {
        val session = mockk<SessionCoordinator> { every { currentOwner } returns null }
        val curtain = SessionEntryCurtain()
        val preloader = DashboardEntryPreloader(mockk(), session, curtain)
        // Normal local-first screen visits do not require remote hydration.
        preloader.awaitInitialData()
        curtain.start("test@example.com")
        curtain.authenticated()
        assertTrue(runCatching { preloader.awaitInitialData() }.isFailure)
        curtain.dismiss()
        // Losing the verified owner must remain an error, not silently become ready on retry.
        assertTrue(runCatching { preloader.awaitInitialData() }.isFailure)
    }

    @Test
    fun `does not reveal initial cached data while remote hydration is running`() = runTest {
        val states = MutableStateFlow<WorkInfo?>(null)
        val ready = async { awaitDashboardSync(states) }
        runCurrent()
        states.value = work(WorkInfo.State.RUNNING)
        runCurrent()
        assertFalse(ready.isCompleted)
        states.value = work(WorkInfo.State.SUCCEEDED)
        ready.await()
        assertTrue(ready.isCompleted)
    }

    @Test
    fun `backoff resolves as an explicit retry error instead of a false ready state`() = runTest {
        val result = runCatching {
            awaitDashboardSync(MutableStateFlow(work(WorkInfo.State.ENQUEUED, attempts = 1)))
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `a running first attempt keeps waiting instead of failing`() = runTest {
        // WorkManager counts the attempt in progress, so RUNNING already reports one attempt.
        val states = MutableStateFlow<WorkInfo?>(work(WorkInfo.State.RUNNING, attempts = 1))
        val ready = async { awaitDashboardSync(states) }
        runCurrent()
        assertFalse(ready.isCompleted)
        states.value = work(WorkInfo.State.SUCCEEDED, attempts = 1)
        ready.await()
    }

    @Test
    fun `work replaced by another scheduler is followed to its replacement`() = runTest {
        val states = MutableStateFlow<WorkInfo?>(work(WorkInfo.State.CANCELLED))
        val ready = async { awaitDashboardSync(states) }
        runCurrent()
        assertFalse(ready.isCompleted)
        states.value = listOf(work(WorkInfo.State.CANCELLED), work(WorkInfo.State.RUNNING, attempts = 1)).currentSync()
        runCurrent()
        assertFalse(ready.isCompleted)
        states.value = work(WorkInfo.State.SUCCEEDED, attempts = 1)
        ready.await()
    }

    @Test
    fun `the unfinished sync wins over finished history for the same owner`() {
        val running = work(WorkInfo.State.RUNNING, attempts = 1)
        assertTrue(listOf(work(WorkInfo.State.SUCCEEDED), running).currentSync() === running)
        val succeeded = work(WorkInfo.State.SUCCEEDED)
        assertTrue(listOf(work(WorkInfo.State.CANCELLED), succeeded).currentSync() === succeeded)
    }

    @Test
    fun `offline work has a bounded wait and does not silently reveal partial data`() = runTest {
        val result = async {
            runCatching { awaitDashboardSync(MutableStateFlow(work(WorkInfo.State.ENQUEUED))) }
        }
        advanceTimeBy(15_001)
        assertTrue(result.await().exceptionOrNull() is IllegalStateException)
    }
}
