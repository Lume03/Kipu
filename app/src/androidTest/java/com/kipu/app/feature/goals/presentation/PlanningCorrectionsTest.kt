package com.kipu.app.feature.goals.presentation

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.*
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.feature.debts.presentation.DebtDetailScreen
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningCorrectionsTest {
    @get:Rule val compose = createComposeRule()
    private val goal = Goal("goal", "owner", "Laptop para trabajar", Money(500_000, Currency.PEN),
        linkedAccountId = "account", iconKey = "laptop", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private val event = GoalEvent("event", "owner", "goal", GoalEventType.ALLOCATION,
        Money(100_000, Currency.PEN), "account", "operation", occurredAt = Instant.EPOCH)
    private val account = GoalBackingAccount("account", "Cuenta de ahorros", Currency.PEN,
        Money(200_000, Currency.PEN), Money(100_000, Currency.PEN), Money(100_000, Currency.PEN), Money.zero(Currency.PEN))
    private val detail get() = GoalDetailUiState(goal = goal, progress = GoalProgressCalculator.calculate(goal, listOf(event)),
        events = listOf(event), backingAccounts = listOf(account), isLoading = false)

    @Test fun deleteWithReservesRequiresConfirmationAndOffersPause() {
        var deleted = false
        compose.setContent { KipuTheme { GoalDetailScreen(detail, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {},
            initialAction = "delete", onDelete = { deleted = true }) } }
        compose.onNodeWithTag("goal_delete_confirm").assertIsEnabled()
        compose.onNodeWithText("Mejor pausarla").assertIsDisplayed().performClick()
        compose.onNodeWithTag("goal_archive_confirm").assertIsEnabled()
        compose.runOnIdle { assertFalse(deleted) }
    }

    @Test fun completeRequiresExplicitConfirmation() {
        var completed = false
        compose.setContent { KipuTheme { GoalDetailScreen(detail, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {},
            initialAction = "complete", onComplete = { completed = true }) } }
        compose.onNodeWithText("El objetivo se ajustará al dinero que ya tienes reservado:").assertIsDisplayed()
        compose.runOnIdle { assertFalse(completed) }
        compose.onNodeWithTag("goal_complete_confirm").performClick()
        compose.runOnIdle { assertTrue(completed) }
    }

    @Test fun optionsShowCompletionAndDeletionEvenWithReserves() {
        compose.setContent { KipuTheme { GoalListScreen(GoalManagementUiState(
            goals = listOf(GoalListItemState(goal, detail.progress!!)), isLoading = false), {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Opciones de ${goal.name}").performScrollTo().performClick()
        compose.onNodeWithText("Marcar como lograda").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Eliminar meta").performScrollTo().performClick()
        compose.onNodeWithText("Mejor pausarla").assertIsDisplayed()
        compose.onNodeWithTag("goal_delete_confirm").assertIsEnabled()
    }

    @Test fun emptyGoalCannotBeMarkedAsAchieved() {
        compose.setContent { KipuTheme { GoalCompleteDialog(goal, Money.zero(Currency.PEN), false, {}, {}) } }
        compose.onNodeWithTag("goal_complete_confirm").assertIsNotEnabled()
    }

    @Test fun goalSummaryHasThreeColumnsAndManagementActionsStayInMenu() {
        compose.setContent { KipuTheme { GoalDetailScreen(detail, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {}) } }
        compose.onNodeWithText("Reservado").assertIsDisplayed()
        compose.onNodeWithText("Faltan").assertIsDisplayed()
        compose.onNodeWithText("Objetivo").assertIsDisplayed()
        compose.onNodeWithTag("goal_reassign").assertDoesNotExist()
        compose.onNodeWithTag("goal_archive").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones de la meta").performClick()
        compose.onNodeWithTag("goal_archive").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("goal_archive_confirm").assertIsEnabled()
    }

    @Test fun finalizedGoalsStartFoldedBelowActiveGoals() {
        val completed = goal.copy(id = "done", name = "Meta completada", status = GoalStatus.COMPLETED, target = event.delta)
        compose.setContent { KipuTheme { GoalListScreen(GoalManagementUiState(goals = listOf(
            GoalListItemState(goal, detail.progress!!), GoalListItemState(completed, GoalProgressCalculator.calculate(completed, emptyList()))),
            isLoading = false), {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("goal_card_done").assertDoesNotExist()
        compose.onNodeWithText("Metas finalizadas (1)").performScrollTo().performClick()
        compose.onNodeWithTag("goal_card_done").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Metas archivadas", substring = true).assertDoesNotExist()
    }

    @Test fun celebrationMovesAcrossFramesWithoutChangingTheReserve() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KipuTheme { GoalAchievedScreen(goal.copy(status = GoalStatus.COMPLETED, target = event.delta), listOf(event), {}, {}, reducedMotion = false) } }
        compose.mainClock.advanceTimeBy(32)
        val first = compose.onNodeWithTag("goal_celebration").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(640)
        val second = compose.onNodeWithTag("goal_celebration").captureToImage().toPixelMap()
        var changes = 0
        for (y in 0 until first.height step 8) for (x in 0 until first.width step 8) if (first[x, y] != second[x, y]) changes++
        assertTrue("Confetti and the check pulse must move", changes > 40)
        compose.onAllNodesWithText("S/ 1,000.00").assertCountEquals(2)
    }

    @Test fun choosingAnAccountClosesTheKeyboardAndKeepsTheHeaderVisible() {
        compose.setContent { KipuTheme { GoalContributionSheet(GoalContributionUiState(goal = goal, accounts = listOf(account), isLoading = false), {}, { _, _, _, _ -> }) } }
        val header = compose.onNodeWithText("Aportar a ${goal.name}")
        val initialTop = header.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("goal_contribution_amount").performTextInput("10")
        compose.onNodeWithText(account.name).performScrollTo().performClick()
        compose.onNodeWithTag("goal_contribution_amount").assertIsNotFocused()
        header.assertIsDisplayed()
        assertEquals(initialTop, header.fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun displayPreferencesPersistAndRemainIsolatedByOwner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstOwner = "ui-test-${java.util.UUID.randomUUID()}"
        val secondOwner = "ui-test-${java.util.UUID.randomUUID()}"
        val preferences = com.kipu.app.feature.goals.data.GoalDisplayPreferences(context)
        try {
            kotlinx.coroutines.runBlocking { preferences.pause(firstOwner, "paused", true); preferences.remove(firstOwner, "removed") }
            val reopened = com.kipu.app.feature.goals.data.GoalDisplayPreferences(context)
            assertEquals(setOf("paused"), reopened.snapshot(firstOwner).paused)
            assertEquals(setOf("removed"), reopened.snapshot(firstOwner).removed)
            assertTrue(reopened.snapshot(secondOwner).paused.isEmpty())
            assertTrue(reopened.snapshot(secondOwner).removed.isEmpty())
        } finally {
            context.getSharedPreferences("goal_display", android.content.Context.MODE_PRIVATE).edit()
                .remove("$firstOwner:paused").remove("$firstOwner:removed").commit()
        }
    }

    @Test fun inspectPlanningInBothThemesAndLargeText() {
        val screen = mutableStateOf("debt")
        val dark = mutableStateOf(false)
        val large = mutableStateOf(false)
        val debt = DebtSummary("debt", "owner", DebtObligationType.PAYABLE, "Préstamo para estudios y equipo de trabajo",
            300_000, 200_000, "PEN", LocalDate.now().minusMonths(2), LocalDate.now().plusDays(4), null, null,
            DebtLifecycleStatus.ACTIVE, "SYNCED", 1)
        val installments = (1..6).map { DebtScheduleItem("i$it", it, LocalDate.now().plusMonths((it - 3).toLong()), 50_000,
            if (it < 3) "PAID" else "PENDING") }
        compose.setContent {
            KipuTheme(darkTheme = dark.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 1.5f else 1f)) {
                    key(screen.value, dark.value, large.value) {
                        when (screen.value) {
                            "goal-detail" -> GoalDetailScreen(detail, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {})
                            "completed-detail" -> GoalDetailScreen(detail.copy(goal = goal.copy(status = GoalStatus.COMPLETED, target = event.delta),
                                progress = GoalProgressCalculator.calculate(goal.copy(status = GoalStatus.COMPLETED, target = event.delta), listOf(event))), {}, {}, { _, _, _ -> }, { _, _, _ -> }, {})
                            "celebration" -> GoalAchievedScreen(goal.copy(status = GoalStatus.COMPLETED, target = event.delta), listOf(event), {}, {}, {})
                            "debt" -> DebtDetailScreen(debt, true, {}, {}, {}, installments = installments)
                            "delete" -> GoalDeleteDialog(goal, true, false, {}, {}, {})
                            "complete" -> GoalCompleteDialog(goal, Money(100_000, Currency.PEN), false, {}, {})
                            "contribution" -> GoalContributionSheet(GoalContributionUiState(goal = goal, accounts = listOf(account), isLoading = false), {}, { _, _, _, _ -> })
                            "options" -> GoalListScreen(GoalManagementUiState(goals = listOf(GoalListItemState(goal, detail.progress!!)), isLoading = false), {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "planning-corrections")
        output.mkdirs()
        for (night in listOf(false, true)) for (case in listOf("goal-detail", "completed-detail", "celebration", "debt", "options", "delete", "complete", "contribution")) {
            compose.runOnIdle { screen.value = case; dark.value = night; large.value = false }
            compose.waitForIdle()
            if (case == "options") compose.onNodeWithContentDescription("Opciones de ${goal.name}").performScrollTo().performClick()
            if (case == "contribution") {
                compose.onNodeWithTag("goal_contribution_amount").performTextInput("1200")
                compose.onNodeWithTag("goal_contribution_submit").assertIsNotEnabled()
                compose.onNodeWithTag("goal_contribution_amount").performImeAction()
                compose.onNodeWithText("Saldo insuficiente").performScrollTo().assertIsDisplayed()
            }
            saveScreenshot(File(output, "$case-${if (night) "dark" else "light"}.png"))
            if (case == "options") {
                compose.onNodeWithText("Eliminar meta").performScrollTo().performClick()
                compose.onNodeWithText("Cancelar").performClick()
            }
        }
        compose.runOnIdle { screen.value = "debt"; large.value = true }
        compose.onNodeWithText("Registrar pago").assertIsDisplayed()
        saveScreenshot(File(output, "debt-large-text.png"))
        compose.runOnIdle { screen.value = "goal-detail" }
        compose.onNodeWithText("Reservado").assertIsDisplayed()
        saveScreenshot(File(output, "goal-detail-large-text.png"))
    }

    private fun saveScreenshot(file: File) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue(file.length() > 0)
    }
}
