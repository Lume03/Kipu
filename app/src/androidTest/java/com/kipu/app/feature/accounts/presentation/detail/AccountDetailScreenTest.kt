package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savesAliasAndPresentationWithoutChangingOpeningBalance() {
        var savedAlias: String? = null
        var savedPreset: AccountPreset? = null
        var correctedAmount: Long? = null
        setScreen(
            account = account(),
            onSaveAppearance = { alias, preset ->
                savedAlias = alias
                savedPreset = preset
            },
            onCorrectOpeningBalance = { correctedAmount = it },
        )

        compose.onNodeWithTag("account_alias").performScrollTo().performTextReplacement("Ahorro de viaje")
        compose.onNodeWithTag("account_institution_selector").performScrollTo().performClick()
        compose.onNodeWithTag("account_preset_bbva").performClick()
        compose.onNodeWithTag("save_account_presentation").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("Ahorro de viaje", savedAlias)
            assertEquals(AccountPreset.BBVA, savedPreset)
            assertEquals(null, correctedAmount)
        }
    }

    @Test
    fun cashAccountDoesNotShowAnInstitutionSelectorAndNormalizesItsPreset() {
        var savedPreset: AccountPreset? = null
        setScreen(
            account().copy(type = AccountType.CASH, preset = AccountPreset.BCP),
            onSaveAppearance = { _, preset -> savedPreset = preset },
        )

        compose.onAllNodesWithTag("account_preset_selector").assertCountEquals(0)
        compose.onNodeWithTag("cash_presentation").assertIsDisplayed()
        compose.onNodeWithTag("account_alias").assertIsDisplayed()
        compose.onNodeWithTag("save_account_presentation").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(AccountPreset.CASH, savedPreset) }
    }

    @Test
    fun savingsAccountShowsOnlyBankPresets() {
        setScreen(account())

        compose.onNodeWithTag("account_institution_selector").performScrollTo().performClick()
        compose.onNodeWithTag("account_preset_bcp").assertIsDisplayed()
        compose.onNodeWithTag("account_preset_banco_nacion").assertIsDisplayed()
        compose.onAllNodesWithTag("account_preset_cash").assertCountEquals(0)
        compose.onAllNodesWithTag("account_preset_yape").assertCountEquals(0)
        compose.onAllNodesWithTag("account_preset_plin").assertCountEquals(0)
    }

    @Test
    fun legacySavingsWalletPresetDisplaysAndSavesAsOther() {
        var savedPreset: AccountPreset? = null
        setScreen(
            account().copy(preset = AccountPreset.YAPE),
            onSaveAppearance = { _, preset -> savedPreset = preset },
        )

        compose.onNodeWithTag("account_institution_selector").assertIsDisplayed()
        compose.onNodeWithText("Otro").assertIsDisplayed()
        compose.onNodeWithTag("save_account_presentation").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(AccountPreset.GENERIC, savedPreset) }
    }

    @Test
    fun openingBalanceChangesOnlyAfterExplicitAuditedCorrectionConfirmation() {
        var correctedAmount: Long? = null
        setScreen(account = account(), onCorrectOpeningBalance = { correctedAmount = it })

        compose.onNodeWithText("Corregir saldo inicial").performScrollTo().performClick()
        compose.onNodeWithText("Se conserva el movimiento de apertura original", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nuevo saldo inicial (PEN)").performTextReplacement("12.50")
        compose.runOnIdle { assertEquals(null, correctedAmount) }

        compose.onNodeWithText("Registrar corrección").performClick()

        compose.runOnIdle { assertEquals(1_250L, correctedAmount) }
    }

    @Test
    fun archiveRequiresConfirmationAndExplainsHistoryIsRetained() {
        var archived = false
        setScreen(account = account(), onArchive = { archived = true })

        compose.onNodeWithText("Archivar cuenta").performScrollTo().performClick()
        compose.onNodeWithText("No se eliminarán los movimientos ni el historial", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertFalse(archived) }

        compose.onNodeWithText("Archivar").performClick()

        compose.runOnIdle { assertTrue(archived) }
    }

    @Test
    fun accountDetailOpensItsMovementHistoryAction() {
        val bankAccount = account()
        var openedAccount: Account? = null
        setScreen(account = bankAccount, onNavigateToMovements = { openedAccount = it })

        compose.onNodeWithTag("account_view_movements").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(bankAccount, openedAccount) }
    }

    @Test
    fun yapeDetailOpensItsAttributedMovementHistoryAction() {
        val wallet = account().copy(
            type = AccountType.DIGITAL_WALLET,
            preset = AccountPreset.YAPE,
            linkedAccountId = AccountId.generate(),
        )
        var openedAccount: Account? = null
        setScreen(account = wallet, onNavigateToMovements = { openedAccount = it })

        compose.onNodeWithTag("account_view_movements").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(wallet, openedAccount) }
    }

    @Test
    fun archivedInstrumentCanBeReactivatedAfterConfirmationAndBackWorks() {
        var reactivated = false
        var navigatedBack = false
        setScreen(
            account = account().copy(isArchived = true),
            onReactivate = { reactivated = true },
            onNavigateBack = { navigatedBack = true },
        )

        compose.onNodeWithText("Este instrumento está archivado", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reactivar cuenta").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(reactivated) }
        compose.onNodeWithText("Reactivar").performClick()
        compose.runOnIdle { assertTrue(reactivated) }

        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.runOnIdle { assertTrue(navigatedBack) }
    }

    private fun setScreen(
        account: Account,
        onNavigateBack: () -> Unit = {},
        onSaveAppearance: (String, AccountPreset?) -> Unit = { _, _ -> },
        onCorrectOpeningBalance: (Long) -> Unit = {},
        onArchive: () -> Unit = {},
        onReactivate: () -> Unit = {},
        onNavigateToMovements: (Account) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                AccountDetailContent(
                    account = account,
                    card = null,
                    currentBalance = Money(2_500L, Currency.PEN),
                    creditCardSummary = null,
                    isLoading = false,
                    onNavigateBack = onNavigateBack,
                    onSaveAppearance = onSaveAppearance,
                    onCorrectOpeningBalance = onCorrectOpeningBalance,
                    onArchive = onArchive,
                    onReactivate = onReactivate,
                    onNavigateToMovements = onNavigateToMovements,
                )
            }
        }
    }

    private fun account() = Account(
        id = AccountId.generate(),
        userId = UserId.generate(),
        alias = "Mi cuenta",
        type = AccountType.SAVINGS,
        currency = Currency.PEN,
        preset = AccountPreset.BCP,
        initialBalance = Money(10_000L, Currency.PEN),
        openedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

}
