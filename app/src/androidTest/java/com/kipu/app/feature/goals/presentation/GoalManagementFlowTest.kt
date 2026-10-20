package com.kipu.app.feature.goals.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GoalManagementFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun createsVirtualGoalWithoutChangingAnyAccount() {
        var savedGoalId: String? = "unset"
        var savedTarget: Money? = null
        var linkedAccountId: String? = "unset"
        compose.setContent {
            GoalFormScreen(
                existingGoal = null,
                accounts = emptyList(),
                isSaving = false,
                errorMessage = null,
                onNavigateBack = {},
                onSave = { id, _, target, _, accountId, _ ->
                    savedGoalId = id
                    savedTarget = target
                    linkedAccountId = accountId
                },
            )
        }

        compose.onNodeWithText("Sin cuenta vinculada · meta virtual").assertIsDisplayed()
        compose.onNodeWithTag("goal_form_name").performTextInput("Fondo de viaje")
        compose.onNodeWithTag("goal_form_target").performTextInput("120.50")
        compose.onNodeWithTag("goal_save_button").performScrollTo().performClick()

        compose.runOnIdle {
            assertNull(savedGoalId)
            assertEquals(Money(12_050L, Currency.PEN), savedTarget)
            assertNull(linkedAccountId)
        }
    }

    @Test
    fun freePlanShowsItsTwoGoalLimitAndHidesCreateActionAtQuota() {
        val uiState = state(goals = listOf(goal("goal-1", GoalStatus.ACTIVE), goal("goal-2", GoalStatus.ACTIVE)))
        compose.setContent {
            GoalListScreen(uiState, {}, {}, {}, {}, {}, {})
        }

        compose.onNodeWithText("Plan Free: 2 de 2 metas activas.").assertIsDisplayed()
        compose.onNodeWithText("Ver planes para crear más metas").assertIsDisplayed()
        compose.onNodeWithTag("goal_create_button").assertDoesNotExist()
    }

    @Test
    fun completedGoalDoesNotConsumeFreePlanSlot() {
        val uiState = state(goals = listOf(goal("goal-active", GoalStatus.ACTIVE), goal("goal-completed", GoalStatus.COMPLETED)))
        compose.setContent { GoalListScreen(uiState, {}, {}, {}, {}, {}, {}) }

        compose.onNodeWithText("Plan Free: 1 de 2 metas activas.").assertIsDisplayed()
        compose.onNodeWithTag("goal_create_button").assertIsDisplayed()
    }

    @Test
    fun freePlanNeverAllowsMoreThanTwoActiveGoals() {
        val uiState = state(
            goals = listOf(
                goal("goal-1", GoalStatus.ACTIVE),
                goal("goal-2", GoalStatus.ACTIVE),
                goal("goal-3", GoalStatus.ACTIVE),
            ),
        )
        compose.setContent { GoalListScreen(uiState, {}, {}, {}, {}, {}, {}) }

        compose.onNodeWithText("Plan Free: 2 de 2 metas activas.").assertIsDisplayed()
        compose.onNodeWithTag("goal_create_button").assertDoesNotExist()
    }


    @Test
    fun editingVirtualGoalPreservesOptionalAccountAndDoesNotCreateContribution() {
        var saved: Triple<String?, Money, String?>? = null
        val original = goal("goal-edit", GoalStatus.ACTIVE)
        compose.setContent {
            GoalFormScreen(
                existingGoal = original,
                accounts = emptyList(),
                isSaving = false,
                errorMessage = null,
                onNavigateBack = {},
                onSave = { id, _, target, _, accountId, _ -> saved = Triple(id, target, accountId) },
            )
        }

        compose.onNodeWithTag("goal_form_name").performTextReplacement("Meta editada")
        compose.onNodeWithTag("goal_form_target").performTextReplacement("150.00")
        compose.onNodeWithTag("goal_save_button").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Triple("goal-edit", Money(15_000L, Currency.PEN), null), saved)
        }
    }

    @Test
    fun completedGoalThatRemainsCompletedDoesNotAskForReplacement() {
        var replacement: String? = "not-saved"
        val completed = goal("goal-completed", GoalStatus.COMPLETED)
        val candidates = listOf(goal("active-1", GoalStatus.ACTIVE), goal("active-2", GoalStatus.ACTIVE))
        compose.setContent {
            GoalFormScreen(
                existingGoal = completed,
                accounts = emptyList(),
                replacementCandidates = candidates,
                currentReserved = Money(10_000L, Currency.PEN),
                isSaving = false,
                errorMessage = null,
                onNavigateBack = {},
                onSave = { _, _, _, _, _, replacementId -> replacement = replacementId },
            )
        }

        compose.onNodeWithTag("goal_form_name").performTextReplacement("Renombrada")
        compose.onNodeWithTag("goal_save_button").performScrollTo().performClick()
        compose.onAllNodesWithText("Elige una meta para pausar").assertCountEquals(0)
        compose.runOnIdle { assertNull(replacement) }
    }

    @Test
    fun reactivationShowsReplacementChoiceAndCancelDoesNotSave() {
        var saveCount = 0
        val completed = goal("goal-completed", GoalStatus.COMPLETED)
        val candidates = listOf(goal("active-1", GoalStatus.ACTIVE), goal("active-2", GoalStatus.ACTIVE))
        compose.setContent {
            GoalFormScreen(
                existingGoal = completed,
                accounts = emptyList(),
                replacementCandidates = candidates,
                currentReserved = Money(10_000L, Currency.PEN),
                isSaving = false,
                errorMessage = null,
                onNavigateBack = {},
                onSave = { _, _, _, _, _, _ -> saveCount++ },
            )
        }

        compose.onNodeWithTag("goal_form_target").performTextReplacement("150.00")
        compose.onNodeWithTag("goal_save_button").performScrollTo().performClick()
        compose.onNodeWithText("Elige una meta para pausar").assertIsDisplayed()
        compose.onNodeWithText("active-1").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onAllNodesWithText("Elige una meta para pausar").assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, saveCount) }
    }

    @Test
    fun confirmingReplacementPassesSelectedGoalToSingleSaveCallback() {
        var savedReplacement: String? = null
        val completed = goal("goal-completed", GoalStatus.COMPLETED)
        val candidates = listOf(goal("active-1", GoalStatus.ACTIVE), goal("active-2", GoalStatus.ACTIVE))
        compose.setContent {
            GoalFormScreen(
                existingGoal = completed,
                accounts = emptyList(),
                replacementCandidates = candidates,
                currentReserved = Money(10_000L, Currency.PEN),
                isSaving = false,
                errorMessage = null,
                onNavigateBack = {},
                onSave = { _, _, _, _, _, replacementId -> savedReplacement = replacementId },
            )
        }

        compose.onNodeWithTag("goal_form_target").performTextReplacement("150.00")
        compose.onNodeWithTag("goal_save_button").performScrollTo().performClick()
        compose.onNodeWithTag("replacement_radio_active-2").performClick()
        compose.onNodeWithText("Confirmar").performClick()
        compose.runOnIdle { assertEquals("active-2", savedReplacement) }
    }

    @Test
    fun premiumReactivationDoesNotAskForFreePlanReplacement() {
        var saveCount = 0
        val completed = goal("goal-completed", GoalStatus.COMPLETED)
        val candidates = listOf(goal("active-1", GoalStatus.ACTIVE), goal("active-2", GoalStatus.ACTIVE))
        compose.setContent {
            GoalFormScreen(
                existingGoal = completed,
                accounts = emptyList(),
                replacementCandidates = candidates,
                currentReserved = Money(10_000L, Currency.PEN),
                hasPremiumAccess = true,
                isSaving = false,
                errorMessage = null,
                onNavigateBack = {},
                onSave = { _, _, _, _, _, replacementId ->
                    if (replacementId == null) saveCount++
                },
            )
        }

        compose.onNodeWithTag("goal_form_target").performTextReplacement("150.00")
        compose.onNodeWithTag("goal_save_button").performScrollTo().performClick()
        compose.onAllNodesWithText("Elige una meta para pausar").assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, saveCount) }
    }

    private fun goal(id: String, status: GoalStatus) = Goal(
        id = id,
        userId = "user-1",
        name = id,
        target = Money(10_000L, Currency.PEN),
        status = status,
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )

    private fun state(goals: List<Goal>) = GoalManagementUiState(
        goals = goals.map { goal -> GoalListItemState(goal, GoalProgressCalculator.calculate(goal, emptyList())) },
        isLoading = false,
        hasPremiumAccess = false,
    )
}
