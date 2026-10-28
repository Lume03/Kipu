package com.kipu.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.budgets.domain.*
import com.kipu.app.feature.budgets.presentation.*
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.*
import com.kipu.app.feature.goals.presentation.*
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.presentation.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic fixtures only. Exercises the production composables without altering the user's ledger. */
class PlanningVisualEvidenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inspectReferenceStatesInBothThemesAndWithLargeLongText() {
        val screen = mutableStateOf("goal-detail")
        val dark = mutableStateOf(false)
        val large = mutableStateOf(false)
        val now = Instant.parse("2026-10-24T15:00:00Z")
        val name = "Laptop para diseño y trabajo con pantalla de alta resolución"
        val goal = Goal("visual-goal", "visual-owner", name, Money(300_000, Currency.PEN),
            targetDate = LocalDate.of(2026, 12, 15), linkedAccountId = "visual-account", iconKey = "laptop",
            createdAt = now, updatedAt = now)
        val accounts = listOf(GoalBackingAccount("visual-account", "Cuenta de ahorros para proyectos personales",
            Currency.PEN, Money(240_000, Currency.PEN), Money(135_000, Currency.PEN),
            Money(105_000, Currency.PEN), Money.zero(Currency.PEN)))
        val events = (0..2).map { month -> GoalEvent("visual-event-$month", "visual-owner", goal.id,
            GoalEventType.ALLOCATION, Money(45_000, Currency.PEN), "visual-account", "visual-operation-$month",
            occurredAt = now.minusSeconds(month * 31L * 86_400)) }
        val completed = goal.copy(status = GoalStatus.COMPLETED)
        val completedEvents = listOf(events.first().copy(delta = goal.target))
        val interval = BudgetInterval(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
        val budget = Budget("visual-budget", "visual-owner", "Alimentación y compras de supermercado para toda la familia",
            BudgetScope.Total, 60_000, "PEN", BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
            thresholdsBps = listOf(7000, 9000, 10000), createdOn = interval.start)
        val consumption = BudgetConsumption(60_000, 46_800)
        val budgetDetail = BudgetDetailUiState.Content(BudgetDetailContent(budget, "Todos los gastos",
            BudgetPeriodRow("visual-period", interval, "Todos los gastos", consumption, true), emptyList(), null))
        val quota = BudgetQuotaState(2, 2, false)
        val budgetItems = listOf(
            BudgetListItem(budget.id, budget.name, "Supermercado y comida", "PEN", BudgetStatus.ACTIVE,
                BudgetPeriodType.MONTHLY, interval, consumption, false, budget.thresholdsBps),
            BudgetListItem("visual-transport", "Transporte", "Gasolina y taxi", "PEN", BudgetStatus.ACTIVE,
                BudgetPeriodType.MONTHLY, interval, BudgetConsumption(30_000, 31_200), false, budget.thresholdsBps),
        )
        val notifications = listOf(
            AppNotification("visual-alert", "visual-owner", "Transporte superó el límite de tu presupuesto",
                "Revisa el detalle de tus gastos y ajusta tu planificación para los próximos días.",
                "BUDGET_ALERT", "BUDGET", budget.id, false, now, null),
            AppNotification("visual-success", "visual-owner", "Alcanzaste tu meta de ahorro para proyectos personales",
                "El dinero continúa reservado hasta que decidas liberarlo.", "GOAL_REACHED", "GOAL", goal.id, false, now, null),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 1.5f else 1f)) {
                KipuTheme(darkTheme = dark.value) {
                    Surface(Modifier.fillMaxSize().testTag("planning-evidence-root"), color = rememberCalmEmeraldColors().background) {
                        key(screen.value, dark.value, large.value) {
                            when (screen.value) {
                                "goal-detail", "goal-release" -> GoalDetailScreen(GoalDetailUiState(goal = goal,
                                    progress = GoalProgressCalculator.calculate(goal, events), events = events,
                                    backingAccounts = accounts, isLoading = false), {}, {}, { _, _, _ -> }, { _, _, _ -> }, {})
                                "goal-completed" -> GoalDetailScreen(GoalDetailUiState(goal = completed,
                                    progress = GoalProgressCalculator.calculate(completed, completedEvents), events = completedEvents,
                                    backingAccounts = accounts, isLoading = false), {}, {}, { _, _, _ -> }, { _, _, _ -> }, {})
                                "goal-contribution" -> GoalContributionSheet(GoalContributionUiState(goal = goal, accounts = accounts,
                                    isLoading = false), {}, { _, _, _, _ -> })
                                "goal-options", "goal-delete" -> GoalListScreen(GoalManagementUiState(
                                    goals = listOf(GoalListItemState(goal, GoalProgressCalculator.calculate(goal, if (screen.value == "goal-delete") emptyList() else events))),
                                    isLoading = false), {}, {}, {}, {}, {}, {})
                                "goal-empty" -> GoalListScreen(GoalManagementUiState(isLoading = false), {}, {}, {}, {}, {}, {})
                                "budget-list" -> BudgetListScreen(BudgetListUiState.Content(budgetItems, quota), {}, {}, {}, {}, {})
                                "budget-empty" -> BudgetListScreen(BudgetListUiState.Empty(BudgetQuotaState(0, 2, false)), {}, {}, {}, {}, {})
                                "budget-detail", "budget-options", "budget-delete" -> BudgetDetailScreen(budgetDetail, {}, {}, {}, {}, {})
                                "budget-form" -> BudgetFormScreen(BudgetFormUiState.Content(BudgetFormState(false,
                                    BudgetFormInput(name = budget.name, limitText = "600"), null, emptyList())), {}, {}, {}, {}, {}, {})
                                "notifications" -> NotificationCenterScreen(NotificationsUiState(notifications = notifications,
                                    visibleNotifications = notifications), 2, {}, {}, {}, {}, {}, {}, reducedMotion = true)
                                "notifications-empty" -> NotificationCenterScreen(NotificationsUiState(), 0, {}, {}, {}, {}, {}, {}, reducedMotion = true)
                            }
                        }
                    }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "planning-visual")
        output.mkdirs()
        val cases = listOf("goal-detail", "goal-release", "goal-completed", "goal-contribution", "goal-options", "goal-delete", "goal-empty",
            "budget-list", "budget-detail", "budget-options", "budget-delete", "budget-form", "budget-empty", "notifications", "notifications-empty")
        for (night in listOf(false, true)) for (case in cases) {
            compose.runOnIdle { screen.value = case; dark.value = night; large.value = false }
            compose.waitForIdle()
            when (case) {
                "goal-options", "goal-delete" -> {
                    compose.onNodeWithContentDescription("Opciones de $name").performScrollTo().performClick()
                    if (case == "goal-delete") compose.onNodeWithText("Eliminar meta").performScrollTo().performClick()
                }
                "goal-release" -> compose.onNodeWithTag("goal_release").performClick()
                "budget-delete" -> compose.onNodeWithTag("budget_delete").performScrollTo().performClick()
                "budget-options" -> compose.onNodeWithContentDescription("Acciones del presupuesto").performClick()
                "goal-contribution" -> compose.onNodeWithTag("goal_contribution_amount").performTextInput("330")
            }
            compose.waitForIdle()
            saveScreenshot(File(output, "$case-${if (night) "dark" else "light"}.png"))
        }
        for (case in listOf("goal-detail", "goal-completed", "goal-contribution", "budget-list", "budget-form", "budget-detail", "notifications")) {
            compose.runOnIdle { screen.value = case; large.value = true; dark.value = true }
            compose.waitForIdle()
            compose.onNodeWithTag("planning-evidence-root").assertIsDisplayed()
            saveScreenshot(File(output, "$case-large-text.png"))
        }
    }

    private fun saveScreenshot(file: File) {
        // Native dialog/window transitions are outside Compose's test clock.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        assertTrue(file.name, file.length() > 0)
    }
}
