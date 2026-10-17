package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MovementThemeRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openFilterSheetUpdatesItsSurfacesAndPreservesDraftAcrossThemeChanges() {
        val dark = mutableStateOf(false)
        var applied: MovementFilterDraft? = null
        compose.setContent {
            KipuTheme(darkTheme = dark.value) {
                MovementFiltersSheet(MovementHistoryUiState(ownerId = "theme-test"), {}, {
                    applied = it
                    it.validate()
                })
            }
        }
        compose.onNodeWithText("Este mes").performScrollTo().performClick()
        val lightImage = compose.onNodeWithTag("filter_period_card").captureToImage().toPixelMap()
        assertTrue(lightImage[lightImage.width / 2, 4].luminance() > 0.75f)
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        val darkImage = compose.onNodeWithTag("filter_period_card").captureToImage().toPixelMap()
        assertTrue(darkImage[darkImage.width / 2, 4].luminance() < 0.15f)
        compose.runOnIdle { dark.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.runOnIdle {
            assertNotNull(applied)
            assertEquals(LocalDate.now().withDayOfMonth(1).toString(), applied?.fromDate)
            assertEquals(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString(), applied?.toDate)
        }
    }
}
