package com.kipu.app.feature.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import com.kipu.app.ui.theme.KipuTheme
import java.util.UUID
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val userId = UUID.randomUUID()

    private fun viewModel(
        dao: FakePermissionDao = FakePermissionDao(),
        gateway: FakeGateway = FakeGateway(),
        premium: Boolean = false,
    ): PermissionsViewModel {
        val categories = mockk<com.kipu.app.feature.categories.domain.CategoriesRepository>()
        every { categories.observePremiumVerified(com.kipu.app.core.finance.domain.model.UserId(userId.toString())) } returns flowOf(premium)
        val gate = object : com.kipu.app.feature.capture.domain.CaptureAccessGate {
            override fun currentOwnerId() = userId.toString()
            override suspend fun evaluateCurrent() = evaluate(userId.toString())
            override suspend fun evaluate(ownerId: String): com.kipu.app.feature.capture.domain.CaptureAccessDecision =
                if (premium) com.kipu.app.feature.capture.domain.CaptureAccessDecision.Allowed(
                    com.kipu.app.feature.plans.domain.model.EffectiveEntitlement(true, null),
                ) else com.kipu.app.feature.capture.domain.CaptureAccessDecision.PremiumRequired
        }
        return PermissionsViewModel(dao, gateway, FakeSessionCoordinator(LocalOwner(userId.toString(), false)), gate, categories)
    }

    private class FakePermissionDao : PermissionConsentDao {
        val consents = MutableStateFlow<List<AccountSourceConsentEntity>>(emptyList())
        override suspend fun findInstallationPermission(source: String): InstallationPermissionStateEntity? = null
        override fun observeInstallationPermissions(): Flow<List<InstallationPermissionStateEntity>> = MutableStateFlow(emptyList())
        override suspend fun putInstallationPermission(entity: InstallationPermissionStateEntity) {}
        override suspend fun findAccountConsent(userId: UUID, source: String): AccountSourceConsentEntity? =
            consents.value.find { it.userId == userId && it.source == source }
        override fun observeAccountConsents(userId: UUID): Flow<List<AccountSourceConsentEntity>> = consents
        override suspend fun putAccountConsent(entity: AccountSourceConsentEntity) {
            consents.value = consents.value.filterNot { it.userId == entity.userId && it.source == entity.source } + entity
        }
        override suspend fun revokeAllAccountConsents(userId: UUID) {}
    }

    private class FakeGateway : PermissionSourceGateway {
        var listenerAuth = DeviceAuthorization.GRANTED
        override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization = when (source) {
            PermissionSource.OWN_NOTIFICATIONS -> DeviceAuthorization.GRANTED
            PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> listenerAuth
            PermissionSource.YAPE_RECEIPT_OCR -> DeviceAuthorization.NOT_APPLICABLE
        }
        override fun createSettingsIntent(source: PermissionSource): android.content.Intent = android.content.Intent()
        override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> =
            PermissionSource.entries.associateWith { checkDeviceAuthorization(it) }
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
        composeTestRule.onNodeWithText("Notificaciones de Kipu").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Lectura de Notificaciones Bancarias").performScrollTo().assertIsDisplayed()
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

    @Test
    fun local_ocr_can_be_revoked_on_Free_without_Android_settings() {
        val source = PermissionSource.YAPE_RECEIPT_OCR
        val dao = FakePermissionDao()
        val opened = mutableListOf<PermissionSource>()
        lateinit var model: PermissionsViewModel
        composeTestRule.runOnIdle { model = viewModel(dao, premium = false) }
        composeTestRule.setContent {
            KipuTheme { PermissionsScreen(model, onNavigateBack = {}, onOpenSettingsIntent = { opened += it }) }
        }
        composeTestRule.runOnIdle { model.grantConsent(source) }
        composeTestRule.waitUntil { model.uiState.value.items.any { it.source == source && it.isProcessingAuthorized } }
        val card = hasContentDescription("OCR local de comprobantes Yape. Estado: Activo")
        composeTestRule.onNode(card).performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Abrir ajustes del sistema") and hasAnyAncestor(card)).assertDoesNotExist()
        composeTestRule.onNode(hasText("Revocar") and hasAnyAncestor(card)).performScrollTo().performClick()
        composeTestRule.waitUntil { dao.consents.value.any { it.source == source.name && it.consentState == "REVOKED" } }
        composeTestRule.runOnIdle { assertEquals(emptyList<PermissionSource>(), opened) }
    }

    @Test
    fun listener_consent_opens_settings_once_and_refresh_shows_real_authorization() {
        val source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT
        val dao = FakePermissionDao()
        val gateway = FakeGateway().apply { listenerAuth = DeviceAuthorization.DENIED }
        val opened = mutableListOf<PermissionSource>()
        lateinit var model: PermissionsViewModel
        composeTestRule.runOnIdle { model = viewModel(dao, gateway, premium = true) }
        composeTestRule.setContent {
            KipuTheme {
                PermissionsScreen(model, onNavigateBack = {}, onOpenSettingsIntent = {
                    assertEquals("GRANTED", dao.consents.value.first { consent -> consent.source == source.name }.consentState)
                    opened += it
                })
            }
        }
        composeTestRule.runOnIdle { model.showRationale(source) }
        composeTestRule.onNodeWithTag("capture_consent_check").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("capture_consent_confirm").assertIsEnabled().performClick()
        composeTestRule.waitUntil { opened.size == 1 }
        composeTestRule.onNodeWithText("Falta activar en Android").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(listOf(source), opened)
            assertEquals(DeviceAuthorization.DENIED, gateway.listenerAuth)
            model.refresh()
        }
        composeTestRule.onNodeWithTag("permission_open_settings_${source.name}").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(source, source), opened)
            gateway.listenerAuth = DeviceAuthorization.GRANTED
            model.refresh()
        }
        composeTestRule.waitUntil { model.uiState.value.items.any { it.source == source && it.isProcessingAuthorized } }
        composeTestRule.onNodeWithContentDescription("Lectura de Notificaciones Bancarias. Estado: Activo")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Falta activar en Android").assertDoesNotExist()
    }
}
