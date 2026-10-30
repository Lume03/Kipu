package com.kipu.app.feature.statements.presentation

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.feature.statements.storedStatement
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
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

    private fun show(state: StatementReviewUiState, dark: Boolean = false, fontScale: Float = 1f, confirm: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              KipuTheme(darkTheme = dark) {
                StatementReviewScreen(state, onBack = {}, onCard = {}, onSelect = { _, _ -> },
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
}
