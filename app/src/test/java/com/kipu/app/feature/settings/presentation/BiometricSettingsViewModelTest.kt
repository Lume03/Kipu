package com.kipu.app.feature.settings.presentation

import androidx.fragment.app.FragmentActivity
import com.kipu.app.core.security.LocalAuthenticatorGateway
import com.kipu.app.core.security.LocalLockCoordinator
import com.kipu.app.core.security.model.LocalAuthenticatorCapability
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
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
class BiometricSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()

    private class FakeDeviceDao : DeviceAccountSettingsDao {
        var entity: DeviceAccountSettingsEntity? = null
        override suspend fun findSettings(userId: UUID): DeviceAccountSettingsEntity? = entity
        override fun observeSettings(userId: UUID): Flow<DeviceAccountSettingsEntity?> = MutableStateFlow(entity)
        override suspend fun putSettings(settings: DeviceAccountSettingsEntity) {
            entity = settings
        }
        override suspend fun deleteSettings(userId: UUID) {
            entity = null
        }
    }

    private class FakeGateway(var canAuth: Boolean = true) : LocalAuthenticatorGateway {
        var authSuccess: Boolean = true
        var biometrics = true
        var pendingResult: ((Boolean, String?) -> Unit)? = null
        var deferResult = false

        override fun getCapability(): LocalAuthenticatorCapability = LocalAuthenticatorCapability(
            hasBiometrics = biometrics,
            hasDeviceCredential = true,
            canAuthenticate = canAuth,
        )

        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            onResult: (Boolean, String?) -> Unit,
        ) {
            pendingResult = onResult
            if (!deferResult) onResult(authSuccess, if (authSuccess) null else "Error de autenticación")
        }
    }

    @Test
    fun `activation persists only after successful authentication and survives reopening`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeDeviceDao()
            val gateway = FakeGateway().apply { deferResult = true }
            val session = FakeCoordinator(LocalOwner(userId.toString(), false))
            val lock = LocalLockCoordinator(dao, session, { 0L }, backgroundScope)
            val vm = BiometricSettingsViewModel(dao, lock, gateway, session)
            advanceUntilIdle()
            vm.toggleLocalUnlock(mockk<FragmentActivity>())
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isLocalUnlockEnabled)
            assertTrue(dao.entity?.localUnlockEnabled != true)

            gateway.pendingResult!!(false, "Autenticación cancelada")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isLocalUnlockEnabled)
            assertTrue(dao.entity?.localUnlockEnabled != true)
            assertNotNull(vm.uiState.value.errorMessage)

            vm.toggleLocalUnlock(mockk<FragmentActivity>())
            gateway.pendingResult!!(true, null)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isLocalUnlockEnabled)
            assertTrue(dao.findSettings(userId)?.localUnlockEnabled == true)
            val reopened = BiometricSettingsViewModel(dao, lock, gateway, session)
            advanceUntilIdle()
            assertTrue(reopened.uiState.value.isLocalUnlockEnabled)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `device credential enables local protection when biometrics are unavailable`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeDeviceDao()
            val gateway = FakeGateway().apply { biometrics = false; deferResult = true }
            val session = FakeCoordinator(LocalOwner(userId.toString(), false))
            val lock = LocalLockCoordinator(dao, session, { 0L }, backgroundScope)
            val vm = BiometricSettingsViewModel(dao, lock, gateway, session)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.hasBiometrics)
            assertTrue(vm.uiState.value.hasDeviceCredential)
            assertTrue(vm.uiState.value.canAuthenticate)
            vm.toggleLocalUnlock(mockk<FragmentActivity>())
            assertNotNull(gateway.pendingResult)
            assertTrue(dao.entity?.localUnlockEnabled != true)
            gateway.pendingResult!!(true, null)
            advanceUntilIdle()
            assertTrue(dao.findSettings(userId)?.localUnlockEnabled == true)
            assertTrue(vm.uiState.value.isLocalUnlockEnabled)
        } finally { Dispatchers.resetMain() }
    }

    private class FakeCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
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

    @Test
    fun `loadSettings reflects capability and stored preference`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeDeviceDao()
            dao.entity = DeviceAccountSettingsEntity(userId = userId, localUnlockEnabled = true)
            val gateway = FakeGateway(canAuth = true)
            val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))
            val lockCoordinator = LocalLockCoordinator(dao, coordinator, { 0L }, CoroutineScope(Dispatchers.Unconfined))

            val viewModel = BiometricSettingsViewModel(dao, lockCoordinator, gateway, coordinator)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isLocalUnlockEnabled)
            assertTrue(state.canAuthenticate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `attempting to enable when device cannot authenticate sets error message`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeDeviceDao()
            val gateway = FakeGateway(canAuth = false)
            val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))
            val lockCoordinator = LocalLockCoordinator(dao, coordinator, { 0L }, CoroutineScope(Dispatchers.Unconfined))

            val viewModel = BiometricSettingsViewModel(dao, lockCoordinator, gateway, coordinator)
            advanceUntilIdle()

            viewModel.toggleLocalUnlock(null)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isLocalUnlockEnabled)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
