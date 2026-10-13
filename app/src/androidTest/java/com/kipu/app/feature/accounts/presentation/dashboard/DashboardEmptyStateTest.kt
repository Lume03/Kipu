package com.kipu.app.feature.accounts.presentation.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardEmptyStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstAccountActionNavigatesToAccountCreation() {
        var navigatedToAccountCreation = false
        setEmptyDashboard(onNavigateToNewAccount = { navigatedToAccountCreation = true })

        compose.onNodeWithText("+ Agregar primera cuenta").assertIsDisplayed().performClick()

        compose.runOnIdle { assertTrue(navigatedToAccountCreation) }
    }

    @Test
    fun creditCardCreationRemainsAvailableFromTheEmptyState() {
        var navigatedToCardCreation = false
        setEmptyDashboard(onNavigateToNewCard = { navigatedToCardCreation = true })

        compose.onNodeWithText("+ Vincular").performClick()

        compose.runOnIdle { assertTrue(navigatedToCardCreation) }
    }

    @Test
    fun capturesCurrentEmptyDashboardLightTheme() = captureBefore(dark = false, name = "dashboard-empty-before-light")

    @Test
    fun capturesCurrentEmptyDashboardDarkTheme() = captureBefore(dark = true, name = "dashboard-empty-before-dark")

    private fun setEmptyDashboard(
        onNavigateToNewAccount: () -> Unit = {},
        onNavigateToNewCard: () -> Unit = {},
        dark: Boolean = false,
    ) {
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EmptyDashboardContent(
                        quotaCount = 0,
                        maxQuota = 4,
                        onNavigateToNewAccount = onNavigateToNewAccount,
                        onNavigateToNewCard = onNavigateToNewCard,
                        onNavigateToPlans = {},
                    )
                }
            }
        }
    }

    private fun captureBefore(dark: Boolean, name: String) {
        setEmptyDashboard(dark = dark)
        compose.waitForIdle()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "dashboard-empty-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
