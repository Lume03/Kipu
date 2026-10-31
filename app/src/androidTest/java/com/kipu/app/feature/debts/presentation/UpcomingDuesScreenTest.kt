package com.kipu.app.feature.debts.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpcomingDuesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-15")
    private fun row(id: String, offset: Long, status: DueStatus, origin: DueOrigin = DueOrigin.DEBT) = UpcomingDue(
        id, origin, id, "Vencimiento $id", null, today.plusDays(offset), Money(if (status == DueStatus.PAID) 0 else 1000, Currency.PEN),
        Money(1000, Currency.PEN), status, DebtObligationType.PAYABLE, canSettle = origin == DueOrigin.DEBT && status != DueStatus.PAID)
    private fun render(rows: List<UpcomingDue>, openDebt: (String) -> Unit = {}, openCard: (String) -> Unit = {}, settle: (String) -> Unit = {}) {
        compose.setContent { KipuTheme {
            UpcomingDuesScreen(UpcomingDuesUiState(rows = rows, periods = rows.groupBy { it.period(today) }, isLoading = false, today = today),
                {}, {}, {}, {}, {}, openDebt, openCard, settle, {})
        } }
    }
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex)).performScrollToNode(matcher)
    }
    @Test fun allCalendarSectionsAndPaidExpansionAreReachable() {
        val rows = listOf(row("old", -1, DueStatus.OVERDUE), row("week", 7, DueStatus.UPCOMING, DueOrigin.CARD),
            row("month", 8, DueStatus.PENDING), row("later", 35, DueStatus.PENDING), row("paid", -2, DueStatus.PAID))
        render(rows)
        for (period in DuePeriod.entries.filter { it != DuePeriod.RECENTLY_PAID }) {
            scrollTo(hasText(period.label.uppercase()))
            compose.onNodeWithText(period.label.uppercase()).assertIsDisplayed()
        }
        scrollTo(hasText("Pagadas recientemente (1) · últimos 30 días"))
        compose.onNodeWithText("Pagadas recientemente (1) · últimos 30 días").performClick()
        scrollTo(hasTestTag("upcoming_due_paid"))
        compose.onNodeWithTag("upcoming_due_paid").assertIsDisplayed()
    }
    @Test fun debtAndCardOpenTheirOwnDestinationsAndOnlyDebtOffersSettlement() {
        var debt = ""; var card = ""; var settled = ""
        render(listOf(row("debt", 1, DueStatus.UPCOMING), row("card", 2, DueStatus.UPCOMING, DueOrigin.CARD)),
            { debt = it }, { card = it }, { settled = it })
        scrollTo(hasTestTag("upcoming_due_debt"))
        compose.onNodeWithTag("upcoming_due_debt").performClick()
        compose.onNodeWithText("Registrar pago").performScrollTo().performClick()
        scrollTo(hasTestTag("upcoming_due_card"))
        compose.onNodeWithTag("upcoming_due_card").performClick()
        compose.runOnIdle { assertEquals("debt", debt); assertEquals("card", card); assertEquals("debt", settled) }
    }
    @Test fun emptyCalendarShowsItsOwnScreenAndSelectionMessage() {
        render(emptyList())
        compose.onNodeWithTag("upcoming_dues_screen").assertIsDisplayed()
        scrollTo(hasTestTag("upcoming_dues_empty"))
        compose.onNodeWithText("Todo al día en esta selección").assertIsDisplayed()
    }
}
