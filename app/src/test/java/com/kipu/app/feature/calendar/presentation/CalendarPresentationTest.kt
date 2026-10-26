package com.kipu.app.feature.calendar.presentation

import com.kipu.app.feature.calendar.domain.CalendarDay
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventKind
import com.kipu.app.feature.calendar.domain.CalendarEventSource
import com.kipu.app.feature.calendar.domain.CalendarEventStatus
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPresentationTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val october = YearMonth.of(2026, 10)

    private fun event(
        date: LocalDate,
        kind: CalendarEventKind = CalendarEventKind.DEBT_INSTALLMENT,
        status: CalendarEventStatus = CalendarEventStatus.PLANNED,
    ) = CalendarEvent(
        id = "$kind:$date", date = date, kind = kind, status = status, title = "Evento",
        source = CalendarEventSource.Debt("d"),
    )

    private fun day(dayOfMonth: Int) = october.atDay(dayOfMonth).let { CalendarDay(it, listOf(event(it))) }

    @Test
    fun `solo las cuotas o deudas pendientes con fecha pasada se muestran vencidas`() {
        val past = today.minusDays(4)
        assertEquals(CalendarDisplayStatus.OVERDUE, displayStatus(event(past), today))
        assertEquals(CalendarDisplayStatus.OVERDUE, displayStatus(event(past, CalendarEventKind.DEBT_DUE), today))
        assertEquals(CalendarDisplayStatus.PLANNED, displayStatus(event(past, CalendarEventKind.CARD_DUE), today))
        assertEquals(CalendarDisplayStatus.PLANNED, displayStatus(event(today), today))
        assertEquals(
            CalendarDisplayStatus.REALIZED,
            displayStatus(event(past, CalendarEventKind.MOVEMENT, CalendarEventStatus.REALIZED), today),
        )
    }

    @Test
    fun `el filtro separa previstos y realizados`() {
        val planned = event(today)
        val realized = event(today, CalendarEventKind.MOVEMENT, CalendarEventStatus.REALIZED)
        assertTrue(CalendarFilter.ALL.matches(planned) && CalendarFilter.ALL.matches(realized))
        assertTrue(CalendarFilter.PLANNED.matches(planned))
        assertFalse(CalendarFilter.PLANNED.matches(realized))
        assertTrue(CalendarFilter.REALIZED.matches(realized))
        assertFalse(CalendarFilter.REALIZED.matches(planned))
    }

    @Test
    fun `dia por defecto en el mes actual prioriza hoy y luego el proximo evento`() {
        assertEquals(october.atDay(24), defaultSelectedDate(october, today, listOf(day(3), day(24), day(28))))
        assertEquals(october.atDay(28), defaultSelectedDate(october, today, listOf(day(3), day(28))))
        assertEquals(october.atDay(15), defaultSelectedDate(october, today, listOf(day(3), day(15))))
        assertEquals(today, defaultSelectedDate(october, today, emptyList()))
    }

    @Test
    fun `dia por defecto en otros meses es el primer evento o el dia 1`() {
        val september = YearMonth.of(2026, 9)
        val days = listOf(CalendarDay(september.atDay(12), listOf(event(september.atDay(12)))))
        assertEquals(september.atDay(12), defaultSelectedDate(september, today, days))
        assertEquals(YearMonth.of(2026, 12).atDay(1), defaultSelectedDate(YearMonth.of(2026, 12), today, emptyList()))
    }

    @Test
    fun `subtitulo distingue mes pasado actual y futuro`() {
        assertEquals("Mes pasado · gratis", monthSubtitle(YearMonth.of(2026, 9), today))
        assertEquals("Mes actual", monthSubtitle(october, today))
        assertEquals("Mes futuro", monthSubtitle(YearMonth.of(2026, 11), today))
    }

    @Test
    fun `cada fuente tiene su accion de apertura`() {
        assertEquals("Abrir deuda", openSourceLabel(event(today)))
        assertEquals("Abrir tarjeta", openSourceLabel(event(today).copy(source = CalendarEventSource.Card("c"))))
        assertEquals("Abrir movimiento", openSourceLabel(event(today).copy(source = CalendarEventSource.Movement("t"))))
        assertEquals("Ver movimientos", openSourceLabel(event(today).copy(source = CalendarEventSource.Recurrence("r"))))
        assertEquals("2 eventos · agrupados en el mismo día", eventCountLabel(2))
    }
}
