package com.kipu.app.feature.capture.presentation

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureConsentSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrow_sheet_with_large_text_keeps_both_decisions_reachable() {
        var consentCalls = 0
        var dismissCalls = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KipuTheme {
                    CaptureConsentSheet(
                        requiresPremium = true,
                        onConsent = { consentCalls++ },
                        onDismiss = { dismissCalls++ },
                        modifier = Modifier.width(320.dp).heightIn(max = 560.dp),
                    )
                }
            }
        }
        // Actions stay in the fixed footer while the explanation and acceptance row scroll.
        compose.onNodeWithTag("capture_consent_confirm").assertExists().assertHasClickAction()
            .assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("capture_consent_decline").assertExists().assertHasClickAction()
            .assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Google Wallet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capture_consent_check").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("capture_consent_confirm").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("capture_consent_decline").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, consentCalls)
            assertEquals(1, dismissCalls)
        }
    }
}
