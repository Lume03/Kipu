package com.kipu.app.feature.analytics

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.analytics.data.AnalyticsDao
import com.kipu.app.feature.analytics.data.AnalyticsRepository
import com.kipu.app.feature.analytics.domain.AnalyticsOverview
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.ObserveAnalyticsOverview
import com.kipu.app.feature.analytics.presentation.AnalyticsUiState
import com.kipu.app.feature.analytics.presentation.AnalyticsViewModel
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import com.kipu.app.feature.plans.domain.OfflineEntitlementLeasePolicy
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementGrantClaims
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseContext
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    @Test fun retryRecoversAndOwnerSwitchCancelsOldSourceWithoutLeakingErrors() = runTest {
        val session = mockk<SessionCoordinator>()
        val access = MutableStateFlow<LocalAccess>(LocalAccess.Available("owner-a", RemoteSession.Absent))
        every { session.localAccess } returns access
        val plan = mockk<VerifiedPlanAccess>()
        every { plan.observePremiumVerified() } returns flowOf(false)
        val dao = mockk<AnalyticsDao>()
        var fail = true
        val activeOwners = mutableSetOf<String>()
        every { dao.observeExpenses(any(), any(), any()) } answers {
            val owner = firstArg<String>()
            flow {
                if (fail) error("private synthetic details must never reach UI")
                activeOwners += owner
                try { emit(emptyList()); kotlinx.coroutines.awaitCancellation() }
                finally { activeOwners -= owner }
            }
        }
        every { dao.observeIncomes(any(), any(), any()) } returns flowOf(emptyList())
        val overview = mockk<ObserveAnalyticsOverview>()
        every { overview.invoke(any(), any(), any()) } returns flowOf(AnalyticsOverview.Empty)
        val vm = AnalyticsViewModel(session, plan, AnalyticsRepository(dao), overview)
        val collect = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        assertEquals(AnalyticsUiState.Error("No se pudieron cargar las estadísticas."), vm.state.value)
        fail = false
        vm.retry()
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.Content)
        assertEquals(setOf("owner-a"), activeOwners)
        access.value = LocalAccess.Available("owner-b", RemoteSession.Absent)
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.Content)
        assertEquals(setOf("owner-b"), activeOwners)
        access.value = LocalAccess.Protected("owner-b", "test")
        runCurrent()
        assertEquals(AnalyticsUiState.Locked, vm.state.value)
        assertTrue(activeOwners.isEmpty())
        access.value = LocalAccess.NoOwner
        runCurrent()
        assertEquals(AnalyticsUiState.NoOwner, vm.state.value)
        collect.cancel()
    }

    private class LeasePlanAccess : VerifiedPlanAccess {
        private val policy = OfflineEntitlementLeasePolicy()
        private val elapsed = MutableStateFlow(0L)
        private val grant = MutableStateFlow<OfflineEntitlementGrantClaims?>(null)
        private val serverTime = 1_000_000L
        private val lifetime = 60_000L
        fun activate() {
            grant.value = OfflineEntitlementGrantClaims(
                version = 1, keyId = "key", grantId = "d45373b2-9b32-4e19-a847-f585e59c50ba",
                userId = "owner", installationKeyThumbprint = "device", policyVersion = 1,
                tier = "PREMIUM", serverVerifiedAtMillis = serverTime,
                entitlementEndsAtMillis = serverTime + lifetime, notAfterMillis = serverTime + lifetime,
            )
        }
        fun advanceToBoundary() { elapsed.value = lifetime }
        private fun allowed(owner: String?): Boolean = owner == "owner" && policy.evaluate(
            grant.value,
            OfflineEntitlementLeaseContext("owner", "device", 1, 0L, 1, elapsed.value, 1),
        ) is OfflineEntitlementLeaseDecision.Allowed
        override suspend fun isPremiumVerified(ownerId: String?) = allowed(ownerId ?: "owner")
        override fun observePremiumVerified() = kotlinx.coroutines.flow.combine(grant, elapsed) { _, _ -> allowed("owner") }
    }

    @Test fun freePremiumAndExpiryReevaluateRequests() = runTest {
        val session = mockk<SessionCoordinator>()
        val access = MutableStateFlow<LocalAccess>(LocalAccess.Available("owner", RemoteSession.Absent))
        every { session.localAccess } returns access
        val plan = LeasePlanAccess()
        val dao = mockk<AnalyticsDao>()
        every { dao.observeExpenses(any(), any(), any()) } returns flowOf(emptyList())
        every { dao.observeIncomes(any(), any(), any()) } returns flowOf(emptyList())
        val overview = mockk<ObserveAnalyticsOverview>()
        every { overview.invoke(any(), any(), any()) } returns flowOf(AnalyticsOverview.Empty)
        val vm = AnalyticsViewModel(session, plan, AnalyticsRepository(dao), overview)
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
        plan.activate()
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.Comparison)
        plan.advanceToBoundary()
        runCurrent()
        assertTrue(vm.state.value is AnalyticsUiState.PremiumRequired)
        access.value = LocalAccess.NoOwner
        runCurrent()
        assertEquals(AnalyticsUiState.NoOwner, vm.state.value)
        collect.cancel()
    }
}
