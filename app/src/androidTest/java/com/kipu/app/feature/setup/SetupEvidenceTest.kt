package com.kipu.app.feature.setup

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.auth.domain.model.OtpFailure
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Draws each setup step with synthetic data in the isolated lab host and saves screenshots; never touches the user's package. */
@RunWith(Parameterized::class)
class SetupEvidenceTest(private val mode: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dark get() = mode == "dark"
    private val fontScale get() = if (mode == "large") 1.4f else 1f
    private val catalog = InitialCategoryCatalog.entries
    private val bank = SetupAccountDraft(SetupAccountKind.BANK, AccountPreset.BCP, SetupBankSubtype.SAVINGS, "Cuenta BCP", "1250.00", Currency.PEN)
    private val draft = SetupDraft("ana@correo.com", bank, setOf(catalog[2].id), CommercialOption.ANNUAL)

    @Before fun prepareLabHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("com.kipu.app.lab", instrumentation.targetContext.packageName)
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true)
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Composable private fun Themed(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { KipuTheme(darkTheme = dark, content = content) }
    }

    @Test fun steps() {
        val screen = mutableStateOf<@Composable () -> Unit>({})
        compose.setContent { Themed { screen.value() } }
        fun show(name: String, content: @Composable () -> Unit) {
            compose.runOnIdle { screen.value = content }
            capture(name)
        }
        show("1-tipo") { SetupAccountTypeContent({}, {}) }
        show("2-cuenta-vacia") { SetupAccountFormContent(SetupAccountDraft(SetupAccountKind.BANK, AccountPreset.BCP, alias = "BCP"), false, {}, {}, {}, {}, {}, {}, {}) }
        show("3-cuenta-llena") { SetupAccountFormContent(bank, false, {}, {}, {}, {}, {}, {}, {}) }
        show("4-cuenta-errores") { SetupAccountFormContent(bank.copy(alias = "", balanceText = "abc"), true, {}, {}, {}, {}, {}, {}, {}) }
        show("5-billetera") { SetupAccountFormContent(SetupAccountDraft(SetupAccountKind.WALLET, AccountPreset.YAPE, alias = "Yape"), false, {}, {}, {}, {}, {}, {}, {}) }
        show("6-categorias") { SetupCategoriesContent(catalog, emptySet(), false, {}, {}, {}, {}) }
        show("7-categorias-ocultas") { SetupCategoriesContent(catalog, setOf(catalog[2].id), false, {}, {}, {}, {}) }
        show("8-plan") { SetupPlanContent(CommercialOption.ANNUAL, {}, {}, {}) }
        show("9-plan-free") { SetupPlanContent(CommercialOption.FREE, {}, {}, {}) }
        show("10-resumen") { SetupSummaryContent(draft, catalog, SetupSaveState(), {}, {}, {}, {}, {}, {}) }
        show("11-resumen-creando") { SetupSummaryContent(draft, catalog, SetupSaveState(loading = true), {}, {}, {}, {}, {}, {}) }
        show("12-resumen-error") { SetupSummaryContent(draft, catalog, SetupSaveState(error = "No pudimos crear tu cuenta. Revisa tu conexión."), {}, {}, {}, {}, {}, {}) }
        val email = "ana@correo.com"
        show("13-otp") { SetupOtpContent(email, SetupOtpState(resendSeconds = 45), {}, {}, {}, {}) }
        show("14-otp-escribiendo") { SetupOtpContent(email, SetupOtpState(code = "482", resendSeconds = 38), {}, {}, {}, {}) }
        show("15-otp-error") { SetupOtpContent(email, SetupOtpState(failure = OtpFailure.INVALID, attemptsLeft = 3, resendSeconds = 20), {}, {}, {}, {}) }
        show("16-otp-vencido") { SetupOtpContent(email, SetupOtpState(failure = OtpFailure.EXPIRED), {}, {}, {}, {}) }
        show("17-otp-verificando") { SetupOtpContent(email, SetupOtpState(code = "482901", verifying = true), {}, {}, {}, {}) }
        show("18-otp-verificado") { SetupOtpContent(email, SetupOtpState(code = "482901", verified = true), {}, {}, {}, {}) }
    }

    private fun capture(name: String) {
        if (compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(700)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.filesDir, "setup-evidence").apply { mkdirs() }
        File(directory, "$mode-$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf("light"), arrayOf("dark"), arrayOf("large"))
    }
}
