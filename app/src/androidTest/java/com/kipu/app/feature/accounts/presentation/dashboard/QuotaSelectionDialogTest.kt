package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class QuotaSelectionDialogTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var originalConfiguration: android.content.res.Configuration

    @org.junit.After
    @Suppress("DEPRECATION")
    fun restoreActivityFontConfiguration() {
        if (::originalConfiguration.isInitialized) compose.runOnUiThread {
            val resources = compose.activity.resources
            resources.updateConfiguration(originalConfiguration, resources.displayMetrics)
        }
    }

    @Test fun longQuotaSelectionScrollsAndAnnouncesDisabledItemsAt200Percent() {
        val accounts = (1..5).map {
            Account(id = AccountId.generate(), userId = UserId.generate(), alias = "QA95_BANK$it",
                type = AccountType.BANK, currency = Currency.PEN, initialBalance = Money(0, Currency.PEN), openedAt = java.time.Instant.EPOCH)
        }
        var dismissals = 0
        var saves = 0
        // Dialog owns a separate window; its View context must also carry the test font scale.
        compose.runOnUiThread {
            val resources = compose.activity.resources
            originalConfiguration = android.content.res.Configuration(resources.configuration)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(android.content.res.Configuration(originalConfiguration).apply { fontScale = 2f }, resources.displayMetrics)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                KipuTheme {
                    InstrumentQuotaSelectionDialog(accounts, emptyList(), accounts.take(4).map { it.id.value }.toSet(), 4,
                        onDismiss = { dismissals++ }, onSave = { saves++ })
                }
            }
        }
        val title = compose.onNodeWithText("Instrumentos disponibles").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Dialog must actually render with the 200% font scale", title.bottom - title.top >= 40.dp)
        compose.onNodeWithContentDescription("QA95_BANK5").performScrollTo().assertIsNotEnabled()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "qa95-quota-200.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithContentDescription("QA95_BANK1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("QA95_BANK5").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, saves) }
    }
}
