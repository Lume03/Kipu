package com.kipu.app.feature.debts.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.runtime.mutableStateOf
import com.kipu.app.feature.debts.data.sync.DebtReminderDestination
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtScheduleAndClosureFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reminderDefaultsToTwoDaysCanBeEditedAndDoesNotChangeDebtFinancialState() {
        val originalDebt = debt().copy(dueDate = LocalDate.parse("2026-11-10"))
        val leadDays = mutableStateOf<Int?>(null)
        compose.setContent {
            DebtScheduleScreen(
                state = DebtScheduleUiState(debt = originalDebt, installmentCount = "3", firstDueDate = "2026-11-10", reminderLeadDays = leadDays.value),
                onInstallmentCountChange = {}, onFirstDueDateChange = {},
                onReminderLeadDaysChange = { leadDays.value = it },
                onSchedule = {}, onCancelSchedule = {}, onCloseDebt = { _, _ -> }, onNavigateBack = {},
            )
        }

        compose.onNodeWithText("2 días").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Más")[1].performClick()
        compose.onNodeWithText("3 días").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Menos")[1].performClick()
        compose.onNodeWithText("2 días").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Menos")[1].performClick()
        compose.onNodeWithText("1 días").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, leadDays.value)
            assertEquals(LocalDate.parse("2026-11-10"), originalDebt.dueDate)
            assertEquals(10_000L, originalDebt.remainingPrincipalMinor)
            assertEquals(DebtLifecycleStatus.ACTIVE, originalDebt.status)

        }
    }

    @Test
    fun replacingScheduleRequiresExplicitConfirmationAndExplainsHistoryEffect() {
        var saveCalls = 0
        val state = DebtScheduleUiState(
            debt = debt(),
            installments = listOf(
                DebtScheduleItem(INSTALLMENT_ID, 1, LocalDate.parse("2026-11-10"), 10_000L, status = "PARTIAL"),
            ),
        )
        compose.setContent {
            DebtScheduleScreen(
                state = state,
                onInstallmentCountChange = {},
                onFirstDueDateChange = {},
                onReminderLeadDaysChange = {},
                onSchedule = { saveCalls++ },
                onCancelSchedule = {},
                onCloseDebt = { _, _ -> },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Guardar cronograma").performScrollTo().performClick()
        compose.onNodeWithText("Las cuotas pendientes o parciales", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saveCalls) }
        compose.onNodeWithText("Conservar actual").performClick()
        compose.runOnIdle { assertEquals(0, saveCalls) }

        compose.onNodeWithText("Guardar cronograma").performScrollTo().performClick()
        compose.onAllNodesWithText("Reemplazar cronograma")[1].performClick()
        compose.runOnIdle { assertEquals(1, saveCalls) }
    }

    @Test
    fun scheduledInstallmentIsClearlyNotAPaymentAndReminderOpensItsDebt() {
        var scheduleCancelled = false
        val state = DebtScheduleUiState(
            debt = debt(),
            installments = listOf(
                DebtScheduleItem(
                    id = INSTALLMENT_ID,
                    installmentNumber = 1,
                    dueDate = LocalDate.parse("2026-11-10"),
                    principalMinor = 3_334L,
                    status = "PENDING",
                ),
            ),
            installmentCount = "3",
            firstDueDate = "2026-11-10",
            reminderLeadDays = 3,
        )
        compose.setContent {
            DebtScheduleScreen(
                state = state,
                onInstallmentCountChange = {},
                onFirstDueDateChange = {},
                onReminderLeadDaysChange = {},
                onSchedule = {},
                onCancelSchedule = { scheduleCancelled = true },
                onCloseDebt = { _, _ -> },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Las cuotas planificadas organizan tu calendario", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pendiente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("33.34", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("debt-schedule-cancel").performScrollTo().performClick()
        assertEquals(true, scheduleCancelled)
        assertEquals("kipu://debts/detail/$DEBT_ID", DebtReminderDestination.uri(DEBT_ID))
    }

    @Test
    fun cancelledDebtKeepsItsBalanceVisibleAndIsNotPresentedAsPaid() {
        compose.setContent {
            DebtScheduleScreen(
                state = DebtScheduleUiState(
                    debt = debt().copy(status = DebtLifecycleStatus.CANCELLED, remainingPrincipalMinor = 8_000L),
                    installments = listOf(
                        DebtScheduleItem(INSTALLMENT_ID, 1, LocalDate.parse("2026-11-10"), 3_334L, status = "CANCELLED"),
                    ),
                ),
                onInstallmentCountChange = {},
                onFirstDueDateChange = {},
                onReminderLeadDaysChange = {},
                onSchedule = {},
                onCancelSchedule = {},
                onCloseDebt = { _, _ -> },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Cancelada").assertIsDisplayed()
        compose.onNodeWithText("Saldo pendiente no pagado").assertIsDisplayed()
        compose.onNodeWithTag("debt-schedule-total").assertTextContains("80.00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Liquidada").assertDoesNotExist()
    }

    @Test
    fun contextualHelpExplainsBothTabsAndCanBeDismissedWithoutChangingDebt() {
        val originalDebt = debt()
        var scheduleCalls = 0
        var closureCalls = 0
        compose.setContent {
            DebtScheduleScreen(
                state = DebtScheduleUiState(debt = originalDebt, isLoading = false),
                onInstallmentCountChange = {},
                onFirstDueDateChange = {},
                onReminderLeadDaysChange = {},
                onSchedule = { scheduleCalls++ },
                onCancelSchedule = {},
                onCloseDebt = { _, _ -> closureCalls++ },
                onNavigateBack = {},
            )
        }

        compose.onAllNodesWithContentDescription("Ayuda sobre cuotas y cronograma")[0].performClick()
        compose.onNodeWithText("no registra un pago ni reduce el saldo", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Entendido").assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("Entendido").assertDoesNotExist()

        compose.onNodeWithText("Ajustes y cierre").performClick()
        compose.onNodeWithText("Ajustes y cierre").assertIsSelected()
        compose.onAllNodesWithContentDescription("Ayuda sobre ajustes y cierre contable")[0].performClick()
        compose.onNodeWithText("queda registrado en el historial", substring = true).assertIsDisplayed()
        compose.onNodeWithText("saldo pendiente es cero", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Entendido").assertIsDisplayed()

        pressBack()

        compose.onNodeWithText("Ajustes y cierre").assertIsSelected()
        compose.onNodeWithText("Entendido").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, scheduleCalls)
            assertEquals(0, closureCalls)
            assertEquals(10_000L, originalDebt.remainingPrincipalMinor)
            assertEquals(DebtLifecycleStatus.ACTIVE, originalDebt.status)
        }
    }

    private fun debt() = DebtSummary(
        debtId = DEBT_ID,
        userId = OWNER_ID,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        remainingPrincipalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = 3,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 1L,
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val INSTALLMENT_ID = "88000000-0000-4000-8000-000000000003"
    }
}
