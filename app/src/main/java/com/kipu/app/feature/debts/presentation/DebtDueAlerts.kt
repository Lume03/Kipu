package com.kipu.app.feature.debts.presentation

import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// An unset reminder disables push notifications; this default is only for the in-app alert window.
internal const val DEFAULT_DEBT_ALERT_LEAD_DAYS = 7

internal data class DebtDueAlert(
    val debt: DebtSummary,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val installmentNumber: Int? = null,
    val installmentCount: Int = 0,
    val overdueInstallmentCount: Int = 0,
    val hasPartialInstallment: Boolean = false,
)

internal data class DebtDueOverview(
    val alerts: List<DebtDueAlert>,
    val upcoming: List<DebtDueAlert>,
)

internal fun buildDebtDueAlerts(
    debts: List<DebtSummary>,
    schedules: Map<String, List<DebtScheduleItem>>,
    today: LocalDate,
): List<DebtDueAlert> = buildDebtDueOverview(debts, schedules, today).alerts

internal fun buildDebtDueOverview(
    debts: List<DebtSummary>,
    schedules: Map<String, List<DebtScheduleItem>>,
    today: LocalDate,
): DebtDueOverview {
    val dues = debts.asSequence()
        .filter { it.status == DebtLifecycleStatus.ACTIVE && it.remainingPrincipalMinor > 0L }
        .mapNotNull { debt ->
            val schedule = schedules[debt.debtId].orEmpty()
            if (schedule.isEmpty()) {
                debt.dueDate?.let {
                    DebtDueAlert(debt, it, debt.remainingPrincipalMinor)
                }
            } else {
                val pending = schedule.filter { it.status in setOf("PENDING", "PARTIAL") && it.principalMinor > 0L }
                    .sortedWith(compareBy<DebtScheduleItem> { it.dueDate }.thenBy { it.installmentNumber })
                val overdue = pending.filter { it.dueDate < today }
                val next = pending.firstOrNull() ?: return@mapNotNull null
                val actionable = overdue.ifEmpty { listOf(next) }
                DebtDueAlert(
                    debt = debt,
                    dueDate = next.dueDate,
                    // Schedule items contain the planned principal, not the remaining installment balance.
                    amountMinor = actionable.sumOf { it.principalMinor },
                    installmentNumber = next.installmentNumber,
                    installmentCount = schedule.size,
                    overdueInstallmentCount = overdue.size,
                    hasPartialInstallment = actionable.any { it.status == "PARTIAL" },
                )
            }
        }.sortedWith(compareBy<DebtDueAlert> { it.dueDate }.thenBy { it.debt.debtId }).toList()
    val (alerts, upcoming) = dues.partition {
        val window = (it.debt.reminderLeadDays ?: DEFAULT_DEBT_ALERT_LEAD_DAYS).coerceAtLeast(0)
        it.dueDate <= today.plusDays(window.toLong())
    }
    return DebtDueOverview(alerts, upcoming)
}

internal fun debtDueDayLabel(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == -1L -> "Vencida hace 1 día"
        days < 0 -> "Vencida hace ${-days} días"
        days == 0L -> "Vence hoy"
        days == 1L -> "Vence mañana"
        else -> "Vence en $days días"
    }
}
