package com.kipu.app.feature.calendar.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.calendar.domain.CalendarDay
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventKind
import com.kipu.app.feature.calendar.domain.CalendarEventSource
import com.kipu.app.feature.calendar.domain.CalendarEventStatus
import com.kipu.app.feature.calendar.domain.CalendarMonth
import com.kipu.app.feature.home.domain.CurrencyNetWorth
import com.kipu.app.feature.home.presentation.NetWorthCard
import com.kipu.app.feature.home.presentation.NetWorthUiState
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarAndHomeAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 15)

    private fun event(id: String, status: CalendarEventStatus, available: Boolean = true) = CalendarEvent(
        id = id, date = day, kind = CalendarEventKind.CARD_DUE, status = status, title = "Evento $id",
        amount = Money(12_345L, Currency.PEN), source = CalendarEventSource.Card(id), isSourceAvailable = available,
    )

    private fun loaded(vararg events: CalendarEvent) = CalendarUiState(
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 24),
        content = CalendarContent.Loaded(CalendarMonth(YearMonth.of(2026, 10), listOf(CalendarDay(day, events.toList())))),
    )

    private fun show(state: CalendarUiState, onEvent: (CalendarEvent) -> Unit = {}, onRetry: () -> Unit = {}) {
        compose.setContent {
            KipuTheme {
                CalendarScreen(
                    state = state, onPreviousMonth = {}, onNextMonth = {}, onToday = {}, onRetry = onRetry,
                    onEventClick = onEvent, onNavigateBack = {},
                )
            }
        }
    }

    @Test
    fun eventsOnSameDayAreListedSeparatelyWithTextualStatus() {
        show(loaded(event("a", CalendarEventStatus.REALIZED), event("b", CalendarEventStatus.PLANNED)))

        compose.onNodeWithTag(CALENDAR_EVENT_TAG_PREFIX + "a").assertTextContains("Realizado", substring = true)
        compose.onNodeWithTag(CALENDAR_EVENT_TAG_PREFIX + "b").assertTextContains("Previsto", substring = true)
    }

    @Test
    fun availableEventNavigatesAndUnavailableDoesNot() {
        val clicked = mutableListOf<String>()
        show(
            loaded(event("ok", CalendarEventStatus.PLANNED), event("gone", CalendarEventStatus.PLANNED, available = false)),
            onEvent = { clicked += it.id },
        )

        compose.onNodeWithTag(CALENDAR_EVENT_TAG_PREFIX + "ok").assertHasClickAction().performClick()
        compose.onNodeWithTag(CALENDAR_EVENT_TAG_PREFIX + "gone").assertTextContains("No disponible", substring = true)
        compose.onNodeWithTag(CALENDAR_EVENT_TAG_PREFIX + "gone").assertIsNotEnabled()

        assertEquals(listOf("ok"), clicked)
    }

    @Test
    fun errorStateOffersRetry() {
        var retries = 0
        show(CalendarUiState(YearMonth.of(2026, 10), day, CalendarContent.Error("No se pudo cargar el calendario.")), onRetry = { retries++ })

        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun emptyMonthShowsEmptyState() {
        show(CalendarUiState(YearMonth.of(2026, 10), day, CalendarContent.Empty))

        compose.onNodeWithText("Sin eventos en Octubre 2026").assertTextContains("Sin eventos", substring = true)
    }

    @Test
    fun netWorthCardHidesAmountsWhenMasked() {
        val money = { units: Long -> Money(units * 100, Currency.PEN) }
        val state = NetWorthUiState.Content(listOf(CurrencyNetWorth(Currency.PEN, money(1000), money(200), money(300))))
        compose.setContent {
            KipuTheme {
                CompositionLocalProvider(LocalBalanceMasked provides true) { NetWorthCard(state = state, onRetry = {}) }
            }
        }

        compose.onNodeWithTag("home_net_worth_card").assertExists()
        assertTrue(compose.onAllNodesWithTextCount("900.00") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
}
