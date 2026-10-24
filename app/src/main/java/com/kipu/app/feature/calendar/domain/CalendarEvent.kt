package com.kipu.app.feature.calendar.domain

import com.kipu.app.core.finance.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Evento de lectura del calendario financiero (HU-40). Es una proyección efímera sobre
 * fuentes existentes: nunca se persiste, no es un movimiento ni dispara pagos.
 */
enum class CalendarEventKind { MOVEMENT, CARD_CLOSING, CARD_DUE, DEBT_INSTALLMENT, DEBT_DUE, RECURRENCE }

/** REALIZED: hecho financiero ya registrado. PLANNED: fecha prevista o esperada. */
enum class CalendarEventStatus { REALIZED, PLANNED }

/** Referencia al objeto fuente; cada variante se corresponde con una ruta de navegación existente. */
sealed interface CalendarEventSource {
    data class Movement(val transactionId: String) : CalendarEventSource
    data class Card(val cardId: String) : CalendarEventSource
    data class Debt(val debtId: String) : CalendarEventSource
    data class Recurrence(val recurrenceId: String) : CalendarEventSource
}

data class CalendarEvent(
    /** Identificador de presentación, único por evento (dos eventos del mismo día no se fusionan). */
    val id: String,
    val date: LocalDate,
    val kind: CalendarEventKind,
    val status: CalendarEventStatus,
    val title: String,
    val amount: Money? = null,
    val source: CalendarEventSource,
    /** False cuando el objeto fuente ya no existe: se muestra como no disponible y no navega. */
    val isSourceAvailable: Boolean = true,
)

data class CalendarDay(val date: LocalDate, val events: List<CalendarEvent>)

data class CalendarMonth(val month: YearMonth, val days: List<CalendarDay>) {
    val isEmpty: Boolean get() = days.isEmpty()
}
