package com.kipu.app.feature.accounts.presentation.detail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class AccountPresetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun capturesCashPresentationInLightTheme() {
        capture(type = AccountType.CASH, preset = AccountPreset.BCP, dark = false, name = "cash-detail-light")
    }

    @Test
    fun capturesCashPresentationInDarkTheme() {
        capture(type = AccountType.CASH, preset = AccountPreset.BCP, dark = true, name = "cash-detail-dark")
    }

    @Test
    fun capturesSavingsPresentationInLightTheme() {
        capture(type = AccountType.SAVINGS, preset = AccountPreset.YAPE, dark = false, name = "savings-detail-light")
    }

    @Test
    fun capturesSavingsPresentationInDarkTheme() {
        capture(type = AccountType.SAVINGS, preset = AccountPreset.YAPE, dark = true, name = "savings-detail-dark")
    }

    @Test
    fun capturesSavingsInstitutionOptionsInLightTheme() {
        capture(
            type = AccountType.SAVINGS,
            preset = AccountPreset.YAPE,
            dark = false,
            name = "savings-options-light",
            openInstitutionMenu = true,
        )
    }

    @Test
    fun capturesSavingsInstitutionOptionsInDarkTheme() {
        capture(
            type = AccountType.SAVINGS,
            preset = AccountPreset.YAPE,
            dark = true,
            name = "savings-options-dark",
            openInstitutionMenu = true,
        )
    }

    private fun capture(
        type: AccountType,
        preset: AccountPreset,
        dark: Boolean,
        name: String,
        openInstitutionMenu: Boolean = false,
    ) {
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                AccountDetailContent(
                    account = account(type, preset),
                    card = null,
                    currentBalance = Money(25_000L, Currency.PEN),
                    creditCardSummary = null,
                    isLoading = false,
                    onNavigateBack = {},
                    onSaveAppearance = { _, _ -> },
                    onCorrectOpeningBalance = {},
                    onArchive = {},
                    onReactivate = {},
                )
            }
        }

        val targetTag = if (type == AccountType.CASH) "cash_presentation" else "account_institution_selector"
        compose.onNodeWithTag(targetTag).performScrollTo()
        if (openInstitutionMenu) compose.onNodeWithTag(targetTag).performClick()
        compose.waitForIdle()

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "account-preset-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun account(type: AccountType, preset: AccountPreset) = Account(
        id = AccountId("e1000000-0000-4000-8000-000000000001"),
        userId = UserId("e1000000-0000-4000-8000-000000000002"),
        alias = if (type == AccountType.CASH) "Caja chica" else "Ahorro de viaje",
        type = type,
        currency = Currency.PEN,
        preset = preset,
        initialBalance = Money(25_000L, Currency.PEN),
        openedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )
}
