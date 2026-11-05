package com.kipu.app.core.security

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AndroidXBiometricGatewayTest {
    @Test fun credentialOnlyDeviceOffersPinInThePlatformPrompt() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        val biometric = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = mockk<BiometricManager>()
        mockkStatic(BiometricManager::class)
        mockkConstructor(BiometricPrompt::class)
        try {
            every { BiometricManager.from(any()) } returns manager
            every { manager.canAuthenticate(biometric) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            every { manager.canAuthenticate(credential) } returns BiometricManager.BIOMETRIC_SUCCESS
            every { manager.canAuthenticate(biometric or credential) } returns BiometricManager.BIOMETRIC_SUCCESS
            every { anyConstructed<BiometricPrompt>().authenticate(any<BiometricPrompt.PromptInfo>()) } just Runs
            val gateway = AndroidXBiometricGateway(activity)
            val capability = gateway.getCapability()
            assertFalse(capability.hasBiometrics)
            assertTrue(capability.hasDeviceCredential)
            assertTrue(capability.canAuthenticate)
            gateway.authenticate(activity, "Kipu", "Confirma tu identidad") { _, _ -> }
            verify(exactly = 1) {
                anyConstructed<BiometricPrompt>().authenticate(match<BiometricPrompt.PromptInfo> {
                    it.allowedAuthenticators and credential != 0
                })
            }
        } finally {
            unmockkConstructor(BiometricPrompt::class)
            unmockkStatic(BiometricManager::class)
            controller.pause().stop().destroy()
        }
    }
}
