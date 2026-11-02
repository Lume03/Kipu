package com.kipu.app.feature.statements.presentation

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.RememberOption
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.feature.statements.storedStatement
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Merchant suggestions, merchant choice and "Recordar…" options of the statement review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w375dp-h812dp")
class StatementMerchantReviewTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val tambo = MerchantCatalogEntry(MerchantId("tambo"), "Tambo", "tambo")
    private val rappi = MerchantCatalogEntry(MerchantId("rappi"), "Rappi", "rappi")
    private val line = StatementImportLine("line", "import", owner, "fingerprint", 0, StatementLineKind.PURCHASE,
        LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), "YQ-TAMBO BARRERA-R", 1000, Currency.PEN, null, true)
    private val grasa = CategoryOption("grasa", "Grasa", "restaurant", CategoryType.EXPENSE)
    private val calls = mutableListOf<String>()

    private fun show(state: StatementReviewUiState) {
        compose.setContent {
            KipuTheme {
                StatementReviewScreen(state, onBack = {}, onCard = {}, onSelect = { _, _ -> }, onSelectAll = {},
                    onEdit = { _, _, _ -> }, onBatchCategory = {}, onConfirm = {}, onDiscard = {}, onDismissMessage = {}, onPlans = {},
                    onAcceptSuggestion = { calls += "accept:$it" }, onRejectSuggestion = { calls += "reject:$it" },
                    onChooseMerchant = { id, merchant -> calls += "choose:$id:$merchant" }, onRemoveMerchant = { calls += "remove:$it" },
                    onRememberAlias = { id, on -> calls += "alias:$id:$on" }, onRememberCategory = { id, on -> calls += "category:$id:$on" })
            }
        }
    }

    private fun review(vararg lines: StatementImportLine, merchants: List<MerchantCatalogEntry> = listOf(tambo, rappi)) =
        StatementReviewUiState(loading = false, merchants = merchants, categories = listOf(grasa),
            snapshot = StatementImportSnapshot(storedStatement(), lines.toList()))

    @Test fun pendingSuggestionCanBeAcceptedOrRejected() {
        show(review(line.copy(suggestedMerchantId = "tambo")))
        compose.onNodeWithTag("statement_review_list").performScrollToNode(hasTestTag("statement_suggestion_reject_line"))
        compose.onNodeWithTag("statement_suggestion_line", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("¿Es Tambo?", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("statement_suggestion_accept_line", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("statement_suggestion_reject_line", useUnmergedTree = true).performClick()
        assertEquals(listOf("accept:line", "reject:line"), calls)
    }

    @Test fun reviewedOrChosenLinesShowNoSuggestionAndChosenMerchantIsNamed() {
        show(review(line.copy(suggestedMerchantId = "tambo", merchantReviewed = true),
            line.copy(id = "chosen", merchantId = "rappi", merchantReviewed = true, description = "DLC*RAPPI PERU")))
        compose.onNodeWithTag("statement_suggestion_line", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("statement_merchant_chosen", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Rappi", useUnmergedTree = true).assertExists()
    }

    @Test fun editSheetShowsRememberOptionsAndMerchantField() {
        val chosen = line.copy(merchantId = "tambo", categoryId = "grasa", merchantReviewed = true)
        show(review(chosen).copy(
            rememberAliasOptions = mapOf("line" to RememberAliasOption("Recordar Tambo para 'YQ-TAMBO BARRERA-R'", true, "tambo", "YQ-TAMBO BARRERA-R")),
            rememberCategoryOptions = mapOf("line" to RememberOption("Recordar Grasa para Tambo", false, "tambo", "grasa"))))
        compose.onNodeWithTag("statement_line_line").performClick()
        compose.onNodeWithText("Recordar Tambo para 'YQ-TAMBO BARRERA-R'").assertExists()
        compose.onNodeWithTag("statement_remember_alias").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("statement_remember_category").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("statement_edit_merchant_clear").performScrollTo().performClick()
        assertEquals(listOf("alias:line:false", "category:line:true", "remove:line"), calls)
    }

    @Test fun merchantFieldOpensPickerAndChoosesMerchant() {
        show(review(line))
        compose.onNodeWithTag("statement_line_line").performClick()
        compose.onNodeWithTag("statement_edit_merchant").performScrollTo().assertTextContains("Elegir comercio", substring = true).performClick()
        compose.onNodeWithTag("merchant_picker_sheet").assertExists()
        compose.onNodeWithTag("merchant_search_input").performTextInput("Rap")
        compose.onNodeWithTag("merchant_item_rappi").performClick()
        assertEquals(listOf("choose:line:rappi"), calls)
    }

    @Test fun learningWarningsAppearAfterRegistering() {
        val registered = line.copy(merchantId = "tambo", categoryId = "grasa", status = StatementImportLineStatus.REGISTERED)
        show(review(registered).copy(successCount = 1, learningWarnings = listOf(
            StatementLearningWarning(StatementLearningWarningKind.CATEGORY_CONFLICT, "tambo", listOf("line")),
            StatementLearningWarning(StatementLearningWarningKind.ALIAS_SAVE_FAILED, "yq tambo barrera r", listOf("line")))))
        compose.onNodeWithTag("statement_learning_warnings").performScrollTo().assertExists()
        compose.onNodeWithText("No recordamos la categoría de Tambo", substring = true).assertExists()
        compose.onNodeWithText("No pudimos recordar el comercio de 'YQ-TAMBO BARRERA-R'", substring = true).assertExists()
        compose.onNodeWithText("Tus movimientos sí se registraron", substring = true).assertExists()
    }
}
