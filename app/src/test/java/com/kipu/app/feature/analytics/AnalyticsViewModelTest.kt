package com.kipu.app.feature.analytics

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.analytics.data.AnalyticsDao
import com.kipu.app.feature.analytics.data.AnalyticsRepository
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.presentation.AnalyticsUiState
import com.kipu.app.feature.analytics.presentation.AnalyticsViewModel
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.feature.home.domain.ObserveNetWorthSummary
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun freePremiumAndExpiryReevaluateRequests() = runTest {
        val session = mockk<SessionCoordinator>()
        val access = MutableStateFlow<LocalAccess>(LocalAccess.Available("owner", RemoteSession.Absent))
        every { session.localAccess } returns access
        val premium = MutableStateFlow(false)
        val plan = mockk<VerifiedPlanAccess>()
        every { plan.observePremiumVerified() } returns premium
        val dao = mockk<AnalyticsDao>()
        every { dao.observeExpenses(any(), any(), any()) } returns flowOf(emptyList())
        val worth = mockk<ObserveNetWorthSummary>()
        every { worth.invoke(any()) } returns flowOf(NetWorthSummary.NoData)
        val vm = AnalyticsViewModel(session, plan, AnalyticsRepository(dao), worth)
        val collect = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.Content)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val current = AnalyticsPeriod.currentMonth(today, zone)
        val previous = AnalyticsPeriod.month(YearMonth.from(today).minusMonths(1), today, zone)
        vm.compare(previous, current)
        runCurrent()
        assertTrue((vm.state.value as AnalyticsUiState.PremiumRequired).basicHistoryAvailable)
        premium.value = true
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.Comparison)
        premium.value = false
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.PremiumRequired)
        access.value = LocalAccess.NoOwner
        runCurrent()
        assertEquals(AnalyticsUiState.NoOwner, vm.state.value)
        collect.cancel()
    }
}
