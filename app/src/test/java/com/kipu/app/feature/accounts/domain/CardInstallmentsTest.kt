package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.testing.cardInstallmentFixture
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CardInstallmentsTest {
    private val today = LocalDate.parse("2026-10-15")
    @Test fun `zero balance is paid regardless of due date`() {
        for (offset in listOf(-30L, 0L, 60L)) assertEquals(CardInstallmentStatus.PAID,
            cardInstallmentFixture(due = today.plusDays(offset), remaining = 0).atDate(today).status)
    }
    @Test fun `status uses civil date and includes today through seven days`() {
        for ((offset, expected) in listOf(-1L to CardInstallmentStatus.OVERDUE, 0L to CardInstallmentStatus.UPCOMING,
            7L to CardInstallmentStatus.UPCOMING, 8L to CardInstallmentStatus.PENDING)) {
            assertEquals(expected, cardInstallmentFixture(due = today.plusDays(offset)).atDate(today).status)
        }
    }
    @Test fun `summary includes interest and groups outstanding by derived status`() {
        val rows = listOf(cardInstallmentFixture("old", today.minusDays(1), 900, 200),
            cardInstallmentFixture("next", today.plusDays(7)), cardInstallmentFixture("later", today.plusDays(8)),
            cardInstallmentFixture("paid", today.minusDays(5), 0, 1100)).map { it.atDate(today) }
        val summary = CardInstallmentsSummary.from(rows, Currency.PEN)
        assertEquals(3100L, summary.outstandingTotal.minorUnits)
        assertEquals(900L, summary.overdueTotal.minorUnits)
        assertEquals(1100L, summary.upcomingTotal.minorUnits)
        assertEquals(1300L, summary.paidTotal.minorUnits)
        assertEquals(listOf("old"), summary.overdue.map { it.id })
        assertEquals(listOf("next"), summary.upcoming.map { it.id })
        assertEquals(listOf("paid"), summary.paid.map { it.id })
    }
    @Test fun `summary rejects mixed currencies and supports an empty card`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardInstallmentsSummary.from(listOf(cardInstallmentFixture(currency = Currency.USD)), Currency.PEN)
        }
        assertEquals(0L, CardInstallmentsSummary.from(emptyList(), Currency.USD).outstandingTotal.minorUnits)
    }
}
