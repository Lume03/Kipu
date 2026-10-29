package com.kipu.app.feature.auth.presentation

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Renders the two sign-in celebration screens in the isolated lab variant; nothing here reaches a remote service. */
class AuthCelebrationScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test fun accountCreatedShowsTheAddressAndContinuesOnce() {
        var continued = 0
        rule.setContent {
            KipuTheme(darkTheme = false) { AccountCreatedScreen("ana@correo.com", { continued++ }) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("¡Cuenta creada!").assertIsDisplayed()
        rule.onNodeWithText("Te damos la bienvenida a Kipu.").assertIsDisplayed()
        rule.onNodeWithText("ana@correo.com").assertIsDisplayed()
        rule.onNodeWithText("Siguiente paso: elegir tu plan").assertIsDisplayed()
        rule.onNodeWithTag("auth-created-continue").assertIsEnabled().performClick()
        rule.onNodeWithTag("auth-created-continue").performClick()
        rule.runOnIdle { assertEquals(1, continued) }
    }

    @Test fun welcomeCurtainWaitsForTheAppAndThenClosesOnce() {
        val ready = mutableStateOf(false)
        var finished = 0
        rule.setContent {
            KipuTheme(darkTheme = false) { WelcomeBackCurtain("ana@correo.com", ready.value, { finished++ }) }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithText("¡Hola de nuevo!").assertIsDisplayed()
        rule.onNodeWithText("Preparando tu resumen").assertIsDisplayed()
        rule.onNodeWithTag("auth-welcome-logo", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { assertEquals("the curtain keeps waiting while the app loads", 0, finished) }
        rule.runOnIdle { ready.value = true }
        rule.mainClock.advanceTimeBy(3_000)
        rule.runOnIdle { assertEquals(1, finished) }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "auth-evidence/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Frames of both animations at fixed instants, kept as evidence next to the other auth captures. */
    @Test fun capturesTheCelebrationFramesAtFixedInstants() {
        rule.mainClock.autoAdvance = false
        rule.setContent { KipuTheme(darkTheme = false) { AccountCreatedScreen("ana@correo.com", {}) } }
        var now = 0L
        for ((name, at) in listOf("created-1-weaving" to 2_200L, "created-2-gathering" to 3_600L,
            "created-3-seal" to 4_300L, "created-4-final" to 6_500L)) {
            rule.mainClock.advanceTimeBy(at - now)
            now = at
            capture(name)
        }
    }

    @Test fun capturesTheWelcomeCurtainAtFixedInstants() {
        val ready = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent { KipuTheme(darkTheme = false) { WelcomeBackCurtain("ana@correo.com", ready.value, {}) } }
        rule.mainClock.advanceTimeBy(1_300)
        capture("welcome-1-entering")
        rule.mainClock.advanceTimeBy(1_700)
        capture("welcome-2-waiting")
        rule.runOnIdle { ready.value = true }
        rule.mainClock.advanceTimeBy(450)
        capture("welcome-3-gathering")
        rule.mainClock.advanceTimeBy(500)
        capture("welcome-4-check")
        rule.mainClock.advanceTimeBy(250)
        capture("welcome-5-iris")
    }

    @Test fun welcomeCurtainShowsItsClosingLineOnceReady() {
        val ready = mutableStateOf(false)
        rule.setContent {
            KipuTheme(darkTheme = false) { WelcomeBackCurtain("ana@correo.com", ready.value, {}) }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { ready.value = true }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText("¡Todo listo!").assertIsDisplayed()
    }
}
