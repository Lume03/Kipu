package com.kipu.app.feature.plans.presentation

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

class PlanSelectionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun capturesUpdatedPlanSelectionLightTheme() {
        captureAfter(dark = false, suffix = "after-light")
    }

    @Test
    fun capturesUpdatedPlanSelectionDarkTheme() {
        captureAfter(dark = true, suffix = "after-dark")
    }

    private fun captureAfter(dark: Boolean, suffix: String) {
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                Surface(modifier = Modifier) {
                    PlanSelectionScreen(
                        state = PlanSelectionUiState(isLoadingEligibility = false),
                        onOptionSelected = {},
                        onConfirm = {},
                        onContinueFree = {},
                    )
                }
            }
        }

        capture("plan-selection-$suffix-top")
        compose.onNodeWithText("Continuar con Plan Free").performScrollTo()
        capture("plan-selection-$suffix-actions")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "plan-selection-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
