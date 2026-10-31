package com.kipu.app.feature.debts.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtPlanningTabTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dueAlertsSeparateOverdueFromUpcomingAndOpenTheCorrectCollection() {
        var action = ""
        compose.setContent {
            KipuTheme {
                DebtListScreen(listOf(
                    debt("past", DebtObligationType.RECEIVABLE, dueDate = LocalDate.parse("2026-10-21")),
                    debt("future", DebtObligationType.PAYABLE, dueDate = LocalDate.parse("2026-10-28"))),
                    null, {}, {}, {}, {}, today = LocalDate.parse("2026-10-24"), onDebtAction = { id, selected -> action = "$id:$selected" })
            }
        }
        compose.onNodeWithTag("debt_due_open").performClick()
        compose.onNodeWithTag("debt_due_filter_1").performClick()
        compose.onNode(hasText("Contraparte past") and hasAnyAncestor(hasTestTag("debt_due_alerts"))).assertIsDisplayed()
        compose.onNode(hasText("Contraparte future") and hasAnyAncestor(hasTestTag("debt_due_alerts"))).assertDoesNotExist()
        compose.onNodeWithText("Registrar cobro").performClick()
        compose.runOnIdle { assertEquals("past:settle", action) }
    }

    @Test fun alertsShowOneCardPerDebtWithGroupedOverdueAndNoDistantInstallments() {
        val today = LocalDate.parse("2026-10-24")
        compose.setContent {
            KipuTheme {
                DebtDueAlertsSheet(
                    debts = listOf(debt("past", DebtObligationType.PAYABLE), debt("far", DebtObligationType.RECEIVABLE)),
                    schedules = mapOf(
                        "past" to listOf(
                            DebtScheduleItem("i1", 1, today.minusDays(3), 1_000),
                            DebtScheduleItem("i2", 2, today.minusDays(1), 1_000, "PARTIAL"),
                            DebtScheduleItem("i3", 3, today.plusDays(26), 1_000)),
                        "far" to listOf(DebtScheduleItem("i4", 1, today.plusDays(26), 1_000))),
                    today = today, onDismiss = {}, onView = {}, onSettle = {},
                )
            }
        }
        compose.onAllNodesWithTag("debt_due_card_past").assertCountEquals(1)
        compose.onNodeWithTag("debt_due_card_far").assertDoesNotExist()
        compose.onNodeWithText("2 cuotas vencidas").assertIsDisplayed()
        compose.onNodeWithText("Todas 1").assertIsDisplayed()
        compose.onNodeWithTag("debt_due_filter_2").performClick()
        compose.onNodeWithText("Todo al día en este filtro").assertIsDisplayed()
    }

    @Test fun distantDueAppearsOnlyInAllWithoutAlertTotalsAndOpensDetails() {
        val today = LocalDate.parse("2026-10-30")
        var viewed = ""
        compose.setContent {
            KipuTheme {
                DebtDueAlertsSheet(
                    debts = listOf(debt("far", DebtObligationType.PAYABLE)),
                    schedules = mapOf("far" to (1..5).map { number ->
                        DebtScheduleItem("i$number", number, today.plusDays(26L + (number - 1) * 30L), 1_000)
                    }),
                    today = today, onDismiss = {}, onView = { viewed = it }, onSettle = {},
                )
            }
        }
        compose.onNodeWithText("Sin alertas en tu plazo de aviso").assertIsDisplayed()
        compose.onNodeWithText("Nada vencido ni por vencer en tu plazo de aviso").assertIsDisplayed()
        compose.onNodeWithText("Próximos vencimientos").assertIsDisplayed()
        compose.onNodeWithText("Todas 0").assertIsDisplayed()
        compose.onNodeWithText("Programado por pagar").assertDoesNotExist()
        compose.onNodeWithText("En 26 días").assertIsDisplayed()
        compose.onAllNodesWithTag("debt_upcoming_row_far").assertCountEquals(1)
        compose.onNodeWithTag("debt_due_filter_2").performClick()
        compose.onNodeWithTag("debt_upcoming_row_far").assertDoesNotExist()
        compose.onNodeWithTag("debt_due_filter_0").performClick()
        compose.onNodeWithTag("debt_upcoming_row_far").performClick()
        compose.runOnIdle { assertEquals("far", viewed) }
    }

    @Test fun overflowOpensSheetWithoutOpeningCardAndDispatchesTheChosenAction() {
        var details = 0
        var action = ""
        compose.setContent {
            KipuTheme {
                DebtListScreen(listOf(debt("d1", DebtObligationType.RECEIVABLE)), null, {}, { details++ }, {}, {},
                    onDebtAction = { id, selected -> action = "$id:$selected" })
            }
        }
        compose.onNodeWithTag("debt_options_d1").performClick()
        compose.onNodeWithTag("debt_actions_sheet").assertIsDisplayed()
        compose.onNodeWithText("Registrar cobro").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, details) }
        compose.onNodeWithTag("planning_actions_cancel").performClick()
        compose.onNodeWithTag("debt_actions_sheet").assertDoesNotExist()
        compose.onNodeWithTag("debt_options_d1").performClick()
        compose.onNodeWithTag("debt_action_edit").performClick()
        compose.runOnIdle { assertEquals("d1:edit", action); assertEquals(0, details) }
        compose.onNodeWithTag("debt_card_d1").performClick()
        compose.runOnIdle { assertEquals(1, details) }
    }

    @Test fun withoutRecordsTheEmptyStateOffersBothDirections() {
        var payable = 0
        compose.setContent {
            KipuTheme {
                DebtListScreen(emptyList(), null, {}, {}, onAddPayable = { payable++ }, onAddReceivable = {}, embedded = true)
            }
        }

        compose.onNodeWithText("Todavía no tienes deudas ni préstamos").assertIsDisplayed()
        compose.onNodeWithTag("debt_filter_button").assertIsDisplayed()
        compose.onNodeWithTag("debt_shortcut_payable").performClick()
        compose.runOnIdle { assertEquals(1, payable) }
        compose.onNodeWithTag("debt_empty_register").performClick()
        compose.onNodeWithText("Agregar deuda o préstamo").assertIsDisplayed()
    }

    @Test fun withRecordsTheListShowsTotalsCountsAndDueSoonInstallment() {
        compose.setContent {
            KipuTheme {
                DebtListScreen(
                    debts = listOf(debt("d1", DebtObligationType.PAYABLE, 400_000L, 240_000L, LocalDate.parse("2026-10-28")),
                        debt("d2", DebtObligationType.RECEIVABLE, 30_000L, 15_000L, LocalDate.parse("2026-10-21"))),
                    selectedType = null, onTypeSelected = {}, onDebtSelected = {}, onAddPayable = {}, onAddReceivable = {},
                    embedded = true, today = LocalDate.parse("2026-10-24"),
                )
            }
        }

        compose.onNodeWithTag("debt_filter_button").assertIsDisplayed()
        compose.onNodeWithText("Vence en 4 días").assertIsDisplayed()
        compose.onNodeWithText("Vencida hace 3 días").assertIsDisplayed()
        compose.onNodeWithText("40%").assertIsDisplayed()
    }

    @Test fun basicPlanAtTheSharedLimitShowsTheLimitSheetInsteadOfCreating() {
        var created = 0
        compose.setContent {
            KipuTheme {
                DebtListScreen(
                    debts = listOf(debt("d1", DebtObligationType.PAYABLE), debt("d2", DebtObligationType.RECEIVABLE)),
                    selectedType = null, onTypeSelected = {}, onDebtSelected = {},
                    onAddPayable = { created++ }, onAddReceivable = { created++ },
                    embedded = true, freeLimitReached = true, activeDebtCount = 2,
                )
            }
        }

        compose.onNodeWithTag("debt_new_chip").performClick()
        compose.onNodeWithText("Llegaste al límite de tu Plan Básico").assertIsDisplayed()
        compose.onNodeWithText("2 de 2 deudas o préstamos activos", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, created) }
    }

    @Test fun filterSheetKeepsSelectionAndDispatchesBothDebtDirections() {
        var selected by androidx.compose.runtime.mutableStateOf<DebtObligationType?>(null)
        compose.setContent {
            KipuTheme {
                DebtListScreen(emptyList(), selected, { selected = it }, {}, {}, {}, embedded = true)
            }
        }
        compose.onNodeWithTag("debt_filter_button").performClick()
        compose.onNodeWithTag("debt_filter_all").assertIsSelected()
        compose.onNodeWithTag("debt_filter_payable").performClick()
        compose.runOnIdle { assertEquals(DebtObligationType.PAYABLE, selected) }
        compose.onNodeWithTag("debt_filter_payable").assertDoesNotExist()
        compose.onNodeWithTag("debt_filter_button").performClick()
        compose.onNodeWithTag("debt_filter_payable").assertIsSelected()
        compose.onNodeWithTag("debt_filter_receivable").performClick()
        compose.runOnIdle { assertEquals(DebtObligationType.RECEIVABLE, selected) }
        compose.onNodeWithTag("debt_filter_button").performClick()
        compose.onNodeWithTag("debt_filter_all").performClick()
        compose.runOnIdle { assertEquals(null, selected) }
    }

    private fun debt(
        id: String,
        type: DebtObligationType,
        principal: Long = 10_000L,
        remaining: Long = 10_000L,
        dueDate: LocalDate? = null,
    ) = DebtSummary(
        debtId = id,
        userId = "owner",
        obligationType = type,
        counterpartyName = "Contraparte $id",
        principalMinor = principal,
        remainingPrincipalMinor = remaining,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-06-28"),
        dueDate = dueDate,
        reminderLeadDays = null,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 1L,
        openingMode = DebtOpeningMode.HISTORICAL,
    )
}
