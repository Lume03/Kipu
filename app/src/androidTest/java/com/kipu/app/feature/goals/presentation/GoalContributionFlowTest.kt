package com.kipu.app.feature.goals.presentation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoalContributionFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reservesAgainstBackingAccountWithoutRequestingMovement() {
        var reserved: Triple<Money, String, Boolean>? = null
        compose.setContent {
            GoalContributionSheet(
                state = state(destinationAvailable = 80_000L),
                onNavigateBack = {},
                onReserve = { amount, account, transfer, _ -> reserved = Triple(amount, account, transfer) },
            )
        }

        compose.onNodeWithTag("goal_contribution_amount").performTextInput("200.00")
        compose.onNodeWithTag("goal_contribution_submit").performClick()
        compose.runOnIdle {
            assertEquals(Triple(Money(20_000L, Currency.PEN), "destination", false), reserved)
        }
    }

    @Test
    fun internalTransferRequiresExplicitConfirmationAndSourceSelection() {
        var transfer: Pair<String, Boolean>? = null
        compose.setContent {
            GoalContributionSheet(
                state = state(destinationAvailable = 0L, includeSource = true),
                onNavigateBack = {},
                onReserve = { _, account, confirmed, source -> transfer = (source.orEmpty() + "->" + account) to confirmed },
            )
        }

        compose.onNodeWithTag("goal_contribution_amount").performTextInput("100.00")
        compose.onNodeWithTag("goal_contribution_amount").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag("goal_transfer_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("goal_transfer_source_picker").performScrollTo().performClick()
        compose.onNodeWithText("Origen · libre 500.00").performClick()
        compose.onNodeWithTag("goal_contribution_submit").performClick()

        compose.runOnIdle {
            assertEquals("source->destination" to true, transfer)
        }
    }

    @Test
    fun insufficientBackingRejectsTheWholeAmount() {
        var called = false
        compose.setContent {
            GoalContributionSheet(
                state = state(destinationAvailable = 80_000L),
                onNavigateBack = {},
                onReserve = { _, _, _, _ -> called = true },
            )
        }
        compose.onNodeWithTag("goal_contribution_amount").performTextInput("900.00")
        compose.onNodeWithTag("goal_contribution_submit").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(called) }
    }

    @Test
    fun displaysConflictFeedbackWithoutSubmittingAgain() {
        compose.setContent {
            GoalContributionSheet(
                state = state(destinationAvailable = 80_000L).copy(
                    errorMessage = "La meta o la cuenta cambió. Revisa los saldos antes de reintentar.",
                ),
                onNavigateBack = {},
                onReserve = { _, _, _, _ -> },
            )
        }

        compose.onNodeWithText("La meta o la cuenta cambió. Revisa los saldos antes de reintentar.").assertExists()
    }

    private fun state(destinationAvailable: Long, includeSource: Boolean = false) = GoalContributionUiState(
        userId = "user-1",
        goal = goal("destination"),
        accounts = buildList {
            add(backingAccount("destination", "Destino", destinationAvailable))
            if (includeSource) add(backingAccount("source", "Origen", 50_000L))
        },
        isLoading = false,
    )

    private fun goal(linkedAccountId: String?) = Goal(
        id = "goal-1",
        userId = "user-1",
        name = "Fondo",
        target = Money(100_000L, Currency.PEN),
        linkedAccountId = linkedAccountId,
        status = GoalStatus.ACTIVE,
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )

    private fun backingAccount(id: String, name: String, available: Long) = GoalBackingAccount(
        id = id,
        name = name,
        currency = Currency.PEN,
        confirmedBalance = Money(available, Currency.PEN),
        reserved = Money.zero(Currency.PEN),
        available = Money(available, Currency.PEN),
        shortfall = Money.zero(Currency.PEN),
    )
}
