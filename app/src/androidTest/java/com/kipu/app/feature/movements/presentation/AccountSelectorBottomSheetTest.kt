package com.kipu.app.feature.movements.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class AccountSelectorBottomSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun creditCardRowShowsAvailableCreditCopyAndAccessibleMeaning() {
        val card = CreditCard(
            id = CardId.generate(),
            userId = UserId.generate(),
            alias = "Principal",
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "4821",
            currency = Currency.PEN,
            creditLimitMinorUnits = 400_000,
            billingDay = 15,
            dueDay = 5,
        )

        compose.setContent {
            KipuTheme {
                AccountSelectorBottomSheet(
                    onDismissRequest = {},
                    accounts = emptyList(),
                    creditCards = listOf(card),
                    cardDebts = mapOf(card.id.value to Money(142_000, Currency.PEN)),
                    cardAvailableCredits = mapOf(card.id.value to Money(258_000, Currency.PEN)),
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                )
            }
        }

        compose.onNodeWithText("Créd. disp.:", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Crédito disponible, no es dinero propio:",
            substring = true,
        ).assertIsDisplayed()
    }

    private val user = UserId.generate()

    private fun account(alias: String, type: AccountType, preset: AccountPreset?, linked: AccountId? = null) = Account(
        id = AccountId.generate(), userId = user, alias = alias, type = type, currency = Currency.PEN,
        preset = preset, initialBalance = Money(0, Currency.PEN),
        openedAt = Instant.parse("2026-10-01T00:00:00Z"), linkedAccountId = linked,
    )

    private fun card() = CreditCard(
        id = CardId.generate(), userId = user, alias = "Principal", issuer = "BCP", network = CardNetwork.VISA,
        lastFourDigits = "4821", currency = Currency.PEN, creditLimitMinorUnits = 400_000, billingDay = 15, dueDay = 5,
    )

    @Test
    fun affiliatedWalletShowsSharedLinkedBalanceAndMissingBalanceIsNotZero() {
        val bcp = account("Cuenta BCP", AccountType.SAVINGS, AccountPreset.BCP)
        val yape = account("Yape", AccountType.DIGITAL_WALLET, AccountPreset.YAPE, bcp.id)
        val bbva = account("BBVA", AccountType.SAVINGS, AccountPreset.BBVA)

        compose.setContent {
            KipuTheme {
                AccountSelectorBottomSheet(
                    onDismissRequest = {},
                    accounts = listOf(bcp, yape, bbva),
                    accountBalances = mapOf(bcp.id.value to Money(98_770, Currency.PEN)),
                    balancesLoaded = true,
                )
            }
        }

        compose.onNodeWithText("Yape · Comparte saldo con Cuenta BCP").assertIsDisplayed()
        compose.onNodeWithText("Saldo compartido").assertIsDisplayed()
        compose.onNodeWithText("Saldo no disponible").assertIsDisplayed()
        compose.onNodeWithText("S/ 0.00", substring = true).assertDoesNotExist()
    }

    @Test
    fun incomePurposeNeverOffersCreditCards() {
        val bcp = account("Cuenta BCP", AccountType.SAVINGS, AccountPreset.BCP)
        val card = card()

        compose.setContent {
            KipuTheme {
                AccountSelectorBottomSheet(
                    onDismissRequest = {},
                    accounts = listOf(bcp),
                    creditCards = listOf(card),
                    purpose = AccountSelectorPurpose.INCOME_DESTINATION,
                )
            }
        }

        compose.onNodeWithTag("chip_filter_cards").assertDoesNotExist()
        compose.onNodeWithTag("card_item_${card.id.value}").assertDoesNotExist()
        compose.onNodeWithTag("account_item_${bcp.id.value}").assertIsDisplayed()
    }

    @Test
    fun transferDestinationOffersCardsAsCardPayment() {
        val card = card()

        compose.setContent {
            KipuTheme {
                AccountSelectorBottomSheet(
                    onDismissRequest = {},
                    accounts = emptyList(),
                    creditCards = listOf(card),
                    purpose = AccountSelectorPurpose.TRANSFER_DESTINATION,
                )
            }
        }

        compose.onNodeWithText("Pago de tarjeta · •••• 4821").assertIsDisplayed()
    }

    @Test
    fun filterPurposeConfirmsSeveralAccountsAndCards() {
        val bcp = account("Cuenta BCP", AccountType.SAVINGS, AccountPreset.BCP)
        val bbva = account("BBVA", AccountType.SAVINGS, AccountPreset.BBVA)
        val card = card()
        var confirmed: Pair<Set<String>, Set<String>>? = null

        compose.setContent {
            KipuTheme {
                AccountSelectorBottomSheet(
                    onDismissRequest = {},
                    accounts = listOf(bcp, bbva),
                    creditCards = listOf(card),
                    purpose = AccountSelectorPurpose.FILTER,
                    onSelectionConfirmed = { accounts, cards -> confirmed = accounts to cards },
                )
            }
        }

        compose.onNodeWithTag("btn_new_account").assertDoesNotExist()
        compose.onNodeWithTag("account_item_${bcp.id.value}").performClick()
        compose.onNodeWithTag("account_item_${bbva.id.value}").performClick()
        compose.onNodeWithTag("card_item_${card.id.value}").performClick()
        compose.onNodeWithTag("btn_confirm_selection").performClick()

        compose.runOnIdle {
            assertEquals(setOf(bcp.id.value, bbva.id.value) to setOf(card.id.value), confirmed)
        }
    }

    @Test
    fun maskedBalancesAreNotSpokenInRowDescriptions() {
        val bcp = account("Cuenta BCP", AccountType.SAVINGS, AccountPreset.BCP)

        compose.setContent {
            KipuTheme {
                CompositionLocalProvider(LocalBalanceMasked provides true) {
                    AccountSelectorBottomSheet(
                        onDismissRequest = {},
                        accounts = listOf(bcp),
                        accountBalances = mapOf(bcp.id.value to Money(98_770, Currency.PEN)),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Saldo disponible: Monto oculto", substring = true).assertIsDisplayed()
        compose.onNodeWithText("987.70", substring = true).assertDoesNotExist()
    }
}
