package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.kipu.app.ui.component.LocalBalanceMasked
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
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
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.FinancialTerminology
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.DashboardUiState
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.espresso.Espresso.pressBack

class AccountDetailScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before
    fun grantNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} android.permission.POST_NOTIFICATIONS",
            ).close()
        }
    }

    @Test
    fun detailShowsSummaryAndRequiresExplicitEditing() {
        setScreen(account())
        compose.onNodeWithTag("account_summary_card").assertIsDisplayed()
        compose.onNodeWithTag("account_alias").assertDoesNotExist()
        compose.onNodeWithTag("edit_account").performClick()
        compose.onNodeWithTag("account_alias").assertIsDisplayed()
    }

    @Test
    fun cancellingWalletEditsDoesNotSaveAndReopeningRestoresStoredName() {
        var saves = 0
        val wallet = account().copy(type = AccountType.DIGITAL_WALLET, preset = AccountPreset.PLIN)
        setScreen(wallet, onSaveAppearance = { _, _ -> saves++ })
        compose.onNodeWithTag("edit_account").performClick()
        compose.onNodeWithTag("account_alias").performTextReplacement("Borrador")
        compose.onNodeWithContentDescription("Cerrar edición").performClick()
        compose.onNodeWithTag("account_alias").assertDoesNotExist()
        compose.onNodeWithTag("edit_account").performClick()
        compose.onNodeWithTag("account_alias").assertTextContains(wallet.alias)
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun maskedCreditCardDoesNotExposeItsLimitInTextOrAccessibility() {
        setCardScreen(creditCard(), masked = true)
        compose.onAllNodesWithText("1,000.00", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("1,000.00", substring = true).assertDoesNotExist()
    }

    @Test
    fun maskedOpeningCorrectionKeepsTheStoredAmountPrivateAndAcceptsExplicitInput() {
        var correctedAmount: Long? = null
        setScreen(account(), masked = true, onCorrectOpeningBalance = { correctedAmount = it })
        compose.onNodeWithTag("correct_opening_balance").performScrollTo().performClick()
        compose.onAllNodesWithText("100.00", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Saldo inicial oculto").performSemanticsAction(SemanticsActions.SetText) {
            it(AnnotatedString("12.50"))
        }
        compose.onNodeWithText("Aplicar corrección").performClick()
        compose.runOnIdle { assertEquals(1_250L, correctedAmount) }
    }

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

        compose.onNodeWithTag("edit_account").performClick()
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

        compose.onNodeWithTag("edit_account").performClick()
        compose.onAllNodesWithTag("account_preset_selector").assertCountEquals(0)
        compose.onNodeWithTag("cash_presentation").assertIsDisplayed()
        compose.onNodeWithTag("account_alias").assertIsDisplayed()
        compose.onNodeWithTag("save_account_presentation").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(AccountPreset.CASH, savedPreset) }
    }

    @Test
    fun savingsAccountShowsOnlyBankPresets() {
        setScreen(account())

        compose.onNodeWithTag("edit_account").performClick()
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

        compose.onNodeWithTag("edit_account").performClick()
        compose.onNodeWithTag("account_institution_selector").assertIsDisplayed()
        compose.onNodeWithText("Otro").assertIsDisplayed()
        compose.onNodeWithTag("save_account_presentation").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(AccountPreset.GENERIC, savedPreset) }
    }

    @Test
    fun openingBalanceChangesOnlyAfterExplicitAuditedCorrectionConfirmation() {
        var correctedAmount: Long? = null
        setScreen(account = account(), onCorrectOpeningBalance = { correctedAmount = it })

        compose.onNodeWithTag("correct_opening_balance").performScrollTo().performClick()
        compose.onNodeWithText("Se conserva el movimiento de apertura original", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Saldo inicial correcto (PEN)").performTextReplacement("12.50")
        compose.runOnIdle { assertEquals(null, correctedAmount) }

        compose.onNodeWithText("Aplicar corrección").performClick()

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
    fun cancellingArchiveKeepsDetailAndDoesNotInvokeArchiveOrReturn() {
        var archived = false
        var navigatedBack = false
        setScreen(
            account = account(),
            onArchive = { archived = true },
            onNavigateBack = { navigatedBack = true },
        )

        compose.onNodeWithText("Archivar cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()

        compose.onNodeWithTag("account_summary_card").assertIsDisplayed()
        compose.onAllNodesWithText("¿Archivar la cuenta?").assertCountEquals(0)
        compose.runOnIdle {
            assertFalse(archived)
            assertFalse(navigatedBack)
        }
    }

    @Test
    fun dismissingArchiveConfirmationKeepsDetailAndDoesNotInvokeArchiveOrReturn() {
        var archived = false
        var navigatedBack = false
        setScreen(
            account = account(),
            onArchive = { archived = true },
            onNavigateBack = { navigatedBack = true },
        )

        compose.onNodeWithText("Archivar cuenta").performScrollTo().performClick()
        pressBack()

        compose.onNodeWithTag("account_summary_card").assertIsDisplayed()
        compose.onAllNodesWithText("¿Archivar la cuenta?").assertCountEquals(0)
        compose.runOnIdle {
            assertFalse(archived)
            assertFalse(navigatedBack)
        }
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

    @Test
    fun activeCreditCardDebtStaysVisibleAndPaymentActionIsAvailable() {
        var paymentRequested = false
        setCardScreen(creditCard(), onPayCreditCard = { paymentRequested = true })

        compose.onNodeWithText("Deuda actual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pagar tarjeta").performScrollTo().performClick()

        compose.runOnIdle { assertTrue(paymentRequested) }
    }

    @Test
    fun creditCardDetailShowsCanonicalLimitAndAvailableCreditCopy() {
        setCardScreen(creditCard())

        assertTrue(
            compose.onAllNodesWithText(FinancialTerminology.TOTAL_CREDIT_LINE, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            compose.onAllNodesWithText(FinancialTerminology.AVAILABLE_CREDIT_NOT_OWN_MONEY)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun archivedCreditCardDebtStaysVisibleAndRequiresReactivationBeforePayment() {
        var reactivated = false
        setCardScreen(creditCard(isArchived = true), onReactivate = { reactivated = true })

        compose.onNodeWithText("Deuda pendiente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tarjeta archivada · Solo lectura").assertIsDisplayed()
        compose.onAllNodesWithText("Pagar tarjeta").assertCountEquals(0)
        compose.onNodeWithText("Reactivar tarjeta").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(reactivated) }
        compose.onNodeWithText("Reactivar").performClick()
        compose.runOnIdle { assertTrue(reactivated) }
    }

    @Test
    fun creditCardTermsActionRemainsReachableWithoutChangingDebtPresentation() {
        var editRequested = false
        setCardScreen(creditCard(), onEditCreditCardTerms = { editRequested = true })

        compose.onNodeWithText("Deuda actual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Editar tarjeta").performClick()

        compose.runOnIdle { assertTrue(editRequested) }
    }

    @Test
    fun archiveErrorShowsErrorAndDoesNotReturnToDashboard() {
        val target = account()
        val events = MutableSharedFlow<AccountUiEvent>(extraBufferCapacity = 1)
        val viewModel = mockk<AccountsViewModel>(relaxed = true) {
            every { instrumentsUiState } returns MutableStateFlow(
                InstrumentsUiState(isLoading = false, activeAccounts = listOf(target)),
            )
            every { dashboardUiState } returns MutableStateFlow(DashboardUiState(isLoading = false))
            every { creditNotifications } returns MutableStateFlow(emptyList())
            every { purchaseCategories } returns MutableStateFlow(emptyList())
            every { this@mockk.events } returns events
            every { observeAccountBalance(target.id) } returns flowOf(Money(2_500L, Currency.PEN))
        }
        var returned = false

        compose.setContent {
            MaterialTheme {
                AccountDetailScreen(
                    instrumentId = target.id.value,
                    isCard = false,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onReturnToDashboard = { returned = true },
                )
            }
        }
        compose.runOnIdle { events.tryEmit(AccountUiEvent.Error("No se pudo archivar")) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("No se pudo archivar").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("account_summary_card").assertIsDisplayed()
        compose.runOnIdle { assertFalse(returned) }
    }

    @Test
    fun debitDetailUsesLinkedAccountForEditingAndMovements() {
        val linked = account()
        var openedId: String? = null
        var movementAccount: Account? = null
        val debit = com.kipu.app.feature.accounts.domain.model.DebitCard(
            id = CardId("e1000000-0000-4000-8000-000000000003"),
            userId = linked.userId, issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "1234", currency = Currency.PEN,
            linkedAccountId = linked.id, stylePresetId = "bcp-debit-classic",
        )
        compose.setContent {
            MaterialTheme {
                AccountDetailContent(
                    account = null, card = debit, debitAccount = linked,
                    currentBalance = Money(25_000L, Currency.PEN), creditCardSummary = null,
                    isLoading = false, onNavigateBack = {}, onSaveAppearance = { _, _ -> },
                    onCorrectOpeningBalance = {}, onArchive = {}, onReactivate = {},
                    onNavigateToLinkedAccount = { openedId = it },
                    onNavigateToMovements = { movementAccount = it },
                )
            }
        }
        compose.onNodeWithText("Saldo de cuenta vinculada").assertIsDisplayed()
        compose.onNodeWithText("Ver y editar cuenta").performClick()
        compose.onNodeWithTag("account_view_movements").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(linked.id.value, openedId)
            assertEquals(linked, movementAccount)
        }
    }

    private fun setScreen(
        account: Account,
        onNavigateBack: () -> Unit = {},
        onSaveAppearance: (String, AccountPreset?) -> Unit = { _, _ -> },
        onCorrectOpeningBalance: (Long) -> Unit = {},
        onArchive: () -> Unit = {},
        onReactivate: () -> Unit = {},
        onNavigateToMovements: (Account) -> Unit = {},
        masked: Boolean = false,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalBalanceMasked provides masked) {
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
    }

    private fun setCardScreen(
        card: CreditCard,
        onPayCreditCard: () -> Unit = {},
        onEditCreditCardTerms: () -> Unit = {},
        onReactivate: () -> Unit = {},
        masked: Boolean = false,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalBalanceMasked provides masked) {
              MaterialTheme {
                AccountDetailContent(
                    account = null,
                    card = card,
                    currentBalance = null,
                    creditCardSummary = cardSummary(card),
                    isLoading = false,
                    onNavigateBack = {},
                    onSaveAppearance = { _, _ -> },
                    onCorrectOpeningBalance = {},
                    onArchive = {},
                    onReactivate = onReactivate,
                    onPayCreditCard = onPayCreditCard,
                    onEditCreditCardTerms = onEditCreditCardTerms,
                )
              }
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

    private fun creditCard(isArchived: Boolean = false) = CreditCard(
        id = CardId.generate(),
        userId = UserId.generate(),
        alias = "Visa principal",
        issuer = "Banco",
        network = CardNetwork.VISA,
        lastFourDigits = "4242",
        currency = Currency.PEN,
        creditLimitMinorUnits = 100_000L,
        billingDay = 10,
        dueDay = 20,
        isArchived = isArchived,
    )

    private fun cardSummary(card: CreditCard) = CreditCardWithSummary(
        card = card,
        debt = Money(3_000L, Currency.PEN),
        availableCredit = Money(card.creditLimitMinorUnits - 3_000L, Currency.PEN),
        utilizationPercentage = 3.0,
    )

}
