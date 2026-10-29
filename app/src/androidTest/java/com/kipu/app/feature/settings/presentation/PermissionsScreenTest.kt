package com.kipu.app.feature.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import java.util.UUID
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val userId = UUID.randomUUID()

    private fun viewModel(): PermissionsViewModel {
        val categories = mockk<com.kipu.app.feature.categories.domain.CategoriesRepository>()
        every { categories.observePremiumVerified(com.kipu.app.core.finance.domain.model.UserId(userId.toString())) } returns flowOf(false)
        val gate = object : com.kipu.app.feature.capture.domain.CaptureAccessGate {
            override fun currentOwnerId() = userId.toString()
            override suspend fun evaluateCurrent() = com.kipu.app.feature.capture.domain.CaptureAccessDecision.PremiumRequired
            override suspend fun evaluate(ownerId: String) = com.kipu.app.feature.capture.domain.CaptureAccessDecision.PremiumRequired
        }
        return PermissionsViewModel(FakePermissionDao(), FakeGateway(), FakeSessionCoordinator(LocalOwner(userId.toString(), false)), gate, categories)
    }

    private class FakePermissionDao : PermissionConsentDao {
        val consents = MutableStateFlow<List<AccountSourceConsentEntity>>(emptyList())
        override suspend fun findInstallationPermission(source: String): InstallationPermissionStateEntity? = null
        override fun observeInstallationPermissions(): Flow<List<InstallationPermissionStateEntity>> = MutableStateFlow(emptyList())
        override suspend fun putInstallationPermission(entity: InstallationPermissionStateEntity) {}
        override suspend fun findAccountConsent(userId: UUID, source: String): AccountSourceConsentEntity? = null
        override fun observeAccountConsents(userId: UUID): Flow<List<AccountSourceConsentEntity>> = consents
        override suspend fun putAccountConsent(entity: AccountSourceConsentEntity) {}
        override suspend fun revokeAllAccountConsents(userId: UUID) {}
    }

    private class FakeGateway : PermissionSourceGateway {
        override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization = DeviceAuthorization.GRANTED
        override fun createSettingsIntent(source: PermissionSource): android.content.Intent = android.content.Intent()
        override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> =
            mapOf(PermissionSource.OWN_NOTIFICATIONS to DeviceAuthorization.GRANTED)
    }

    private class FakeSessionCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow<com.kipu.app.core.session.LocalAccess>(
            com.kipu.app.core.session.LocalAccess.Available(currentOwner!!.verifiedUserId, com.kipu.app.core.session.RemoteSession.Absent)
        )
        override suspend fun setActiveOwner(userId: String) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override suspend fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
    }

    @Test
    fun screen_displays_guarantee_banner_and_permission_cards() {
        val viewModel = viewModel()

        composeTestRule.setContent {
            PermissionsScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onOpenSettingsIntent = {},
            )
        }

        composeTestRule.onNodeWithText("Permisos y Automatización").assertIsDisplayed()
        composeTestRule.onNodeWithText("Puedes registrar movimientos manualmente sin estos permisos.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notificaciones de Kipu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lectura de Notificaciones Bancarias").assertIsDisplayed()
    }

    @Test
    fun clicking_explanation_opens_rationale_dialog() {
        val viewModel = viewModel()

        composeTestRule.setContent {
            PermissionsScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onOpenSettingsIntent = {},
            )
        }

        val explanationButtons = composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Explicación"))
        explanationButtons[0].performClick()

        composeTestRule.onNodeWithText("Aceptar y Continuar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar").assertIsDisplayed()
    }
}
