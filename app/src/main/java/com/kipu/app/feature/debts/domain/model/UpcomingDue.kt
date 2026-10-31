package com.kipu.app.feature.debts.domain.model

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CardInstallment
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class DueOrigin(val label: String) { DEBT("Deuda"), CARD("Tarjeta") }
enum class DueStatus(val label: String) { PENDING("PENDIENTE"), UPCOMING("PRÓXIMA"), OVERDUE("VENCIDA"), PAID("PAGADA") }
enum class DuePeriod(val label: String) {
    OVERDUE("Vencidas"), THIS_WEEK("Esta semana"), THIS_MONTH("Este mes"), LATER("Más adelante"), RECENTLY_PAID("Pagadas recientemente")
}

data class UpcomingDue(
    val id: String,
    val origin: DueOrigin,
    val destinationId: String,
    val title: String,
    val description: String?,
    val dueDate: LocalDate,
    val outstanding: Money,
    val total: Money,
    val status: DueStatus,
    val obligationType: DebtObligationType,
    val installmentNumber: Int? = null,
    val installmentCount: Int = 0,
    val settledOn: LocalDate? = null,
    val canSettle: Boolean = false,
) {
    fun period(today: LocalDate): DuePeriod = when {
        status == DueStatus.PAID -> DuePeriod.RECENTLY_PAID
        dueDate < today -> DuePeriod.OVERDUE
        dueDate <= today.plusDays(7) -> DuePeriod.THIS_WEEK
        YearMonth.from(dueDate) == YearMonth.from(today) -> DuePeriod.THIS_MONTH
        else -> DuePeriod.LATER
    }
}

data class UpcomingDuesSnapshot(
    val rows: List<UpcomingDue>,
    val debts: List<DebtSummary>,
    val today: LocalDate,
)

/**
 * Presentation-only read projection, never an allocation or financial command.
 * Each debt's remainingPrincipalMinor is the balance authority and caps its scheduled rows.
 * Unassigned reductions (sum of installment remaining minus the debt balance) cover the oldest
 * outstanding installments FIFO in this projection only. A fully covered row is PAID; its projected
 * settlement date uses the last valid general principal-reduction event. Stored allocations,
 * installment statuses and HU-44 reminder settings are never changed.
 * Recent paid rows require a known valid settlement instant, never a guessed date from dueDate.
 */
fun buildUpcomingDues(
    debts: List<DebtSummary>,
    balances: List<DebtDueBalance>,
    cards: List<CardInstallment>,
    today: LocalDate,
): UpcomingDuesSnapshot {
    val zone = ZoneId.of("America/Lima")
    fun status(remaining: Long, date: LocalDate) = when {
        remaining == 0L -> DueStatus.PAID
        date < today -> DueStatus.OVERDUE
        date <= today.plusDays(7) -> DueStatus.UPCOMING
        else -> DueStatus.PENDING
    }
    fun recent(date: LocalDate?) = date != null && date in today.minusDays(29)..today
    val debtRows = debts.filter { it.status != DebtLifecycleStatus.CANCELLED }.flatMap { debt ->
        val currency = Currency.fromCode(debt.currencyCode)
        val entries = balances.filter { it.debtId == debt.debtId }
        val schedule = entries.filter { it.installmentId != null && it.dueDate != null }
            .sortedWith(compareBy({ it.dueDate }, { it.installmentNumber }, { it.installmentId }))
        if (schedule.isEmpty()) {
            val due = debt.dueDate ?: return@flatMap emptyList()
            val remaining = debt.remainingPrincipalMinor.coerceAtLeast(0L)
            val settledOn = entries.firstOrNull()?.lastSettlementAt?.atZone(zone)?.toLocalDate()
            if (remaining == 0L && !recent(settledOn)) emptyList() else listOf(UpcomingDue(
                id = "debt:${debt.debtId}", origin = DueOrigin.DEBT, destinationId = debt.debtId,
                title = debt.counterpartyName, description = debt.notes, dueDate = due,
                outstanding = Money(remaining, currency), total = Money(debt.principalMinor, currency),
                status = status(remaining, due), obligationType = debt.obligationType, settledOn = settledOn,
                canSettle = debt.status == DebtLifecycleStatus.ACTIVE && remaining > 0,
            ))
        } else {
            val scheduleRemaining = schedule.fold(0L) { sum, item -> Math.addExact(sum, item.remainingMinor?.coerceAtLeast(0L) ?: 0L) }
            var generalReduction = (scheduleRemaining - debt.remainingPrincipalMinor.coerceAtLeast(0L)).coerceAtLeast(0L)
            schedule.mapNotNull { item ->
                val linkedRemaining = item.remainingMinor?.coerceAtLeast(0L) ?: 0L
                val applied = minOf(generalReduction, linkedRemaining)
                generalReduction -= applied
                val remaining = linkedRemaining - applied
                val settledOn = (if (applied > 0L) item.lastGeneralSettlementAt else item.lastSettlementAt)?.atZone(zone)?.toLocalDate()
                if (remaining == 0L && !recent(settledOn)) return@mapNotNull null
                val due = requireNotNull(item.dueDate)
                UpcomingDue(
                    id = "debt:${debt.debtId}:${item.installmentId}", origin = DueOrigin.DEBT, destinationId = debt.debtId,
                    title = debt.counterpartyName, description = debt.notes, dueDate = due,
                    outstanding = Money(remaining, currency), total = Money(item.plannedMinor ?: 0L, currency),
                    status = status(remaining, due), obligationType = debt.obligationType,
                    installmentNumber = item.installmentNumber, installmentCount = schedule.size, settledOn = settledOn,
                    canSettle = debt.status == DebtLifecycleStatus.ACTIVE && remaining > 0,
                )
            }
        }
    }
    val cardRows = cards.mapNotNull { card ->
        val remaining = card.outstanding.minorUnits
        val settledOn = card.settledAt?.atZone(zone)?.toLocalDate()
        if (remaining == 0L && !recent(settledOn)) return@mapNotNull null
        UpcomingDue(
            id = "card:${card.cardId.value}:${card.id}", origin = DueOrigin.CARD, destinationId = card.cardId.value,
            title = "${card.merchant?.takeIf(String::isNotBlank) ?: "Compra"} · ${card.cardName}",
            description = card.description, dueDate = card.dueDate, outstanding = card.outstanding, total = card.total,
            status = status(remaining, card.dueDate), obligationType = DebtObligationType.PAYABLE,
            installmentNumber = card.installmentNumber, installmentCount = card.installmentsCount, settledOn = settledOn,
        )
    }
    return UpcomingDuesSnapshot((debtRows + cardRows).sortedWith(compareBy({ it.dueDate }, { it.id })), debts, today)
}
