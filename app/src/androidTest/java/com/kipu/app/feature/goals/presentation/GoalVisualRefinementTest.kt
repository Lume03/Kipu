package com.kipu.app.feature.goals.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.goals.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GoalVisualRefinementTest {
    @get:Rule val compose = createComposeRule()
    private fun goal() = Goal("g", "u", "Laptop", Money(500000, Currency.PEN), iconKey = "laptop", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private fun backing() = GoalBackingAccount("a", "Efectivo", Currency.PEN, Money(20000, Currency.PEN), Money(15000, Currency.PEN), Money(5000, Currency.PEN), Money.zero(Currency.PEN))

    @Test fun embeddedListUsesTheRootCreateActionOnly() {
        compose.setContent { GoalListScreen(GoalManagementUiState(isLoading = false), {}, {}, {}, {}, {}, {}, embedded = true) }
        compose.onNodeWithTag("goal_create_button").assertDoesNotExist()
        compose.onNodeWithText("Todavía no tienes metas", substring = true).assertExists()
    }
    @Test fun cleanFormUsesCalendarWithoutIdeasOrDatePresets() {
        compose.setContent { GoalFormScreen(null, emptyList(), false, null, {}, onSave = { _, _, _, _, _, _, _ -> }) }
        compose.onNodeWithText("Parte de una idea").assertDoesNotExist()
        compose.onNodeWithText("3 meses").assertDoesNotExist()
        compose.onNodeWithTag("goal_icon_picker").performClick()
        compose.onNodeWithTag("goal_icon_laptop").performClick()
        compose.onNodeWithTag("goal_form_date-calendar").performScrollTo().performClick()
        compose.onNodeWithTag("goal_form_date-dialog").assertIsDisplayed()
        compose.onNodeWithTag("goal_form_date-cancel").performClick()
    }
    @Test fun linkedAccountUsesTheSharedSelectorWithRealInstrumentMetadata() {
        val account = Account(AccountId("00000000-0000-0000-0000-000000000001"), UserId("00000000-0000-0000-0000-000000000002"), "Efectivo", AccountType.CASH, Currency.PEN,
            initialBalance = Money.zero(Currency.PEN), openedAt = Instant.EPOCH)
        compose.setContent { GoalFormScreen(null, listOf(backing().copy(id = account.id.value)), false, null, {}, accountOptions = listOf(account), onSave = { _, _, _, _, _, _, _ -> }) }
        compose.onNodeWithTag("goal_form_account").performScrollTo().performClick()
        compose.onNodeWithText("Vincular cuenta de respaldo").assertIsDisplayed()
        compose.onNodeWithText("Efectivo", substring = false).assertExists()
    }
    @Test fun chartShowsAllMonthsAndSwitchesToDaysAndWeeksWithoutLosingTheGoal() {
        compose.setContent { GoalMonthlyContributions(emptyList(), goal(), 0) }
        compose.onNodeWithTag("goal_chart_bucket_11").assertIsDisplayed()
        compose.onNodeWithTag("goal_monthly_suggestion").assertDoesNotExist()
        compose.onNodeWithTag("goal_chart_DAY").performClick()
        compose.onNodeWithTag("goal_chart_bucket_6").assertIsDisplayed()
        compose.onNodeWithTag("goal_chart_bucket_7").assertDoesNotExist()
        compose.onNodeWithTag("goal_chart_WEEK").performClick()
        compose.onNodeWithTag("goal_chart_next").performClick()
        compose.onNodeWithText("Sin aportes en este periodo.").assertIsDisplayed()
    }
    @Test fun withdrawingStaysInTheFundsScreenAndCannotUseOtherGoalsReserves() {
        val event = GoalEvent("e", "u", "g", GoalEventType.ALLOCATION, Money(10000, Currency.PEN), "a", "o", occurredAt = Instant.now())
        val detail = GoalDetailUiState(goal = goal(), events = listOf(event), backingAccounts = listOf(backing()), isLoading = false)
        var releases = 0
        compose.setContent { GoalContributionSheet(GoalContributionUiState(goal = goal(), accounts = listOf(backing()), isLoading = false), {}, { _, _, _, _ -> },
            withdrawalState = detail, onRelease = { _, _, _ -> releases++ }) }
        compose.onNodeWithText("S/ 100").assertDoesNotExist()
        compose.onNodeWithTag("goal_funds_withdraw").performClick()
        compose.onNodeWithTag("goal_contribution_amount").performTextInput("120")
        compose.onNodeWithTag("goal_release_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("goal_contribution_amount").performTextReplacement("25")
        compose.onNodeWithTag("goal_release_confirm").assertIsEnabled()
        compose.onNodeWithTag("goal_contribution_amount").performImeAction()
        compose.onNodeWithTag("goal_funds_contribute").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("goal_backing_account_picker").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, releases) }
    }
    @Test fun datedGoalShowsSuggestionOnlyInMonthlyView() {
        compose.setContent { GoalMonthlyContributions(emptyList(), goal().copy(targetDate = LocalDate.now().plusMonths(2)), 10000) }
        compose.onNodeWithTag("goal_monthly_suggestion").assertExists()
        compose.onNodeWithTag("goal_chart_DAY").performClick()
        compose.onNodeWithTag("goal_monthly_suggestion").assertDoesNotExist()
    }
}
