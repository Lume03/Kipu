package com.kipu.app.feature.accounts.presentation.dashboard

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.DashboardUiState
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardFinancialMessagingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardWithoutCardsAndWithZeroDebtDoesNotMakeUnverifiedFinancialClaims() {
        val viewModel = dashboardViewModel()
        compose.setContent {
            KipuTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToNewAccount = {},
                    onNavigateToNewCard = {},
                    modifier = Modifier.fillMaxSize().testTag(DASHBOARD_ROOT_TAG),
                )
            }
        }

        compose.onNodeWithTag(DASHBOARD_ROOT_TAG).assertExists()
        compose.onNodeWithText("Cuenta de evidencia").assertExists()
        compose.onNodeWithText("Tus cuentas est\u00e1n sincronizadas.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("rango saludable", substring = true).assertDoesNotExist()
        compose.onNodeWithText("inferior al 30%", substring = true).assertDoesNotExist()
    }

    @Test
    fun capturesProductionDashboardAfterBannerRemovalInLightTheme() = captureDashboard(darkTheme = false)

    @Test
    fun capturesProductionDashboardAfterBannerRemovalInDarkTheme() = captureDashboard(darkTheme = true)

    private fun captureDashboard(darkTheme: Boolean) {
        val viewModel = dashboardViewModel()
        val expectedBackgroundArgb = AtomicInteger()
        compose.setContent {
            KipuTheme(darkTheme = darkTheme) {
                val expectedBackground = rememberCalmEmeraldColors().background.toArgb()
                SideEffect { expectedBackgroundArgb.set(expectedBackground) }
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToNewAccount = {},
                    onNavigateToNewCard = {},
                    modifier = Modifier.fillMaxSize().testTag(DASHBOARD_ROOT_TAG),
                )
            }
        }

        compose.onNodeWithTag(DASHBOARD_ROOT_TAG).assertExists()
        compose.onNodeWithText("Cuenta de evidencia").assertExists()
        compose.onNodeWithTag("dashboard_bottom_space").performScrollTo()
        compose.waitForIdle()

        val dashboardRoot = compose.onNodeWithTag(DASHBOARD_ROOT_TAG)
        val bitmap = dashboardRoot.captureToImage().asAndroidBitmap()
        val bottomRightPixel = bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        assertEquals("Dashboard root must have an opaque themed surface", 255, AndroidColor.alpha(bottomRightPixel))
        assertEquals("Dashboard root corner must match the active Kipu theme", expectedBackgroundArgb.get(), bottomRightPixel)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-47-dashboard-evidence").apply { mkdirs() }
        val suffix = if (darkTheme) "dark" else "light"
        val screenshot = File(directory, "dashboard-after-$suffix.png")
        screenshot.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
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

    private fun dashboardViewModel(): AccountsViewModel {
        val account = Account(
            id = AccountId.generate(),
            userId = UserId.generate(),
            alias = "Cuenta de evidencia",
            type = AccountType.CASH,
            currency = Currency.PEN,
            preset = AccountPreset.CASH,
            initialBalance = Money.zero(Currency.PEN),
            openedAt = Instant.parse("2026-10-17T00:00:00Z"),
        )
        val balance = Money.zero(Currency.PEN)
        val dashboardData = FinancialDashboardData(
            totalPen = balance,
            totalUsd = Money.zero(Currency.USD),
            liquidAccounts = listOf(AccountWithBalance(account, balance)),
            creditCards = emptyList(),
            activeComputableCount = 0,
        )
        val viewModel = mockk<AccountsViewModel>(relaxed = true)

        every { viewModel.dashboardUiState } returns MutableStateFlow(
            DashboardUiState(isLoading = false, dashboardData = dashboardData),
        )
        every { viewModel.instrumentsUiState } returns MutableStateFlow(
            InstrumentsUiState(
                isLoading = false,
                activeAccounts = listOf(account),
                activeComputableCount = 0,
            ),
        )
        every { viewModel.creditNotifications } returns MutableStateFlow<List<CreditUtilizationNotification>>(emptyList())
        every { viewModel.isMasked } returns MutableStateFlow(false)

        return viewModel
    }

    private companion object {
        const val DASHBOARD_ROOT_TAG = "dashboard_production_root"
    }
}
