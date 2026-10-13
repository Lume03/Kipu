package com.kipu.app.feature.accounts.presentation.dashboard

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class DashboardFinancialMessagingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardWithoutCardsAndWithZeroDebtDoesNotMakeUnverifiedFinancialClaims() {
        compose.setContent {
            KipuTheme {
                ActiveDashboardContent(
                    data = FinancialDashboardData(
                        totalPen = Money(0, Currency.PEN),
                        totalUsd = Money(0, Currency.USD),
                        liquidAccounts = emptyList(),
                        creditCards = emptyList(),
                        activeComputableCount = 0,
                    ),
                    instruments = InstrumentsUiState(isLoading = false),
                    creditNotifications = emptyList(),
                    isMasked = false,
                    isArchivedExpanded = false,
                    onToggleArchived = {},
                    onToggleMasked = {},
                    onAccountClick = {},
                    onCardClick = {},
                    onManageQuota = {},
                    onNavigateToPlans = {},
                    onNavigateToNewAccount = {},
                    onNavigateToNewCard = {},
                )
            }
        }

        compose.onNodeWithText("Tus cuentas están sincronizadas.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("rango saludable", substring = true).assertDoesNotExist()
        compose.onNodeWithText("inferior al 30%", substring = true).assertDoesNotExist()
    }

    @Test
    fun capturesDashboardAfterBannerRemovalInLightTheme() = captureDashboard(darkTheme = false)

    @Test
    fun capturesDashboardAfterBannerRemovalInDarkTheme() = captureDashboard(darkTheme = true)

    private fun captureDashboard(darkTheme: Boolean) {
        compose.setContent {
            KipuTheme(darkTheme = darkTheme) {
                ActiveDashboardContent(
                    data = FinancialDashboardData(
                        totalPen = Money(0, Currency.PEN),
                        totalUsd = Money(0, Currency.USD),
                        liquidAccounts = emptyList(),
                        creditCards = emptyList(),
                        activeComputableCount = 0,
                    ),
                    instruments = InstrumentsUiState(isLoading = false),
                    creditNotifications = emptyList(),
                    isMasked = false,
                    isArchivedExpanded = false,
                    onToggleArchived = {}, onToggleMasked = {}, onAccountClick = {},
                    onCardClick = {}, onManageQuota = {}, onNavigateToPlans = {},
                    onNavigateToNewAccount = {}, onNavigateToNewCard = {},
                    modifier = Modifier,
                )
            }
        }
        compose.onNodeWithTag("dashboard_bottom_space").performScrollTo()
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-47-dashboard-evidence").apply { mkdirs() }
        val suffix = if (darkTheme) "dark" else "light"
        val screenshot = File(directory, "dashboard-after-$suffix.png")
        screenshot.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "dashboard-after-$suffix.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/issue-47")
        }
        val imageUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create dashboard screenshot in MediaStore")
        context.contentResolver.openOutputStream(imageUri)?.use { screenshot.inputStream().copyTo(it) }
            ?: error("Could not write dashboard screenshot to MediaStore")
    }
}
