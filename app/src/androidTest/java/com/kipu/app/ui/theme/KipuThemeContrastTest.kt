package com.kipu.app.ui.theme

import android.content.res.Configuration
import android.view.Window
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.kipu.app.ui.component.KipuBottomSheet
import androidx.core.view.WindowCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kipu.app.feature.accounts.presentation.instruments.bankChoices
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KipuThemeContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun effectiveAppThemeWinsOverSystemAndUsedColorPairsRemainReadable() {
        val appDark = mutableStateOf(false)
        val systemDark = mutableStateOf(true)
        var palette: KipuUiColors? = null
        var financial: CalmEmeraldColors? = null
        val report = JSONArray()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                KipuTheme(darkTheme = appDark.value) {
                    palette = rememberKipuColors()
                    financial = rememberCalmEmeraldColors()
                    Text("Tema efectivo", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { appDark.value = dark; systemDark.value = !dark }
            compose.waitForIdle()
            compose.runOnIdle {
                val colors = requireNotNull(palette)
                val semantic = requireNotNull(financial)
                assertEquals(dark, colors.isDark)
                assertEquals(dark, semantic.isDark)
                val window = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).single().window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(!dark, controller.isAppearanceLightStatusBars)
                assertEquals(!dark, controller.isAppearanceLightNavigationBars)
                val pairs = listOf(
                    Triple("surface text", colors.inkPrimary, colors.surface),
                    Triple("secondary text", colors.inkSecondary, colors.surface),
                    Triple("container text", colors.inkSecondary, colors.surfaceContainer),
                    Triple("primary action", colors.onPrimary, colors.primary),
                    Triple("selected chip", colors.onPrimaryContainer, colors.primaryContainer),
                    Triple("selected reference", colors.primaryText, colors.selectedSurface),
                    Triple("positive badge", colors.onPositiveContainer, colors.positiveContainer),
                    Triple("income", semantic.incomeText, semantic.incomeBg),
                    Triple("expense", semantic.expenseText, semantic.expenseBg),
                    Triple("transfer", semantic.transferBlue, semantic.transferBg),
                    Triple("warning", semantic.warningText, semantic.warningBg),
                    Triple("income on card", semantic.incomeText, colors.surface),
                    Triple("expense on card", semantic.expenseText, colors.surface),
                    Triple("warning on card", semantic.warningText, colors.surface),
                )
                for ((name, foreground, background) in pairs) {
                    val ratio = colorContrastRatio(foreground, background)
                    report.put(JSONObject().put("name", name).put("dark", dark)
                        .put("foreground", String.format("#%08X", foreground.toArgb()))
                        .put("background", String.format("#%08X", background.toArgb()))
                        .put("ratio", ratio.toDouble()))
                    assertTrue("$name dark=$dark contrast=$ratio", ratio >= 4.5f)
                }
                assertTrue("field outline dark=$dark", colorContrastRatio(colors.outline, colors.surface) >= 3f)
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-39").apply { mkdirs() }
        File(directory, "contrast.json").writeText(report.toString(2))
    }

    @Test fun customCategoryAndBrandSurfacesGetReadableForegrounds() {
        bankChoices.forEach { bank ->
            assertTrue("Bank label ${bank.code}", colorContrastRatio(bank.textColor, bank.color) >= 4.5f)
        }
        val surfaces = listOf(Color.White, Color.Black, Color(0xFF00D1D2), Color(0xFF009940)) +
            (0 until 360 step 15).flatMap { hue ->
                listOf(0.3f, 0.5f, 0.8f).map { lightness -> Color.hsl(hue.toFloat(), 0.65f, lightness) }
            }
        surfaces.forEach { background ->
            assertTrue("Custom foreground on $background", colorContrastRatio(readableContentColor(background), background) >= 4.5f)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun modalWindowUsesEffectiveAppThemeForItsNativeBars() {
        val appDark = mutableStateOf(false)
        var modalWindow: Window? = null
        compose.setContent {
            KipuTheme(darkTheme = appDark.value) {
                KipuBottomSheet(onDismissRequest = {}) {
                    modalWindow = (LocalView.current.parent as DialogWindowProvider).window
                    Text("Ventana de prueba")
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { appDark.value = dark }
            compose.waitForIdle()
            compose.runOnIdle {
                val window = requireNotNull(modalWindow)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(!dark, controller.isAppearanceLightStatusBars)
                assertEquals(!dark, controller.isAppearanceLightNavigationBars)
            }
        }
    }
}
