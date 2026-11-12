package com.kipu.app.core.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kipu.app.core.security.model.AuthenticatorType
import com.kipu.app.core.security.model.LocalAuthenticatorCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidXBiometricGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalAuthenticatorGateway {

    override fun getCapability(): LocalAuthenticatorCapability {
        val manager = BiometricManager.from(context)
        val hasBiometrics = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val hasCredential = if (Build.VERSION.SDK_INT < 30) {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
        } else {
            manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        }

        val types = mutableSetOf<AuthenticatorType>()
        if (hasBiometrics) types.add(AuthenticatorType.BIOMETRIC_STRONG)
        if (hasCredential) types.add(AuthenticatorType.DEVICE_CREDENTIAL)

        return LocalAuthenticatorCapability(
            hasBiometrics = hasBiometrics,
            hasDeviceCredential = hasCredential,
            canAuthenticate = hasBiometrics || hasCredential,
            supportedTypes = types,
        )
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (success: Boolean, error: String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Prompt remains open for retry on failed attempt
                }
            },
        )

        val capability = getCapability()
        if (!capability.canAuthenticate) {
            onResult(false, "No hay autenticación disponible")
            return
        }
        val legacyBiometric = Build.VERSION.SDK_INT < 30 && capability.hasBiometrics
        val authenticators = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else if (legacyBiometric) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
        if (legacyBiometric) builder.setNegativeButtonText("Cancelar")
        val promptInfo = builder.build()

        prompt.authenticate(promptInfo)
    }
}
