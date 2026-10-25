package com.kipu.app.feature.budgets.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetConsumption
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BudgetScreensTest {
    @get:Rule val compose = createComposeRule()

    private val october = BudgetInterval(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
    private val september = BudgetInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))
    private val freeQuota = BudgetQuotaState(activeCount = 1, limit = 2, isPremium = false)

    private fun item(id: String = "b1", consumed: Long = 12_000L, limit: Long = 50_000L) = BudgetListItem(
        id = id, name = "Comida del mes", scopeLabel = "Todos los gastos", currency = "PEN",
        status = BudgetStatus.ACTIVE, periodType = BudgetPeriodType.MONTHLY, interval = october,
        consumption = BudgetConsumption(limit, consumed), hasPendingChange = false,
    )

    private fun budget(status: BudgetStatus = BudgetStatus.ACTIVE) = Budget(
        id = "b1", ownerId = "u", name = "Comida del mes", scope = BudgetScope.Total, limitMinor = 50_000L,
        currency = "PEN", schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY), status = status,
        createdOn = LocalDate.of(2026, 9, 1),
    )

    private fun row(interval: BudgetInterval, limit: Long, consumed: Long, current: Boolean) = BudgetPeriodRow(
        periodId = "p-${interval.start}", interval = interval, scopeLabel = "Todos los gastos",
        consumption = BudgetConsumption(limit, consumed), isCurrent = current,
    )

    private fun detail(status: BudgetStatus = BudgetStatus.ACTIVE) = BudgetDetailUiState.Content(
        BudgetDetailContent(
            budget = budget(status), scopeLabel = "Todos los gastos",
            current = row(october, 50_000L, 12_000L, true),
            history = listOf(row(september, 30_000L, 31_000L, false)),
            pendingChangeLabel = null,
        ),
    )

    // ---- Lista ----

    @Test fun createdBudgetOffersDetailAndOnlyShowsTheCelebrationOnce() {
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, {}, {}, {}, initialAction = "created") }
        compose.onNodeWithTag("budget_created_screen").assertIsDisplayed()
        compose.onNodeWithText("Ver presupuesto").performClick()
        compose.onNodeWithTag("budget_detail_screen").assertIsDisplayed()
        compose.onNodeWithTag("budget_created_screen").assertDoesNotExist()
    }

    @Test fun cardAndOverflowHaveIndependentActionsAndDismissDoesNotNavigate() {
        var details = 0
        var edits = 0
        compose.setContent {
            BudgetListScreen(BudgetListUiState.Content(listOf(item()), freeQuota), {}, {}, { details++ }, {}, {},
                onEditBudget = { edits++ })
        }
        compose.onNodeWithTag("budget_options_b1").performScrollTo().performClick()
        compose.onNodeWithTag("budget_actions_sheet").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, details) }
        compose.onNodeWithTag("planning_actions_cancel").performClick()
        compose.onNodeWithTag("budget_actions_sheet").assertDoesNotExist()
        compose.onNodeWithTag("budget_options_b1").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(0, details) }
        compose.onNodeWithTag("budget_card_b1").performClick()
        compose.runOnIdle { assertEquals(1, details) }
    }

    @Test
    fun localFiltersUseConfiguredThresholdsAndKeepExceededSeparate() {
        val warning = item("warning", consumed = 35_000L).copy(thresholdsBps = listOf(7000))
        val below = item("below", consumed = 35_000L).copy(thresholdsBps = listOf(9000))
        val exceeded = item("exceeded", consumed = 60_000L)
        val finished = warning.copy(id = "finished", status = BudgetStatus.FINISHED)
        compose.setContent {
            BudgetListScreen(BudgetListUiState.Content(listOf(warning, below, exceeded, finished), freeQuota), {}, {}, {}, {}, {})
        }
        compose.onNodeWithTag("budget_filter_1").assertTextContains("Atención 1").performClick()
        compose.onNodeWithTag("budget_card_warning").assertIsDisplayed()
        compose.onNodeWithTag("budget_card_below").assertDoesNotExist()
        compose.onNodeWithTag("budget_card_exceeded").assertDoesNotExist()
        compose.onNodeWithTag("budget_card_finished").assertDoesNotExist()
        compose.onNodeWithTag("budget_filter_2").performClick()
        compose.onNodeWithTag("budget_card_warning").assertDoesNotExist()
        compose.onNodeWithTag("budget_card_exceeded").assertIsDisplayed()
    }

    @Test
    fun quickAmountUpdatesInputAndSaveRemainsVisible() {
        var input by mutableStateOf(BudgetFormInput())
        var saves = 0
        compose.setContent {
            BudgetFormScreen(formState(input = input), {}, { input = it(input) }, { saves++ }, {}, {}, {})
        }
        compose.onNodeWithTag("budget_quick_500").performClick()
        compose.onNodeWithTag("budget_limit_input").assertTextContains("500")
        compose.onNodeWithTag("budget_thresholds").performScrollTo()
        compose.onNodeWithTag("budget_save").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("500", input.limitText); assertEquals(1, saves) }
    }

    @Test
    fun actionsSheetOpensDeleteConfirmationWithoutDeletingUntilConfirmed() {
        var deletes = 0
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, {}, { deletes++ }, {}) }
        compose.onNodeWithTag("budget_actions").performClick()
        compose.onNodeWithTag("budget_actions_sheet").assertIsDisplayed()
        compose.onNodeWithTag("budget_sheet_delete").performScrollTo().performClick()
        compose.onNodeWithTag("budget_delete_dialog").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithTag("budget_delete_confirm").performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test
    fun cancellingActionsSheetDoesNotEditFinishOrDelete() {
        var actions = 0
        compose.setContent { BudgetDetailScreen(detail(), {}, { actions++ }, { actions++ }, { actions++ }, {}) }
        compose.onNodeWithTag("budget_actions").performClick()
        compose.onNodeWithTag("planning_actions_cancel").performClick()
        compose.onNodeWithTag("budget_actions_sheet").assertDoesNotExist()
        compose.onNodeWithTag("budget_detail_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test
    fun planningGoalsTabUsesExistingBackRoute() {
        var backs = 0
        compose.setContent { BudgetListScreen(BudgetListUiState.Empty(freeQuota), { backs++ }, {}, {}, {}, {}) }
        compose.onNodeWithText("Planificación").assertIsDisplayed()
        compose.onNodeWithTag("budget_tab_goals").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun listShowsLoadingThenContentWithLimitConsumedRemainingAndPercent() {
        var state by mutableStateOf<BudgetListUiState>(BudgetListUiState.Loading)
        compose.setContent { BudgetListScreen(state, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("budget_loading").assertIsDisplayed()

        state = BudgetListUiState.Content(listOf(item()), freeQuota)
        // Las estadísticas conservan grupos accesibles propios dentro de la tarjeta.
        val card = compose.onNodeWithTag("budget_card_b1")
        card.assertIsDisplayed()
        compose.onNodeWithTag("budget_limit").assertTextContains("500.00", substring = true)
        compose.onNodeWithTag("budget_consumed").assertTextContains("120.00", substring = true)
        compose.onNodeWithTag("budget_remaining").assertTextContains("380.00", substring = true)
        compose.onNodeWithTag("budget_remaining").assertTextContains("Restante", substring = true)
        card.assertContentDescriptionContains("Consumido 24 %", substring = true)
        card.assertTextContains("Solo se cuentan gastos en PEN", substring = true)
    }

    @Test
    fun progressBarExposesRangeInfoAndSpokenDescriptionNotOnlyColor() {
        compose.setContent {
            BudgetListScreen(BudgetListUiState.Content(listOf(item()), freeQuota), {}, {}, {}, {}, {})
        }
        compose.onNodeWithTag("budget_progress", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertContentDescriptionContains("Consumido 24 %", substring = true)
            .assertContentDescriptionContains("Restante", substring = true)
    }

    @Test
    fun exceededBudgetShowsTextualExceededStateAndNegativeRemainingAsExceeded() {
        compose.setContent {
            BudgetListScreen(
                BudgetListUiState.Content(listOf(item(consumed = 60_000L)), freeQuota), {}, {}, {}, {}, {},
            )
        }
        val card = compose.onNodeWithTag("budget_card_b1")
        compose.onNodeWithTag("budget_remaining").assertTextContains("Te pasaste", substring = true)
        compose.onNodeWithTag("budget_remaining").assertTextContains("100.00", substring = true)
        card.assertContentDescriptionContains("Excedido por", substring = true)
    }

    @Test
    fun emptyStateIsShownWithCreateAction() {
        var created = false
        compose.setContent {
            BudgetListScreen(
                BudgetListUiState.Empty(BudgetQuotaState(0, 2, false)), {}, { created = true }, {}, {}, {},
            )
        }
        compose.onNodeWithTag("budget_empty").assertIsDisplayed()
        compose.onNodeWithTag("budget_create_button").performClick()
        compose.runOnIdle { assertTrue(created) }
    }

    @Test
    fun errorStateOffersRetry() {
        var retries = 0
        compose.setContent {
            BudgetListScreen(BudgetListUiState.Error("No se pudieron cargar los presupuestos."), {}, {}, {}, { retries++ }, {})
        }
        compose.onNodeWithText("No se pudieron cargar los presupuestos.").assertIsDisplayed()
        compose.onNodeWithTag("budget_retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun thirdActiveBudgetOnFreeExplainsLimitAndOffersPlansWithoutCreating() {
        var created = false
        var plans = false
        val atLimit = BudgetQuotaState(activeCount = 2, limit = 2, isPremium = false)
        compose.setContent {
            BudgetListScreen(
                BudgetListUiState.Content(listOf(item("a"), item("b")), atLimit), {}, { created = true }, {}, {}, { plans = true },
            )
        }
        compose.onNodeWithTag("budget_items").performScrollToNode(hasTestTag("budget_card_a"))
        compose.onNodeWithTag("budget_card_a").assertIsDisplayed()
        compose.onNodeWithTag("budget_items").performScrollToNode(hasTestTag("budget_card_b"))
        compose.onNodeWithTag("budget_card_b").assertIsDisplayed()
        compose.onNodeWithTag("budget_items").performScrollToNode(hasTestTag("budget_create_button"))
        compose.onNodeWithTag("budget_create_button").performClick()
        compose.runOnIdle { assertFalse(created) }
        compose.onNodeWithTag("budget_quota_dialog").assertIsDisplayed()
        compose.onNodeWithText("No se borró ni se ocultó ninguno", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ver planes").performClick()
        compose.runOnIdle {
            assertTrue(plans)
            assertFalse(created)
        }
    }

    @Test
    fun openingACardNavigatesToItsDetail() {
        var opened: String? = null
        compose.setContent {
            BudgetListScreen(
                BudgetListUiState.Content(listOf(item()), freeQuota), {}, {}, { opened = it }, {}, {},
            )
        }
        compose.onNodeWithTag("budget_card_b1").performClick()
        compose.runOnIdle { assertEquals("b1", opened) }
    }

    // ---- Formulario ----

    private fun formState(
        errors: Map<BudgetFormField, String> = emptyMap(),
        quotaLimit: Int? = null,
        input: BudgetFormInput = BudgetFormInput(),
        edit: Boolean = false,
        original: BudgetFormInput? = null,
    ) = BudgetFormUiState.Content(
        BudgetFormState(
            isEdit = edit, input = input, original = original,
            categories = listOf(
                BudgetCategoryOption("cat-food", "Comida", null, null),
                BudgetCategoryOption("cat-food-out", "Restaurantes", "cat-food", "Comida"),
            ),
            fieldErrors = errors, quotaLimitDialog = quotaLimit,
        ),
    )

    @Test
    fun formCreateCapturesNameLimitAndSaves() {
        var form by mutableStateOf(BudgetFormInput())
        var saved = 0
        compose.setContent {
            BudgetFormScreen(
                state = formState(input = form),
                onNavigateBack = {},
                onChange = { form = it(form) },
                onSave = { saved++ },
                onRetry = {}, onDismissQuota = {}, onSeePlans = {},
            )
        }
        compose.onNodeWithTag("budget_name").performTextInput("Comida")
        compose.onNodeWithTag("budget_limit_input").performTextInput("500")
        compose.onNodeWithTag("budget_save").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Comida", form.name)
            assertEquals("500", form.limitText)
            assertEquals(1, saved)
        }
        compose.onNodeWithTag("budget_currency_notice").assertTextContains("Solo se cuentan gastos en PEN", substring = true)
    }

    @Test
    fun formShowsInvertedRangeMessage() {
        compose.setContent {
            BudgetFormScreen(
                state = formState(
                    input = BudgetFormInput(periodType = BudgetPeriodType.CUSTOM_RANGE, rangeStartText = "2026-10-20", rangeEndText = "2026-10-10"),
                    errors = mapOf(BudgetFormField.RANGE_END to "La fecha de fin no puede ser anterior a la de inicio."),
                ),
                onNavigateBack = {}, onChange = {}, onSave = {}, onRetry = {}, onDismissQuota = {}, onSeePlans = {},
            )
        }
        compose.onNodeWithText("La fecha de fin no puede ser anterior a la de inicio.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun formShowsNextPeriodNoticeWhenEditingScopeOrCalendar() {
        val original = BudgetFormInput(name = "Comida", limitText = "500.00")
        compose.setContent {
            BudgetFormScreen(
                state = formState(
                    input = original.copy(periodType = BudgetPeriodType.WEEKLY), original = original, edit = true,
                ),
                onNavigateBack = {}, onChange = {}, onSave = {}, onRetry = {}, onDismissQuota = {}, onSeePlans = {},
            )
        }
        compose.onNodeWithTag("budget_next_period_notice").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun formQuotaDialogOffersPlans() {
        var plans = false
        compose.setContent {
            BudgetFormScreen(
                state = formState(quotaLimit = 2),
                onNavigateBack = {}, onChange = {}, onSave = {}, onRetry = {}, onDismissQuota = {}, onSeePlans = { plans = true },
            )
        }
        compose.onNodeWithTag("budget_quota_dialog").assertIsDisplayed()
        compose.onNodeWithText("Ver planes").performClick()
        compose.runOnIdle { assertTrue(plans) }
    }

    // ---- Detalle ----

    @Test
    fun detailShowsCurrentPeriodAndHistoryWithSnapshotLimit() {
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("budget_current_card").assertIsDisplayed()
        compose.onNodeWithTag("budget_history_p-2026-09-01").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("300.00", substring = true).assertCountAtLeastOne()
    }

    @Test
    fun deleteDialogDistinguishesPlanningFromMovementsAndCancelChangesNothing() {
        var deleted = 0
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, {}, { deleted++ }, {}) }
        compose.onNodeWithTag("budget_delete").performScrollTo().performClick()
        compose.onNodeWithTag("budget_delete_dialog").assertIsDisplayed()
        compose.onNodeWithText("irreversible", substring = true).assertIsDisplayed()
        compose.onNodeWithText("NO se borra ningún movimiento", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("budget_delete_cancel").performClick()
        compose.onNodeWithTag("budget_delete_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deleted) }
        compose.onNodeWithTag("budget_current_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleteConfirmationInvokesDeleteOnce() {
        var deleted = 0
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, {}, { deleted++ }, {}) }
        compose.onNodeWithTag("budget_delete").performScrollTo().performClick()
        compose.onNodeWithTag("budget_delete_confirm").performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }

    @Test
    fun finishRequiresConfirmationAndCancelKeepsBudgetActive() {
        var finished = 0
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, { finished++ }, {}, {}) }
        compose.onNodeWithTag("budget_finish").performScrollTo().performClick()
        compose.onNodeWithTag("budget_finish_dialog").assertIsDisplayed()
        compose.onNodeWithTag("budget_finish_cancel").performClick()
        compose.runOnIdle { assertEquals(0, finished) }
        compose.onNodeWithTag("budget_finish").performScrollTo().performClick()
        compose.onNodeWithTag("budget_finish_confirm").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun finishedBudgetHidesEditAndFinishButKeepsHistoryAndDelete() {
        compose.setContent { BudgetDetailScreen(detail(BudgetStatus.FINISHED), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("budget_edit").assertDoesNotExist()
        compose.onNodeWithTag("budget_finish").assertDoesNotExist()
        compose.onNodeWithTag("budget_history").assertExists()
        compose.onNodeWithTag("budget_delete").assertExists()
    }

    @Test
    fun detailEditAndBackAreLabelledForTalkBack() {
        compose.setContent { BudgetDetailScreen(detail(), {}, {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("Volver").assertIsDisplayed()
        compose.onNodeWithContentDescription("Acciones del presupuesto").performClick()
        compose.onNodeWithText("Editar").assertIsDisplayed()
    }

    @Test
    fun detailErrorAndNotFoundStates() {
        var retries = 0
        var state by mutableStateOf<BudgetDetailUiState>(BudgetDetailUiState.Error("No se pudo cargar el presupuesto."))
        compose.setContent { BudgetDetailScreen(state, { }, {}, {}, {}, { retries++ }) }
        compose.onNodeWithTag("budget_retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        state = BudgetDetailUiState.NotFound
        compose.onNodeWithTag("budget_detail_not_found").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountAtLeastOne() {
        assertTrue(fetchSemanticsNodes().isNotEmpty())
    }
}
