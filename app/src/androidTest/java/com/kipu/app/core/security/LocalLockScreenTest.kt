package com.kipu.app.core.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import com.kipu.app.core.security.model.LocalLockState
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import org.junit.Assert.assertEquals
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.security.model.LocalAuthenticatorCapability
import com.kipu.app.testing.TestFragmentActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalLockScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestFragmentActivity>()

    private class FakeGateway : LocalAuthenticatorGateway {
        var authenticateCalled = false
        var attempts = 0
        var result: ((Boolean, String?) -> Unit)? = null
        override fun getCapability(): LocalAuthenticatorCapability =
            LocalAuthenticatorCapability(hasBiometrics = true, hasDeviceCredential = true, canAuthenticate = true)

        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            onResult: (Boolean, String?) -> Unit,
        ) {
            authenticateCalled = true
            attempts++
            result = onResult
        }
    }

    @Test
    fun cancellationAndFailureKeepBalancesHiddenUntilSuccessfulRetry() {
        val owner = UUID.randomUUID()
        val dao = mockk<DeviceAccountSettingsDao>()
        coEvery { dao.findSettings(owner) } returns DeviceAccountSettingsEntity(owner, localUnlockEnabled = true)
        val session = mockk<SessionCoordinator>(relaxed = true)
        every { session.localAccess } returns MutableStateFlow<LocalAccess>(LocalAccess.Available(owner.toString(), RemoteSession.Absent))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var lock: LocalLockCoordinator
        val gateway = FakeGateway()
        composeTestRule.runOnIdle { lock = LocalLockCoordinator(dao, session, { 0L }, scope) }
        try {
            composeTestRule.setContent {
                if (lock.lockState.collectAsState().value == LocalLockState.LOCKED) {
                    LockScreenOverlay(gateway, onUnlockSuccess = lock::unlock, onSignOutRequested = {})
                } else Text("Saldo: S/ 123.45")
            }
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle { gateway.result!!(false, "Autenticación cancelada") }
            composeTestRule.onNodeWithText("Autenticación cancelada").assertIsDisplayed()
            composeTestRule.onNodeWithText("Saldo: S/ 123.45").assertDoesNotExist()
            composeTestRule.runOnIdle { assertEquals(LocalLockState.LOCKED, lock.lockState.value) }

            composeTestRule.onNodeWithText("Desbloquear").performClick()
            composeTestRule.runOnIdle { gateway.result!!(false, "Huella no reconocida") }
            composeTestRule.onNodeWithText("Huella no reconocida").assertIsDisplayed()
            composeTestRule.onNodeWithText("Saldo: S/ 123.45").assertDoesNotExist()
            composeTestRule.runOnIdle { assertEquals(LocalLockState.LOCKED, lock.lockState.value) }

            composeTestRule.onNodeWithText("Desbloquear").performClick()
            composeTestRule.runOnIdle { gateway.result!!(true, null) }
            composeTestRule.onNodeWithText("Saldo: S/ 123.45").assertIsDisplayed()
            composeTestRule.runOnIdle {
                assertEquals(3, gateway.attempts)
                assertEquals(LocalLockState.UNLOCKED, lock.lockState.value)
            }
        } finally { scope.cancel() }
    }

    @Test
    fun lockScreenOverlay_displays_locked_state_and_triggers_prompt() {
        val gateway = FakeGateway()
        var unlockSuccessCalled = false
        var signOutRequested = false

        composeTestRule.setContent {
            LockScreenOverlay(
                gateway = gateway,
                onUnlockSuccess = { unlockSuccessCalled = true },
                onSignOutRequested = { signOutRequested = true },
            )
        }

        composeTestRule.onNodeWithText("Kipu está protegido").assertIsDisplayed()
        composeTestRule.onNodeWithText("Desbloquear").assertIsDisplayed()

        composeTestRule.onNodeWithText("Desbloquear").performClick()
        assertTrue(gateway.authenticateCalled)

        composeTestRule.onNodeWithText("Cerrar sesión").performClick()
        assertTrue(signOutRequested)
    }
}
