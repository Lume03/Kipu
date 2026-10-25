package com.kipu.app.feature.debts.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtPlanningTabTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun withoutRecordsTheEmptyStateOffersBothDirections() {
        var payable = 0
        compose.setContent {
            KipuTheme {
                DebtListScreen(emptyList(), null, {}, {}, onAddPayable = { payable++ }, onAddReceivable = {}, embedded = true)
            }
        }

        compose.onNodeWithText("Todavía no tienes deudas ni préstamos").assertIsDisplayed()
        compose.onNodeWithText("Todas 0").assertIsDisplayed()
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

        compose.onNodeWithText("Todas 2").assertIsDisplayed()
        compose.onNodeWithText("Debo 1").assertIsDisplayed()
        compose.onNodeWithText("Me deben 1").assertIsDisplayed()
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
