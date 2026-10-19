package com.kipu.app.feature.goals.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class GoalLifecycleFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun completedGoalRetainsItsReserveAndCannotBeArchivedUntilResolved() {
        val goal = goal("goal-1", "Casa", GoalStatus.COMPLETED, 20_000L)
        val events = listOf(event(goal.id, "account", 20_000L))
        val state = detailState(goal, events, listOf(account("account", 20_000L, 0L)))
        compose.setContent { GoalDetailScreen(state, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {}) }

        compose.onNodeWithText("Completaste el objetivo. La reserva permanece intacta hasta que decidas liberarla o reasignarla.").assertIsDisplayed()
        compose.onNodeWithTag("goal_archive").assertIsNotEnabled()
        compose.onNodeWithText("Historial de la meta").assertIsDisplayed()
    }

    @Test
    fun releaseIsAnExplicitActionWithAnAmount() {
        val goal = goal("goal-1", "Viaje", GoalStatus.ACTIVE, 100_000L)
        val events = listOf(event(goal.id, "account", 50_000L))
        var released: Pair<String, Money>? = null
        compose.setContent {
            GoalDetailScreen(
                detailState(goal, events, listOf(account("account", 100_000L, 50_000L))),
                {}, {}, { id, amount, _ -> released = id to amount }, { _, _, _ -> }, {},
            )
        }

        compose.onNodeWithTag("goal_release").performClick()
        compose.onNodeWithTag("goal_detail_amount").performTextInput("25.00")
        compose.onNodeWithTag("goal_release_confirm").performClick()
        compose.runOnIdle { assertEquals("account" to Money(2_500L, Currency.PEN), released) }
    }

    @Test
    fun releaseOfCompletedGoalAtQuotaShowsReplacementSelectorForTwoActiveGoals() {
        val completed = goal("goal-completed", "Viaje", GoalStatus.COMPLETED, 100_000L)
        val activeOne = goal("goal-one", "Meta uno", GoalStatus.ACTIVE, 40_000L)
        val activeTwo = goal("goal-two", "Meta dos", GoalStatus.ACTIVE, 40_000L)
        var releasedWithReplacement: Triple<String, Money, String?>? = null
        compose.setContent {
            GoalDetailScreen(
                detailState(completed, listOf(event(completed.id, "account", 100_000L)), listOf(account("account", 200_000L, 100_000L)), listOf(activeOne, activeTwo)),
                {}, {}, { accountId, amount, replacementId -> releasedWithReplacement = Triple(accountId, amount, replacementId) }, { _, _, _ -> }, {},
            )
        }

        compose.onNodeWithTag("goal_release").performClick()
        compose.onNodeWithTag("goal_detail_amount").performTextInput("20.00")
        compose.onNodeWithTag("goal_release_replacement_picker").assertIsDisplayed()
        compose.onNodeWithTag("goal_release_replacement_picker").performClick()
        compose.onNodeWithText("Meta uno").assertIsDisplayed()
        compose.onNodeWithText("Meta dos").assertIsDisplayed()
        compose.onNodeWithText("Meta uno").performClick()
        compose.onNodeWithTag("goal_release_confirm").performClick()
        compose.runOnIdle {
            assertEquals(Triple("account", Money(2_000L, Currency.PEN), "goal-one"), releasedWithReplacement)
        }
    }

    @Test
    fun selectingReleaseReplacementThenCancellingDispatchesNoCommand() {
        val completed = goal("goal-completed", "Viaje", GoalStatus.COMPLETED, 100_000L)
        val activeOne = goal("goal-one", "Meta uno", GoalStatus.ACTIVE, 40_000L)
        val activeTwo = goal("goal-two", "Meta dos", GoalStatus.ACTIVE, 40_000L)
        var releaseCalls = 0
        var reassignmentCalls = 0
        compose.setContent {
            GoalDetailScreen(
                detailState(
                    completed,
                    listOf(event(completed.id, "account", 100_000L)),
                    listOf(account("account", 200_000L, 100_000L)),
                    listOf(activeOne, activeTwo),
                ),
                {}, {}, { _, _, _ -> releaseCalls++ }, { _, _, _ -> reassignmentCalls++ }, {},
            )
        }

        compose.onNodeWithTag("goal_release").performClick()
        compose.onNodeWithTag("goal_detail_amount").performTextInput("20.00")
        compose.onNodeWithTag("goal_release_replacement_picker").performClick()
        compose.onNodeWithText("Meta uno").performClick()
        compose.onNodeWithTag("goal_release_cancel").performClick()

        compose.runOnIdle {
            assertEquals("cancelling replacement selection must not dispatch RELEASE_GOAL", 0, releaseCalls)
            assertEquals("cancelling replacement selection must not dispatch another goal command", 0, reassignmentCalls)
        }
    }

    @Test
    fun releaseWithFreeSlotDoesNotShowReplacementSelectorAndCancelDoesNotRelease() {
        val completed = goal("goal-completed", "Viaje", GoalStatus.COMPLETED, 100_000L)
        var released = false
        compose.setContent {
            GoalDetailScreen(
                detailState(completed, listOf(event(completed.id, "account", 50_000L)), listOf(account("account", 100_000L, 50_000L))),
                {}, {}, { _, _, _ -> released = true }, { _, _, _ -> }, {},
            )
        }

        compose.onNodeWithTag("goal_release").performClick()
        compose.onAllNodesWithTag("goal_release_replacement_picker").assertCountEquals(0)
        compose.onNodeWithTag("goal_detail_amount").performTextInput("25.00")
        compose.onNodeWithTag("goal_release_cancel").performClick()
        compose.runOnIdle { assertFalse(released) }
    }

    @Test
    fun reassignmentKeepsTheSameBackingAccount() {
        val goal = goal("goal-1", "Viaje", GoalStatus.ACTIVE, 100_000L)
        val destination = goal("goal-2", "Emergencia", GoalStatus.ACTIVE, 50_000L)
        val events = listOf(event(goal.id, "account", 30_000L))
        var reassigned: Triple<String, String, Money>? = null
        compose.setContent {
            GoalDetailScreen(
                detailState(goal, events, listOf(account("account", 100_000L, 70_000L)), listOf(destination)),
                {}, {}, { _, _, _ -> }, { to, backing, amount -> reassigned = Triple(to, backing, amount) }, {},
            )
        }

        compose.onNodeWithTag("goal_reassign").performClick()
        compose.onNodeWithTag("goal_reassign_destination_picker").performClick()
        compose.onNodeWithText("Emergencia").performClick()
        compose.onNodeWithTag("goal_detail_amount").performTextInput("20.00")
        compose.onNodeWithTag("goal_reassign_confirm").performClick()
        compose.runOnIdle { assertEquals(Triple("goal-2", "account", Money(2_000L, Currency.PEN)), reassigned) }
    }

    @Test
    fun archiveCanBeCancelledAndThenConfirmedWithoutReleasingFunds() {
        val goal = goal("goal-1", "Meta vacía", GoalStatus.ACTIVE, 25_000L)
        var archived = false
        compose.setContent {
            GoalDetailScreen(detailState(goal, emptyList(), emptyList()), {}, {}, { _, _, _ -> }, { _, _, _ -> }, { archived = true })
        }

        compose.onNodeWithTag("goal_archive").performClick()
        compose.onNodeWithTag("goal_archive_cancel").performClick()
        compose.runOnIdle { assertFalse(archived) }
        compose.onNodeWithTag("goal_archive").performClick()
        compose.onNodeWithTag("goal_archive_confirm").performClick()
        compose.runOnIdle { assertEquals(true, archived) }
    }

    private fun detailState(
        goal: Goal,
        events: List<GoalEvent>,
        accounts: List<GoalBackingAccount>,
        otherGoals: List<Goal> = emptyList(),
    ) = GoalDetailUiState(
        userId = goal.userId,
        goal = goal,
        progress = GoalProgressCalculator.calculate(goal, events),
        events = events,
        backingAccounts = accounts,
        otherGoals = otherGoals,
        isLoading = false,
    )

    private fun goal(id: String, name: String, status: GoalStatus, target: Long) = Goal(
        id = id,
        userId = "user-1",
        name = name,
        target = Money(target, Currency.PEN),
        status = status,
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )

    private fun event(goalId: String, accountId: String, amount: Long) = GoalEvent(
        id = "event-$goalId",
        userId = "user-1",
        goalId = goalId,
        type = GoalEventType.ALLOCATION,
        delta = Money(amount, Currency.PEN),
        backingAccountId = accountId,
        operationId = "operation-$goalId",
        occurredAt = Instant.parse("2026-10-17T10:00:00Z"),
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
    )

    private fun account(id: String, confirmed: Long, available: Long) = GoalBackingAccount(
        id = id,
        name = "Ahorros",
        currency = Currency.PEN,
        confirmedBalance = Money(confirmed, Currency.PEN),
        reserved = Money(confirmed - available, Currency.PEN),
        available = Money(available, Currency.PEN),
        shortfall = Money.zero(Currency.PEN),
    )
}
