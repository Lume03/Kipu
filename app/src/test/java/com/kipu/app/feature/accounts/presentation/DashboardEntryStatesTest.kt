package com.kipu.app.feature.accounts.presentation

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardEntryStatesTest {
    private fun data(cached: Boolean) = FinancialDashboardData(Money(if (cached) 12345L else 0L, Currency.PEN),
        Money(0, Currency.USD), emptyList(), emptyList(), if (cached) 1 else 0)

    @Test fun `cached dashboard appears immediately while remote sync still waits offline`() = runTest {
        val sync = CompletableDeferred<Unit>()
        val states = mutableListOf<DashboardUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dashboardEntryStates(flowOf(data(true)), flowOf(false)) { sync.await() }.collect { states += it }
        }
        runCurrent()
        assertFalse(states.last().isLoading)
        assertEquals(12345L, states.last().dashboardData!!.totalPen.minorUnits)
        assertFalse(sync.isCompleted)
        assertEquals(0L, currentTime)
        sync.completeExceptionally(java.io.IOException("offline"))
        runCurrent()
        assertEquals("offline", states.last().errorMessage)
        assertEquals(12345L, states.last().dashboardData!!.totalPen.minorUnits)
        assertFalse(states.last().isLoading)
        job.cancel()
    }

    @Test fun `first device empty Room waits for remote then displays hydrated snapshot`() = runTest {
        val sync = CompletableDeferred<Unit>()
        val local = MutableStateFlow(data(false))
        val states = mutableListOf<DashboardUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dashboardEntryStates(local, flowOf(false)) { sync.await() }.collect { states += it }
        }
        runCurrent()
        assertTrue(states.last().isLoading)
        local.value = data(true)
        sync.complete(Unit)
        runCurrent()
        assertFalse(states.last().isLoading)
        assertEquals(12345L, states.last().dashboardData!!.totalPen.minorUnits)
        job.cancel()
    }

    @Test fun `first device hydration failure resolves retry without exposing false zeros`() = runTest {
        val states = mutableListOf<DashboardUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dashboardEntryStates(flowOf(data(false)), flowOf(false)) { throw java.io.IOException("offline") }.collect { states += it }
        }
        runCurrent()
        assertFalse(states.last().isLoading)
        assertNull(states.last().dashboardData)
        assertEquals("offline", states.last().errorMessage)
        job.cancel()
    }
    @Test fun `archived zero balance instruments are still a usable local snapshot`() = runTest {
        val states = mutableListOf<DashboardUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dashboardEntryStates(flowOf(data(false).copy(hasStoredInstruments = true)), flowOf(false)) {
                kotlinx.coroutines.awaitCancellation()
            }.collect { states += it }
        }
        runCurrent()
        assertFalse(states.last().isLoading)
        assertNotNull(states.last().dashboardData)
        job.cancel()
    }

}
