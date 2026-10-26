package com.kipu.app.feature.calendar.presentation

import com.kipu.app.feature.calendar.domain.CalendarDay
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventKind
import com.kipu.app.feature.calendar.domain.CalendarEventSource
import com.kipu.app.feature.calendar.domain.CalendarEventStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Reglas de presentación del calendario (sin efectos): estado visible, filtro, día seleccionado
 * por defecto y textos por tipo de evento. No cambian qué eventos produce el dominio.
 */

/** Estado mostrado: "Vencido" solo para cuotas o deudas pendientes cuya fecha ya pasó. */
enum class CalendarDisplayStatus(val label: String) { REALIZED("Realizado"), PLANNED("Previsto"), OVERDUE("Vencido") }

internal fun displayStatus(event: CalendarEvent, today: LocalDate): CalendarDisplayStatus = when {
    event.status == CalendarEventStatus.REALIZED -> CalendarDisplayStatus.REALIZED
    event.date.isBefore(today) &&
        (event.kind == CalendarEventKind.DEBT_INSTALLMENT || event.kind == CalendarEventKind.DEBT_DUE) ->
        CalendarDisplayStatus.OVERDUE
    else -> CalendarDisplayStatus.PLANNED
}

enum class CalendarFilter(val label: String) { ALL("Todos"), PLANNED("Previstos"), REALIZED("Realizados") }

internal fun CalendarFilter.matches(event: CalendarEvent): Boolean = when (this) {
    CalendarFilter.ALL -> true
    CalendarFilter.PLANNED -> event.status == CalendarEventStatus.PLANNED
    CalendarFilter.REALIZED -> event.status == CalendarEventStatus.REALIZED
}

internal enum class MonthPosition { PAST, CURRENT, FUTURE }

internal fun monthPosition(month: YearMonth, today: LocalDate): MonthPosition {
    val current = YearMonth.from(today)
    return when {
        month.isBefore(current) -> MonthPosition.PAST
        month.isAfter(current) -> MonthPosition.FUTURE
        else -> MonthPosition.CURRENT
    }
}

internal fun monthSubtitle(month: YearMonth, today: LocalDate): String = when (monthPosition(month, today)) {
    MonthPosition.PAST -> "Mes pasado · gratis"
    MonthPosition.CURRENT -> "Mes actual"
    MonthPosition.FUTURE -> "Mes futuro"
}

/**
 * Mes actual: hoy si tiene eventos; si no, el próximo día con eventos; si no, el último pasado.
 * Otros meses: el primer día con eventos, o el día 1 si no hay ninguno.
 */
internal fun defaultSelectedDate(month: YearMonth, today: LocalDate, days: List<CalendarDay>): LocalDate {
    val dates = days.filter { it.events.isNotEmpty() }.map { it.date }.sorted()
    return if (YearMonth.from(today) == month) {
        dates.firstOrNull { it == today }
            ?: dates.firstOrNull { it.isAfter(today) }
            ?: dates.lastOrNull()
            ?: today
    } else {
        dates.firstOrNull() ?: month.atDay(1)
    }
}

internal fun eventSubtitle(event: CalendarEvent): String = when (event.kind) {
    CalendarEventKind.MOVEMENT -> when (event.moneyDirection) {
        MovementType.INCOME -> "Ingreso"
        MovementType.EXPENSE -> "Gasto"
        MovementType.TRANSFER -> "Transferencia"
        null -> "Movimiento"
    }
    CalendarEventKind.CARD_CLOSING -> "Cierre de facturación"
    CalendarEventKind.CARD_DUE -> "Sin cargo automático"
    CalendarEventKind.DEBT_INSTALLMENT -> "Cuota de deuda"
    CalendarEventKind.DEBT_DUE -> "Saldo pendiente"
    CalendarEventKind.RECURRENCE -> "Recurrente · sin cargo automático"
}

internal fun eventOrigin(event: CalendarEvent, status: CalendarDisplayStatus): String = when (event.kind) {
    CalendarEventKind.MOVEMENT -> "Movimiento registrado"
    CalendarEventKind.CARD_CLOSING -> "Tarjeta · cierre"
    CalendarEventKind.CARD_DUE -> "Tarjeta · vencimiento"
    CalendarEventKind.DEBT_INSTALLMENT ->
        if (status == CalendarDisplayStatus.OVERDUE) "Deuda · cuota pendiente" else "Deuda · próxima cuota"
    CalendarEventKind.DEBT_DUE -> "Deuda · vencimiento"
    CalendarEventKind.RECURRENCE -> "Recurrencia"
}

/** Texto del botón que abre el objeto fuente; coincide con la ruta de CalendarNavigation. */
internal fun openSourceLabel(event: CalendarEvent): String = when (event.source) {
    is CalendarEventSource.Movement -> "Abrir movimiento"
    is CalendarEventSource.Card -> "Abrir tarjeta"
    is CalendarEventSource.Debt -> "Abrir deuda"
    is CalendarEventSource.Recurrence -> "Ver movimientos"
}

internal fun eventCountLabel(count: Int): String = when (count) {
    0 -> "Sin eventos"
    1 -> "1 evento"
    else -> "$count eventos · agrupados en el mismo día"
}
