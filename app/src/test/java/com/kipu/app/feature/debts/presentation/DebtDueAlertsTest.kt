package com.kipu.app.feature.debts.presentation

import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtDueAlertsTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test fun onlyNextPendingInstallmentIsAnAlert() {
        val schedule = listOf(item(3, 40), item(1, -2, "PAID"), item(2, 3), item(4, 70))
        val alerts = buildDebtDueAlerts(listOf(debt()), mapOf("d" to schedule), today)
        assertEquals(1, alerts.size)
        assertEquals(2, alerts.single().installmentNumber)
        assertEquals(today.plusDays(3), alerts.single().dueDate)
    }

    @Test fun overdueInstallmentsAreGroupedWithPartialAndExcludeFutureAmounts() {
        val schedule = listOf(item(3, 2), item(2, -1, "PARTIAL"), item(1, -30), item(4, -40, "PAID"))
        val alert = buildDebtDueAlerts(listOf(debt()), mapOf("d" to schedule), today).single()
        assertEquals(2, alert.overdueInstallmentCount)
        assertEquals(today.minusDays(30), alert.dueDate)
        assertEquals(2_000L, alert.amountMinor)
        assertTrue(alert.hasPartialInstallment)
    }

    @Test fun windowsIncludeBoundaryAndUseSevenDaysOnlyWhenUnset() {
        assertEquals(1, buildDebtDueAlerts(listOf(debt().copy(dueDate = today.plusDays(7))), emptyMap(), today).size)
        assertTrue(buildDebtDueAlerts(listOf(debt().copy(dueDate = today.plusDays(8))), emptyMap(), today).isEmpty())
        assertTrue(buildDebtDueAlerts(listOf(debt().copy(reminderLeadDays = 2)), mapOf("d" to listOf(item(1, 3))), today).isEmpty())
        assertEquals(1, buildDebtDueAlerts(listOf(debt().copy(reminderLeadDays = 30)), mapOf("d" to listOf(item(1, 30))), today).size)
        assertEquals(1, buildDebtDueAlerts(listOf(debt().copy(dueDate = today, reminderLeadDays = 0)), emptyMap(), today).size)
        assertTrue(buildDebtDueAlerts(listOf(debt().copy(dueDate = today.plusDays(1), reminderLeadDays = 0)), emptyMap(), today).isEmpty())
    }

    @Test fun unscheduledDebtUsesDueDateAndRemainingBalance() {
        val alert = buildDebtDueAlerts(listOf(debt().copy(dueDate = today.minusDays(1))), emptyMap(), today).single()
        assertEquals(10_000L, alert.amountMinor)
        assertEquals(null, alert.installmentNumber)
    }

    @Test fun closedZeroBalanceUndatedAndPaidSchedulesAreExcluded() {
        val debts = listOf(debt().copy(status = DebtLifecycleStatus.SETTLED), debt().copy(remainingPrincipalMinor = 0), debt())
        assertTrue(buildDebtDueAlerts(debts, emptyMap(), today).isEmpty())
        assertTrue(buildDebtDueAlerts(listOf(debt().copy(dueDate = today)), mapOf("d" to listOf(item(1, -1, "PAID"))), today).isEmpty())
    }

    @Test fun relativeDatesUseSpanishSingulars() {
        assertEquals("Vencida hace 1 día", debtDueDayLabel(today.minusDays(1), today))
        assertEquals("Vence hoy", debtDueDayLabel(today, today))
        assertEquals("Vence mañana", debtDueDayLabel(today.plusDays(1), today))
        assertEquals("Vence en 2 días", debtDueDayLabel(today.plusDays(2), today))
    }

    @Test fun distantScheduleHasOnlyOneUpcomingRowAndDoesNotCountAsAlert() {
        val result = buildDebtDueOverview(listOf(debt()), mapOf("d" to listOf(item(3, 87), item(1, 26), item(2, 56))), today)
        assertTrue(result.alerts.isEmpty())
        assertEquals(1, result.upcoming.size)
        assertEquals(1, result.upcoming.single().installmentNumber)
        assertEquals(today.plusDays(26), result.upcoming.single().dueDate)
        assertEquals(1_000L, result.upcoming.single().amountMinor)
    }

    @Test fun upcomingUsesConfiguredWindowAndUnscheduledDueDate() {
        val near = debt().copy(debtId = "near", dueDate = today.plusDays(8), reminderLeadDays = 8)
        val far = debt().copy(debtId = "far", dueDate = today.plusDays(9), reminderLeadDays = 8)
        val result = buildDebtDueOverview(listOf(far, near), emptyMap(), today)
        assertEquals(listOf("near"), result.alerts.map { it.debt.debtId })
        assertEquals(listOf("far"), result.upcoming.map { it.debt.debtId })
        assertEquals(10_000L, result.upcoming.single().amountMinor)
    }

    @Test fun upcomingExcludesClosedPaidAndZeroBalanceAndDebtsAlreadyOverdue() {
        val result = buildDebtDueOverview(
            listOf(debt(), debt().copy(debtId = "closed", status = DebtLifecycleStatus.SETTLED),
                debt().copy(debtId = "zero", remainingPrincipalMinor = 0), debt().copy(debtId = "paid")),
            mapOf("d" to listOf(item(1, -1), item(2, 26)), "closed" to listOf(item(1, 26)),
                "zero" to listOf(item(1, 26)), "paid" to listOf(item(1, 26, "PAID"))), today)
        assertEquals(1, result.alerts.size)
        assertTrue(result.upcoming.isEmpty())
    }

    private fun item(number: Int, days: Long, status: String = "PENDING") =
        DebtScheduleItem("i$number", number, today.plusDays(days), 1_000L, status)

    private fun debt() = DebtSummary("d", "owner", DebtObligationType.PAYABLE, "Ana", 10_000, 10_000,
        "PEN", today.minusMonths(1), null, null, null, DebtLifecycleStatus.ACTIVE, "SYNCED", 1)
}
