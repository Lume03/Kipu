package com.kipu.app.feature.calendar.domain

import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.serialization.json.JsonPrimitive

/**
 * Funciones puras que convierten fuentes existentes en eventos de calendario.
 * No leen ni escriben datos; la fecha de cada evento se calcula con las reglas del dominio
 * (CreditCalculations, RecurrenceScheduleCalculator).
 */
object CalendarEventProjector {

    /** Movimientos realizados del mes; los anulados o fallidos no son hechos financieros vigentes. */
    fun fromMovements(items: List<TransactionItem>, month: YearMonth, zone: ZoneId): List<CalendarEvent> =
        items.mapNotNull { item ->
            val tx = item.transaction
            if (tx.status == TransactionStatus.VOIDED || tx.status == TransactionStatus.FAILED) return@mapNotNull null
            val date = Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate()
            if (YearMonth.from(date) != month) return@mapNotNull null
            val title = item.merchantName?.takeIf { it.isNotBlank() }
                ?: item.categoryName?.takeIf { it.isNotBlank() }
                ?: tx.note?.takeIf { it.isNotBlank() }
                ?: when (tx.type) {
                    MovementType.EXPENSE -> "Gasto"
                    MovementType.INCOME -> "Ingreso"
                    MovementType.TRANSFER -> "Transferencia"
                }
            CalendarEvent(
                id = "movement:${tx.id}",
                date = date,
                kind = CalendarEventKind.MOVEMENT,
                status = CalendarEventStatus.REALIZED,
                title = title,
                amount = currencyOrNull(tx.currency)?.let { Money(tx.amountMinor, it) },
                source = CalendarEventSource.Movement(tx.id),
            )
        }

    /** Cierre y vencimiento de tarjetas de crédito activas, con el día efectivo del mes. */
    fun fromCards(cards: List<Card>, month: YearMonth): List<CalendarEvent> =
        cards.filterIsInstance<CreditCard>().filterNot { it.isArchived }.flatMap { card ->
            val name = card.alias?.takeIf { it.isNotBlank() } ?: card.issuer
            val label = "$name ••${card.lastFourDigits}"
            val closing = CreditCalculations.calculateEffectiveDate(card.billingDay, month.year, month.month)
            val due = CreditCalculations.calculateEffectiveDate(card.dueDay, month.year, month.month)
            listOf(
                CalendarEvent(
                    id = "card-closing:${card.id.value}:$closing",
                    date = closing,
                    kind = CalendarEventKind.CARD_CLOSING,
                    status = CalendarEventStatus.PLANNED,
                    title = "Cierre de tarjeta $label",
                    source = CalendarEventSource.Card(card.id.value),
                ),
                CalendarEvent(
                    id = "card-due:${card.id.value}:$due",
                    date = due,
                    kind = CalendarEventKind.CARD_DUE,
                    status = CalendarEventStatus.PLANNED,
                    title = "Vencimiento de tarjeta $label",
                    source = CalendarEventSource.Card(card.id.value),
                ),
            )
        }

    /**
     * Cuotas pendientes de deudas con cronograma y, para deudas activas sin cronograma, su fecha de
     * vencimiento. Una cuota cuya deuda ya no existe se conserva como no disponible.
     */
    fun fromDebts(
        debts: List<DebtSummary>,
        installmentsByDebtId: Map<String, List<DebtScheduleItem>>,
        month: YearMonth,
    ): List<CalendarEvent> {
        val debtsById = debts.associateBy { it.debtId }
        val installmentEvents = installmentsByDebtId.flatMap { (debtId, installments) ->
            val debt = debtsById[debtId]
            installments
                .filter { it.status == "PENDING" || it.status == "PARTIAL" }
                .filter { YearMonth.from(it.dueDate) == month }
                .filter { debt == null || debt.status == DebtLifecycleStatus.ACTIVE }
                .map { item ->
                    CalendarEvent(
                        id = "debt-installment:${item.id}",
                        date = item.dueDate,
                        kind = CalendarEventKind.DEBT_INSTALLMENT,
                        status = CalendarEventStatus.PLANNED,
                        title = if (debt == null) {
                            "Cuota ${item.installmentNumber} (deuda no disponible)"
                        } else {
                            "${debtVerb(debt)} cuota ${item.installmentNumber} · ${debt.counterpartyName}"
                        },
                        amount = debt?.let { currencyOrNull(it.currencyCode) }?.let { Money(item.principalMinor, it) },
                        source = CalendarEventSource.Debt(debtId),
                        isSourceAvailable = debt != null,
                    )
                }
        }
        val dueEvents = debts
            .filter { it.status == DebtLifecycleStatus.ACTIVE && installmentsByDebtId[it.debtId].isNullOrEmpty() }
            .filter { it.dueDate != null && YearMonth.from(it.dueDate) == month }
            .map { debt ->
                CalendarEvent(
                    id = "debt-due:${debt.debtId}",
                    date = debt.dueDate!!,
                    kind = CalendarEventKind.DEBT_DUE,
                    status = CalendarEventStatus.PLANNED,
                    title = "${debtVerb(debt)} · ${debt.counterpartyName}",
                    amount = currencyOrNull(debt.currencyCode)?.let { Money(debt.remainingPrincipalMinor, it) },
                    source = CalendarEventSource.Debt(debt.debtId),
                )
            }
        return installmentEvents + dueEvents
    }

    /** Ocurrencias futuras (desde la próxima fecha) de recurrencias activas dentro del mes. */
    fun fromRecurrences(rules: List<RecurrenceRule>, month: YearMonth): List<CalendarEvent> =
        rules.filter { it.status == RecurrenceStatus.ACTIVE }.flatMap { rule ->
            val title = (rule.templateTransaction["concept"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                ?: "Obligación recurrente"
            val amountMinor = (rule.templateTransaction["amount_minor"] as? JsonPrimitive)?.content?.toLongOrNull()
            val currency = (rule.templateTransaction["currency_code"] as? JsonPrimitive)?.content?.let(::currencyOrNull)
            val amount = if (amountMinor != null && currency != null) Money(amountMinor, currency) else null
            (1..month.lengthOfMonth()).map { month.atDay(it) }
                .filter { day -> day >= rule.nextDueDate && (rule.endDate == null || day <= rule.endDate) }
                .filter { day -> isOccurrence(rule, day) }
                .map { day ->
                    CalendarEvent(
                        id = "recurrence:${rule.id}:$day",
                        date = day,
                        kind = CalendarEventKind.RECURRENCE,
                        status = CalendarEventStatus.PLANNED,
                        title = title,
                        amount = amount,
                        source = CalendarEventSource.Recurrence(rule.id),
                    )
                }
        }

    /** Agrupa por día sin fusionar eventos; dentro del día, realizados primero y orden estable. */
    fun buildMonth(month: YearMonth, events: List<CalendarEvent>): CalendarMonth {
        val days = events
            .filter { YearMonth.from(it.date) == month }
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, dayEvents) ->
                CalendarDay(
                    date,
                    dayEvents.sortedWith(
                        compareBy<CalendarEvent>({ it.status.ordinal }, { it.kind.ordinal }, { it.title }, { it.id }),
                    ),
                )
            }
        return CalendarMonth(month, days)
    }

    private fun isOccurrence(rule: RecurrenceRule, day: LocalDate): Boolean = try {
        RecurrenceScheduleCalculator.isOccurrence(
            rule.startDate, rule.frequency, rule.intervalCount, rule.customIntervalUnit, day,
        )
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun debtVerb(debt: DebtSummary) =
        if (debt.obligationType == DebtObligationType.RECEIVABLE) "Cobrar" else "Pagar"

    private fun currencyOrNull(code: String): Currency? = runCatching { Currency.fromCode(code) }.getOrNull()
}
