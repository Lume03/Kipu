package com.kipu.app.feature.settings.presentation

import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.sync.ProfileSyncScheduler
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.SyncState
import com.kipu.app.feature.settings.domain.model.ThemeMode
import com.kipu.app.feature.settings.domain.model.UserProfile
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Test
    fun planCardTracksFreePremiumAndDeveloperWithoutManualRefresh() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val refresh = io.mockk.mockk<com.kipu.app.feature.plans.data.entitlement.TestPlanRefresh>()
            io.mockk.coEvery { refresh.refresh() } returns com.kipu.app.feature.plans.data.entitlement.TestPlanRefreshResult(true)
            val dao = io.mockk.mockk<com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao>()
            val access = MutableStateFlow<com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity?>(
                com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity(userId, 1, "PREMIUM", source = "SIGNED_DEVELOPER_GRANT"),
            )
            io.mockk.every { dao.observe(userId) } returns access
            val viewModel = SettingsViewModel(
                FakeProfilePreferencesRepository(), FakeSessionCoordinator(LocalOwner(userId.toString(), false)),
                FakeProfileSyncScheduler(), io.mockk.mockk(relaxed = true), io.mockk.mockk(relaxed = true), refresh, dao,
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isDeveloperPlan)
            assertEquals("Plan Desarrollador", viewModel.uiState.value.planCard.title)
            assertEquals("Todas las funciones Premium, sin vencimiento", viewModel.uiState.value.planCard.subtitle)
            assertFalse(viewModel.uiState.value.planCard.clickable)

            val expiry = java.time.Instant.parse("2026-12-31T12:00:00Z")
            access.value = com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity(
                userId, 1, "PREMIUM", entitlementExpiresAt = expiry, source = "SIGNED_SERVER_GRANT",
            )
            advanceUntilIdle()
            assertEquals("Plan Premium", viewModel.uiState.value.planCard.title)
            assertEquals(expiry, viewModel.uiState.value.planCard.expiresAt)
            assertTrue(viewModel.uiState.value.planCard.subtitle.contains("2026"))
            assertTrue(viewModel.uiState.value.planCard.clickable)
            assertFalse(viewModel.uiState.value.isDeveloperPlan)

            access.value = access.value!!.copy(entitlementExpiresAt = null)
            advanceUntilIdle()
            assertEquals("Acceso Premium sin vencimiento", viewModel.uiState.value.planCard.subtitle)

            access.value = com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity.verifiedFree(userId)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isDeveloperPlan)
            assertEquals("Plan Free", viewModel.uiState.value.planCard.title)
            assertTrue(viewModel.uiState.value.planCard.clickable)
            io.mockk.coVerify(exactly = 0) { refresh.refresh() }
            viewModel.refreshTestPlan()
            advanceUntilIdle()
            io.mockk.coVerify(exactly = if (com.kipu.app.BuildConfig.TEST_PLAN_OVERRIDES_ENABLED) 1 else 0) { refresh.refresh() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()

    private class FakeSessionCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<com.kipu.app.core.session.RemoteSession>(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow<com.kipu.app.core.session.LocalAccess>(
            currentOwner?.let { com.kipu.app.core.session.LocalAccess.Available(it.verifiedUserId, com.kipu.app.core.session.RemoteSession.Absent) }
                ?: com.kipu.app.core.session.LocalAccess.NoOwner
        )
        override suspend fun setActiveOwner(userId: String) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override suspend fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
    }

    private class FakeProfilePreferencesRepository : ProfilePreferencesRepository {
        val profileFlow = MutableStateFlow<UserProfile?>(null)
        var lastUpdatedDelta: ProfilePreferenceDelta? = null
        var syncPendingCalled = false

        override fun observeProfile(userId: UUID): Flow<UserProfile?> = profileFlow

        override suspend fun getProfile(userId: UUID): UserProfile? = profileFlow.value

        override suspend fun updatePreferences(userId: UUID, delta: ProfilePreferenceDelta): Result<UserProfile> {
            lastUpdatedDelta = delta
            val current = profileFlow.value ?: UserProfile(userId = userId, displayName = "")
            val updated = current.copy(
                displayName = delta.displayName ?: current.displayName,
                currencyCode = delta.currencyCode ?: current.currencyCode,
                monthStart = delta.monthStart ?: current.monthStart,
                hideBalances = delta.hideBalances ?: current.hideBalances,
                themeMode = delta.themeMode ?: current.themeMode,
                syncState = SyncState.PENDING,
            )
            profileFlow.value = updated
            return Result.success(updated)
        }

        override suspend fun setHideBalances(userId: UUID, hideBalances: Boolean): Result<UserProfile> {
            return updatePreferences(userId, ProfilePreferenceDelta(hideBalances = hideBalances))
        }

        override suspend fun syncPendingPreferences(userId: UUID): Result<Unit> {
            syncPendingCalled = true
            return Result.success(Unit)
        }

        override suspend fun refreshProfile(userId: UUID): Result<UserProfile> {
            return Result.success(profileFlow.value ?: UserProfile(userId = userId, displayName = ""))
        }
    }

    private class FakeProfileSyncScheduler : ProfileSyncScheduler {
        var scheduledUserId: UUID? = null
        override fun scheduleSync(userId: UUID) {
            scheduledUserId = userId
        }
    }

    @Test
    fun `initial load reflects user profile from repository`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            repository.profileFlow.value = UserProfile(
                userId = userId,
                displayName = "María",
                currencyCode = "PEN",
                monthStart = 15,
                hideBalances = false,
                themeMode = ThemeMode.DARK,
            )
            val coordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false))
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = coordinator,
                profileSyncScheduler = FakeProfileSyncScheduler(),
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("María", state.displayName)
            assertEquals("PEN", state.currencyCode)
            assertEquals(15, state.monthStart)
            assertEquals(ThemeMode.DARK, state.themeMode)
            assertFalse(state.hideBalances)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `validating month start accepts 1 to 28 and rejects out of bounds`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false))
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = coordinator,
                profileSyncScheduler = FakeProfileSyncScheduler(),
            )

            advanceUntilIdle()

            viewModel.onMonthStartChanged(15)
            assertNull(viewModel.uiState.value.monthStartError)

            viewModel.onMonthStartChanged(0)
            assertNotNull(viewModel.uiState.value.monthStartError)

            viewModel.onMonthStartChanged(29)
            assertNotNull(viewModel.uiState.value.monthStartError)

            viewModel.onMonthStartChanged(28)
            assertNull(viewModel.uiState.value.monthStartError)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `rapid month start taps persist the latest value`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            repository.profileFlow.value = UserProfile(userId = userId, displayName = "Test", monthStart = 15)
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false)),
                profileSyncScheduler = FakeProfileSyncScheduler(),
            )
            advanceUntilIdle()

            viewModel.stepMonthStart(1)
            viewModel.stepMonthStart(1)
            advanceUntilIdle()

            assertEquals(17, viewModel.uiState.value.monthStart)
            assertEquals(17, repository.lastUpdatedDelta?.monthStart)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `toggling hide balances updates state immediately`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            repository.profileFlow.value = UserProfile(userId = userId, displayName = "Test", hideBalances = false)
            val coordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false))
            val scheduler = FakeProfileSyncScheduler()
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = coordinator,
                profileSyncScheduler = scheduler,
            )

            advanceUntilIdle()

            viewModel.toggleHideBalances()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hideBalances)
            assertEquals(true, repository.lastUpdatedDelta?.hideBalances)
            assertEquals(userId, scheduler.scheduledUserId)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
