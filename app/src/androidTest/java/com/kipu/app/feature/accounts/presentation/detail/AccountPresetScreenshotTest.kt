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
        capture(type = AccountType.CASH, preset = AccountPreset.CASH, dark = false, name = "cash-detail-light")
    }

    @Test
    fun capturesCashPresentationInDarkTheme() {
        capture(type = AccountType.CASH, preset = AccountPreset.CASH, dark = true, name = "cash-detail-dark")
    }

    @Test
    fun capturesSavingsPresentationInLightTheme() {
        capture(type = AccountType.SAVINGS, preset = AccountPreset.BCP, dark = false, name = "savings-detail-light")
    }

    @Test
    fun capturesSavingsPresentationInDarkTheme() {
        capture(type = AccountType.SAVINGS, preset = AccountPreset.BCP, dark = true, name = "savings-detail-dark")
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

    @Test
    fun capturesYapeDetailInLightTheme() {
        capture(AccountType.DIGITAL_WALLET, AccountPreset.YAPE, false, "yape-detail-light")
    }

    @Test
    fun capturesYapeDetailInDarkTheme() {
        capture(AccountType.DIGITAL_WALLET, AccountPreset.YAPE, true, "yape-detail-dark")
    }

    @Test
    fun capturesPlinDetailInLightTheme() {
        capture(AccountType.DIGITAL_WALLET, AccountPreset.PLIN, false, "plin-detail-light")
    }

    @Test
    fun capturesPlinDetailInDarkTheme() {
        capture(AccountType.DIGITAL_WALLET, AccountPreset.PLIN, true, "plin-detail-dark")
    }

    @Test
    fun capturesDebitDetailInLightTheme() {
        capture(AccountType.SAVINGS, AccountPreset.BCP, false, "debit-detail-light", debit = true)
    }

    @Test
    fun capturesDebitDetailInDarkTheme() {
        capture(AccountType.SAVINGS, AccountPreset.BCP, true, "debit-detail-dark", debit = true)
    }

    private fun capture(
        type: AccountType,
        preset: AccountPreset,
        dark: Boolean,
        name: String,
        openInstitutionMenu: Boolean = false,
        debit: Boolean = false,
    ) {
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                AccountDetailContent(
                    account = if (debit) null else account(type, preset),
                    card = if (debit) com.kipu.app.feature.accounts.domain.model.DebitCard(
                        id = com.kipu.app.core.finance.domain.model.CardId("e1000000-0000-4000-8000-000000000003"),
                        userId = account(type, preset).userId,
                        alias = "Débito BCP", issuer = "BCP",
                        network = com.kipu.app.feature.accounts.domain.model.CardNetwork.VISA,
                        lastFourDigits = "1234", currency = Currency.PEN,
                        linkedAccountId = account(type, preset).id, stylePresetId = "bcp-debit-classic",
                    ) else null,
                    debitAccount = if (debit) account(type, preset) else null,
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

        if (openInstitutionMenu) {
            compose.onNodeWithTag("edit_account").performClick()
            compose.onNodeWithTag("account_institution_selector").performScrollTo().performClick()
        }
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
