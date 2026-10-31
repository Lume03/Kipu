package com.kipu.app.feature.settings.presentation

import android.content.Intent
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
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
class PermissionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()
    private var verifiedPremium = false
    private val categories = mockk<CategoriesRepository> {
        every { observePremiumVerified(com.kipu.app.core.finance.domain.model.UserId(userId.toString())) } answers { flowOf(verifiedPremium) }
    }
    private val captureGate = object : CaptureAccessGate {
        override fun currentOwnerId() = userId.toString()
        override suspend fun evaluateCurrent() = evaluate(userId.toString())
        override suspend fun evaluate(ownerId: String): CaptureAccessDecision =
            if (verifiedPremium) CaptureAccessDecision.Allowed(EffectiveEntitlement(true, null))
            else CaptureAccessDecision.PremiumRequired
    }

    private class FakePermissionDao : PermissionConsentDao {
        val installationState = mutableMapOf<String, InstallationPermissionStateEntity>()
        val accountConsents = MutableStateFlow<List<AccountSourceConsentEntity>>(emptyList())

        override suspend fun findInstallationPermission(source: String): InstallationPermissionStateEntity? =
            installationState[source]

        override fun observeInstallationPermissions(): Flow<List<InstallationPermissionStateEntity>> =
            MutableStateFlow(installationState.values.toList())

        override suspend fun putInstallationPermission(entity: InstallationPermissionStateEntity) {
            installationState[entity.source] = entity
        }

        override suspend fun findAccountConsent(userId: UUID, source: String): AccountSourceConsentEntity? =
            accountConsents.value.find { it.userId == userId && it.source == source }

        override fun observeAccountConsents(userId: UUID): Flow<List<AccountSourceConsentEntity>> =
            accountConsents

        override suspend fun putAccountConsent(entity: AccountSourceConsentEntity) {
            val list = accountConsents.value.filterNot { it.userId == entity.userId && it.source == entity.source }.toMutableList()
            list.add(entity)
            accountConsents.value = list
        }

        override suspend fun revokeAllAccountConsents(userId: UUID) {
            accountConsents.value = accountConsents.value.map {
                if (it.userId == userId) it.copy(consentState = "REVOKED") else it
            }
        }
    }

    private class FakeGateway : PermissionSourceGateway {
        var ownNotificationsAuth = DeviceAuthorization.GRANTED
        var listenerAuth = DeviceAuthorization.DENIED

        override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization {
            return when (source) {
                PermissionSource.OWN_NOTIFICATIONS -> ownNotificationsAuth
                PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> listenerAuth
                PermissionSource.YAPE_RECEIPT_OCR -> DeviceAuthorization.NOT_APPLICABLE
            }
        }

        override fun createSettingsIntent(source: PermissionSource): Intent = Intent()

        override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> {
            return mapOf(
                PermissionSource.OWN_NOTIFICATIONS to ownNotificationsAuth,
                PermissionSource.OTHER_APP_NOTIFICATION_CONTENT to listenerAuth,
            )
        }
    }

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

    @Test
    fun `showRationale and dismissRationale toggle state`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false))
            val viewModel = PermissionsViewModel(dao, gateway, coordinator, captureGate, categories)

            advanceUntilIdle()

            viewModel.showRationale(PermissionSource.OWN_NOTIFICATIONS)
            assertEquals(PermissionSource.OWN_NOTIFICATIONS, viewModel.uiState.value.showRationaleFor)

            viewModel.dismissRationale()
            assertNull(viewModel.uiState.value.showRationaleFor)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `granting notification listener on Free tier shows premium error and does not grant`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false))
            val viewModel = PermissionsViewModel(dao, gateway, coordinator, captureGate, categories)

            advanceUntilIdle()

            viewModel.grantConsent(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.message)
            assertTrue(viewModel.uiState.value.message!!.contains("Premium"))
            val consent = dao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name)
            assertNull(consent)
            assertNull(viewModel.uiState.value.openSettingsFor)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `verified Premium permits granting listener consent without UI override`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            verifiedPremium = true
            val dao = FakePermissionDao()
            val viewModel = PermissionsViewModel(dao, FakeGateway(),
                FakeSessionCoordinator(LocalOwner(userId.toString(), false)), captureGate, categories)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isPremiumUser)
            viewModel.grantConsent(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
            advanceUntilIdle()
            assertEquals("GRANTED", dao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name)?.consentState)
            viewModel.revokeConsent(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
            advanceUntilIdle()
            assertEquals("REVOKED", dao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name)?.consentState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `listener consent requests Android settings and refresh uses the real permission`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            verifiedPremium = true
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val viewModel = PermissionsViewModel(dao, gateway,
                FakeSessionCoordinator(LocalOwner(userId.toString(), false)), captureGate, categories)
            val source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT
            advanceUntilIdle()
            viewModel.grantConsent(source)
            advanceUntilIdle()

            assertEquals("GRANTED", dao.findAccountConsent(userId, source.name)?.consentState)
            assertEquals(source, viewModel.uiState.value.openSettingsFor)
            val pending = viewModel.uiState.value.items.first { it.source == source }
            assertEquals(DeviceAuthorization.DENIED, pending.deviceAuth)
            assertTrue(pending.needsAndroidActivation)
            assertFalse(pending.isProcessingAuthorized)
            assertFalse(viewModel.uiState.value.message.orEmpty().contains("activada"))

            viewModel.onSettingsOpened(source)
            assertNull(viewModel.uiState.value.openSettingsFor)
            viewModel.refresh()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.message.orEmpty().contains("Puedes activarlo cuando quieras"))
            assertFalse(viewModel.uiState.value.items.first { it.source == source }.isProcessingAuthorized)
            assertNull(viewModel.uiState.value.openSettingsFor)

            viewModel.onSettingsOpened(source)
            gateway.listenerAuth = DeviceAuthorization.GRANTED
            viewModel.refresh()
            advanceUntilIdle()
            val active = viewModel.uiState.value.items.first { it.source == source }
            assertEquals(DeviceAuthorization.GRANTED, active.deviceAuth)
            assertTrue(active.isProcessingAuthorized)
            assertFalse(active.needsAndroidActivation)
            assertEquals("Captura automática activada.", viewModel.uiState.value.message)
            assertNull(viewModel.uiState.value.openSettingsFor)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `listener consent with Android already enabled does not request settings`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            verifiedPremium = true
            val gateway = FakeGateway().apply { listenerAuth = DeviceAuthorization.GRANTED }
            val viewModel = PermissionsViewModel(FakePermissionDao(), gateway,
                FakeSessionCoordinator(LocalOwner(userId.toString(), false)), captureGate, categories)
            advanceUntilIdle()
            viewModel.grantConsent(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.openSettingsFor)
            assertTrue(viewModel.uiState.value.items.first { it.source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT }.isProcessingAuthorized)
            assertEquals("Captura automática activada.", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `unavailable Android settings keeps consent and gives a recoverable message`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            verifiedPremium = true
            val dao = FakePermissionDao()
            val viewModel = PermissionsViewModel(dao, FakeGateway(),
                FakeSessionCoordinator(LocalOwner(userId.toString(), false)), captureGate, categories)
            advanceUntilIdle()
            viewModel.grantConsent(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
            advanceUntilIdle()
            viewModel.onSettingsUnavailable()
            assertNull(viewModel.uiState.value.openSettingsFor)
            assertEquals("GRANTED", dao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name)?.consentState)
            assertFalse(viewModel.uiState.value.items.first { it.source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT }.isProcessingAuthorized)
            assertTrue(viewModel.uiState.value.message.orEmpty().contains("Puedes abrirlos"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `Yape OCR is Free revocable versioned and needs no Android settings`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val source = PermissionSource.YAPE_RECEIPT_OCR
            val dao = FakePermissionDao()
            val viewModel = PermissionsViewModel(dao, FakeGateway(),
                FakeSessionCoordinator(LocalOwner(userId.toString(), false)), captureGate, categories)
            advanceUntilIdle()
            viewModel.grantConsent(source)
            advanceUntilIdle()
            val granted = viewModel.uiState.value.items.first { it.source == source }
            assertTrue(granted.isProcessingAuthorized)
            assertFalse(granted.requiresPremium)
            assertFalse(granted.needsAndroidActivation)
            assertEquals(DeviceAuthorization.NOT_APPLICABLE, granted.deviceAuth)
            assertNull(viewModel.uiState.value.openSettingsFor)
            val persisted = requireNotNull(dao.findAccountConsent(userId, source.name))
            assertEquals(com.kipu.app.feature.receipts.domain.OCR_CONSENT_VERSION, persisted.explanationVersion)
            dao.putAccountConsent(persisted.copy(explanationVersion = persisted.explanationVersion + 1))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.items.first { it.source == source }.isProcessingAuthorized)
            viewModel.grantConsent(source)
            advanceUntilIdle()
            viewModel.revokeConsent(source)
            advanceUntilIdle()
            assertEquals("REVOKED", dao.findAccountConsent(userId, source.name)?.consentState)
            assertFalse(viewModel.uiState.value.items.first { it.source == source }.isProcessingAuthorized)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `granting and revoking own notifications updates dao and state`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId.toString(), false))
            val viewModel = PermissionsViewModel(dao, gateway, coordinator, captureGate, categories)

            advanceUntilIdle()

            viewModel.grantConsent(PermissionSource.OWN_NOTIFICATIONS)
            advanceUntilIdle()

            val consent = dao.findAccountConsent(userId, PermissionSource.OWN_NOTIFICATIONS.name)
            assertNotNull(consent)
            assertEquals("GRANTED", consent?.consentState)

            viewModel.revokeConsent(PermissionSource.OWN_NOTIFICATIONS)
            advanceUntilIdle()

            val revokedConsent = dao.findAccountConsent(userId, PermissionSource.OWN_NOTIFICATIONS.name)
            assertEquals("REVOKED", revokedConsent?.consentState)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
