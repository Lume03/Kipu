package com.kipu.app.feature.statements.presentation

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.feature.statements.storedStatement
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w375dp-h812dp")
class StatementReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val cardId = "22222222-2222-2222-2222-222222222222"
    private val line = StatementImportLine("line", "import", owner, "fingerprint", 0, StatementLineKind.PURCHASE,
        LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), "Compra de prueba", 1000, Currency.PEN, null, true)

    private val accountId = "33333333-3333-3333-3333-333333333333"
    private val savings = storedStatement().copy(source = StatementSource.DEPOSIT_ACCOUNT, cardLastFour = "", accountReference = "4321", dueDate = null)
    private val cargo = line.copy(id = "cargo", kind = StatementLineKind.ACCOUNT_DEBIT, description = "Compra en tienda", amountMinor = 4550)
    private val abono = line.copy(id = "abono", kind = StatementLineKind.ACCOUNT_CREDIT, description = "Abono de sueldo", amountMinor = 250000)
    private val ownTransfer = line.copy(id = "own", kind = StatementLineKind.ACCOUNT_CREDIT, description = "TRANSF.BCO. CUENTA PROPIA", amountMinor = 10000, selected = false, possibleOwnTransfer = true)
    private val account = Account(AccountId(accountId), UserId(owner), "Ahorro BCP", AccountType.SAVINGS, Currency.PEN,
        initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH)
    private val debit = DebitCard(CardId(cardId), UserId(owner), alias = "Débito BCP", issuer = "BCP", network = CardNetwork.VISA,
        lastFourDigits = "9876", currency = Currency.PEN, linkedAccountId = AccountId(accountId))

    private fun show(state: StatementReviewUiState, dark: Boolean = false, fontScale: Float = 1f, card: (String) -> Unit = {}, confirm: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              KipuTheme(darkTheme = dark) {
                StatementReviewScreen(state, onBack = {}, onCard = card, onSelect = { _, _ -> },
                    onSelectAll = {}, onEdit = { _, _, _ -> }, onBatchCategory = {},
                    onConfirm = confirm, onDiscard = {}, onDismissMessage = {}, onPlans = {})
              }
            }
        }
    }

    @Test fun missingCardKeepsConfirmationDisabled() {
        show(StatementReviewUiState(loading = false, snapshot = StatementImportSnapshot(storedStatement(), listOf(line))))
        compose.onNodeWithTag("statement_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("statement_card_required").assertExists()
        compose.onNodeWithText("Registrar 1 movimientos").assertExists()
    }

    @Test fun activeCardEnablesReviewedBatch() {
        var confirmed = false
        val card = CreditCard(CardId(cardId), UserId(owner), issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 100000, billingDay = 20, dueDay = 5)
        show(StatementReviewUiState(loading = false, cards = listOf(card), snapshot =
            StatementImportSnapshot(storedStatement().copy(cardId = cardId), listOf(line.copy(categoryId = "category"))))) { confirmed = true }
        compose.onNodeWithTag("statement_confirm").assertIsEnabled().performClick()
        compose.onNodeWithTag("statement_confirm_sheet").assertExists()
        compose.onNodeWithTag("statement_confirm_sheet_register").assertIsEnabled().performClick()
        assertTrue(confirmed)
    }

    @Test fun purchasesWithoutCategoryCannotBeRegisteredFromTheSheet() {
        val card = CreditCard(CardId(cardId), UserId(owner), issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 100000, billingDay = 20, dueDay = 5)
        show(StatementReviewUiState(loading = false, cards = listOf(card), snapshot =
            StatementImportSnapshot(storedStatement().copy(cardId = cardId), listOf(line))))
        compose.onNodeWithTag("statement_confirm").performClick()
        compose.onNodeWithTag("statement_confirm_sheet_register").assertIsNotEnabled()
        compose.onNodeWithText("Categoriza 1 para registrar").assertExists()
    }

    @Test fun completedBatchShowsSuccessAndReturnAction() {
        show(StatementReviewUiState(loading = false, successCount = 3))
        compose.onNodeWithTag("statement_success").assertTextEquals("3 movimientos registrados")
        compose.onNodeWithText("Ver movimientos").assertIsDisplayed()
        compose.onNodeWithText("Listo").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w812dp-h375dp")
    fun successActionRemainsReachableInDarkLandscapeWithLargeText() {
        show(StatementReviewUiState(loading = false, successCount = 3), dark = true, fontScale = 1.5f)
        compose.onNodeWithText("Listo").performScrollTo().assertIsDisplayed()
    }

    @Test fun savingsStatementShowsAccountHeroAndAsksForDebitCard() {
        show(StatementReviewUiState(loading = false, snapshot = StatementImportSnapshot(savings, listOf(cargo, abono, ownTransfer))))
        compose.onNodeWithText("BCP · Cuenta de ahorros").assertExists()
        compose.onNodeWithTag("statement_account_reference").assertTextEquals("Cuenta •••• 4321")
        compose.onNode(hasText("−S/ 45.50") and hasAnyAncestor(hasTestTag("statement_deposit_totals_PEN"))).assertExists()
        compose.onNode(hasText("+S/ 2,600.00") and hasAnyAncestor(hasTestTag("statement_deposit_totals_PEN"))).assertExists()
        compose.onNodeWithTag("statement_card_required").assertExists()
        compose.onNodeWithText("Elige tu tarjeta de débito").assertExists()
        compose.onNodeWithText("Elige la tarjeta de débito para continuar").assertExists()
        compose.onNodeWithTag("statement_confirm").assertIsNotEnabled()
    }

    @Test fun savingsLinesShowCargoAbonoAndPossibleOwnTransfer() {
        show(StatementReviewUiState(loading = false, snapshot = StatementImportSnapshot(savings, listOf(cargo, abono, ownTransfer))))
        compose.onNodeWithTag("statement_review_list").performScrollToNode(hasTestTag("statement_line_cargo"))
        compose.onNodeWithText("Cargo").assertExists()
        compose.onAllNodesWithText("Abono").assertCountEquals(2)
        compose.onNodeWithTag("statement_own_transfer_own", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("statement_own_transfer_abono", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("+S/ 2,500.00") and hasAnyAncestor(hasTestTag("statement_line_abono")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("−S/ 45.50") and hasAnyAncestor(hasTestTag("statement_line_cargo")), useUnmergedTree = true).assertExists()
    }

    @Test fun manuallyDeselectedAccountMovementDoesNotBecomeOwnTransfer() {
        org.junit.Assert.assertFalse(cargo.copy(selected = false).maybeOwnTransfer())
    }

    @Test fun savingsBalancesAndPdfTotalsAreShownForBothCurrencies() {
        val totals = mapOf(
            Currency.PEN to StatementAccountTotals(100000, 4550, 250000, 345450),
            Currency.USD to StatementAccountTotals(20000, 1000, 3000, 22000))
        show(StatementReviewUiState(loading = false, snapshot = StatementImportSnapshot(savings.copy(accountTotals = totals), listOf(cargo, abono))))
        for ((currency, balance) in listOf(Currency.PEN to "S/ 1,000.00 → S/ 3,454.50", Currency.USD to "US$ 200.00 → US$ 220.00")) {
            compose.onNode(hasText("Saldo anterior → Saldo final") and hasAnyAncestor(hasTestTag("statement_deposit_totals_${currency.name}"))).assertExists()
            compose.onNode(hasText(balance) and hasAnyAncestor(hasTestTag("statement_deposit_totals_${currency.name}"))).assertExists()
        }
        // Account totals are from the PDF, unaffected by excluded review rows.
        compose.onNode(hasText("+S/ 2,500.00") and hasAnyAncestor(hasTestTag("statement_deposit_totals_PEN"))).assertExists()
    }

    @Test fun ownTransferHintSurvivesSelectionChangesAndDuplicateDetection() {
        assertTrue(ownTransfer.copy(selected = true, possibleDuplicate = true).maybeOwnTransfer())
    }

    @Test fun debitCardSelectorListsDebitCardsOnly() {
        var picked: String? = null
        val credit = CreditCard(CardId("44444444-4444-4444-4444-444444444444"), UserId(owner), issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 100000, billingDay = 20, dueDay = 5)
        show(StatementReviewUiState(loading = false, cards = listOf(credit), debitCards = listOf(debit), accounts = listOf(account),
            snapshot = StatementImportSnapshot(savings, listOf(cargo))), card = { picked = it })
        compose.onNodeWithTag("statement_card_required").performClick()
        compose.onNodeWithText("Elegir tarjeta de débito").assertExists()
        compose.onNodeWithTag("card_item_${credit.id.value}").assertDoesNotExist()
        compose.onNodeWithTag("card_item_$cardId").performClick()
        compose.onNodeWithTag("btn_confirm_selection").performClick()
        assertEquals(cardId, picked)
    }

    @Test fun chosenDebitCardNamesLinkedAccountInsteadOfPdfMatch() {
        show(StatementReviewUiState(loading = false, debitCards = listOf(debit), accounts = listOf(account),
            snapshot = StatementImportSnapshot(savings.copy(cardId = cardId), listOf(cargo.copy(categoryId = "category"), abono.copy(categoryId = "salary")))))
        compose.onNodeWithTag("statement_linked_account").assertTextEquals("Cuenta Ahorro BCP · PEN")
        compose.onNodeWithText("Coincide con el PDF").assertDoesNotExist()
        compose.onNodeWithTag("statement_confirm").assertIsEnabled().performClick()
        compose.onNode(hasText("Cargos") and hasAnyAncestor(hasTestTag("statement_confirm_sheet"))).assertExists()
        compose.onNodeWithText("Se registrarán 2 movimientos en Ahorro BCP: los cargos como gastos y los abonos como ingresos. Podrás editarlos después.").assertExists()
    }

    @Test fun creditStatementKeepsCardMatchChip() {
        val card = CreditCard(CardId(cardId), UserId(owner), issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 100000, billingDay = 20, dueDay = 5)
        show(StatementReviewUiState(loading = false, cards = listOf(card), snapshot =
            StatementImportSnapshot(storedStatement().copy(cardId = cardId), listOf(line))))
        compose.onNodeWithText("Coincide con el PDF").assertExists()
        compose.onNodeWithTag("statement_account_reference").assertDoesNotExist()
    }
}
