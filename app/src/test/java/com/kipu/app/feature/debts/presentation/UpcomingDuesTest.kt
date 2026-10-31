package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.session.*
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.feature.debts.domain.usecase.ObserveUpcomingDues
import com.kipu.app.testing.*
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingDuesTest {
    private val today = LocalDate.parse("2026-10-15")
    private val settled = Instant.parse("2026-10-14T12:00:00Z")
    @After fun tearDown() = Dispatchers.resetMain()
    private fun balance(id: String, number: Int, remaining: Long = 1000) = DebtDueBalance("debt", id, number,
        today.plusDays(number.toLong()), 1000, remaining, settled, settled)

    @Test fun `calendar unites debts and cards in chronological order`() {
        val debt = debtFixture(due = today.plusDays(2))
        val card = cardInstallmentFixture(due = today.minusDays(1))
        val snapshot = buildUpcomingDues(listOf(debt), emptyList(), listOf(card), today)
        assertEquals(listOf(DueOrigin.CARD, DueOrigin.DEBT), snapshot.rows.map { it.origin })
        assertEquals(listOf(DueStatus.OVERDUE, DueStatus.UPCOMING), snapshot.rows.map { it.status })
        assertTrue(snapshot.rows.last().canSettle)
        assertFalse(snapshot.rows.first().canSettle)
    }
    @Test fun `periods include overdue seven day horizon calendar month later and recently paid`() {
        val cards = listOf(cardInstallmentFixture("past", today.minusDays(1)), cardInstallmentFixture("week", today.plusDays(7)),
            cardInstallmentFixture("month", today.plusDays(8)), cardInstallmentFixture("later", today.plusMonths(1)),
            cardInstallmentFixture("paid", today.minusDays(40), remaining = 0, settled = settled))
        val rows = buildUpcomingDues(emptyList(), emptyList(), cards, today).rows
        assertEquals(DuePeriod.entries.toSet(), rows.map { it.period(today) }.toSet())
        assertEquals(DuePeriod.RECENTLY_PAID, rows.first { it.id.endsWith(":paid") }.period(today))
    }
    @Test fun `paid calendar rows require a known recent settlement rather than guessed due date`() {
        val cards = listOf(cardInstallmentFixture("unknown", today, 0),
            cardInstallmentFixture("old", today, 0, settled = settled.minusSeconds(30 * 86400)),
            cardInstallmentFixture("future-settlement", today, 0, settled = settled.plusSeconds(3 * 86400)),
            cardInstallmentFixture("recent", today.minusYears(1), 0, settled = settled))
        assertEquals(listOf("recent"), buildUpcomingDues(emptyList(), emptyList(), cards, today).rows.map { it.id.substringAfterLast(':') })
    }
    @Test fun `unassigned payment projects FIFO and caps rows at remaining principal`() {
        val schedule = listOf(balance("third", 3), balance("first", 1), balance("second", 2))
        val rows = buildUpcomingDues(listOf(debtFixture(remaining = 1500)), schedule, emptyList(), today).rows
        assertEquals(listOf(0L, 500L, 1000L), rows.map { it.outstanding.minorUnits })
        assertEquals(1500L, rows.sumOf { it.outstanding.minorUnits })
        assertEquals(DueStatus.PAID, rows.first().status)
        assertEquals(today.minusDays(1), rows.first().settledOn)
        assertEquals(listOf(1000L, 1000L, 1000L), schedule.map { it.remainingMinor })
    }
    @Test fun `settled debt removes all outstanding even if schedule still says pending`() {
        val rows = buildUpcomingDues(listOf(debtFixture(remaining = 0)), listOf(balance("a", 1), balance("b", 2)), emptyList(), today).rows
        assertEquals(0L, rows.sumOf { it.outstanding.minorUnits })
        assertTrue(rows.all { it.status == DueStatus.PAID && !it.canSettle })
    }
    @Test fun `cancelled debts and unscheduled debts without due date are excluded`() {
        assertTrue(buildUpcomingDues(listOf(debtFixture(status = DebtLifecycleStatus.CANCELLED), debtFixture("no-date", due = null)),
            emptyList(), emptyList(), today).rows.isEmpty())
    }
    @Test fun `filters preserve per currency totals without counting scheduled principal twice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val debts = listOf(debtFixture(remaining = 1500), debtFixture("usd", 2000, "USD", type = DebtObligationType.RECEIVABLE))
        val snapshot = buildUpcomingDues(debts, listOf(balance("a", 1), balance("b", 2), balance("c", 3)),
            listOf(cardInstallmentFixture(due = today.minusDays(1))), today)
        val source = MutableStateFlow(snapshot)
        val observe = mockk<ObserveUpcomingDues> { every { this@mockk.invoke("owner") } returns source }
        val session = mockk<SessionCoordinator> { every { localAccess } returns MutableStateFlow(LocalAccess.Available("owner", RemoteSession.Absent)) }
        val handle = SavedStateHandle()
        val vm = UpcomingDuesViewModel(session, observe, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(2600L, vm.state.value.payableTotals.single().minorUnits)
        assertEquals(Currency.PEN, vm.state.value.payableTotals.single().currency)
        assertEquals(2000L, vm.state.value.receivableTotals.single().minorUnits)
        assertEquals(Currency.USD, vm.state.value.receivableTotals.single().currency)
        vm.selectOrigin(DueOriginFilter.CARDS); runCurrent()
        assertEquals(1, vm.state.value.rows.size)
        assertEquals(1100L, vm.state.value.payableTotals.single().minorUnits)
        vm.selectOrigin(DueOriginFilter.ALL); vm.selectCurrency(Currency.USD); runCurrent()
        assertEquals(listOf("usd"), vm.state.value.rows.map { it.destinationId })
        assertTrue(vm.state.value.payableTotals.isEmpty())
        vm.selectCurrency(null); vm.selectType(DebtObligationType.PAYABLE); vm.selectStatus(DueStatusFilter.OVERDUE); runCurrent()
        assertEquals(listOf(DueOrigin.CARD), vm.state.value.rows.map { it.origin })
        assertEquals(setOf(DuePeriod.OVERDUE), vm.state.value.periods.keys)
        assertEquals(DueStatusFilter.OVERDUE.name, handle.get<String>("dues.status"))
    }
    @Test fun `pending future and paid filters select exact statuses`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = buildUpcomingDues(emptyList(), emptyList(), listOf(cardInstallmentFixture("next", today),
            cardInstallmentFixture("later", today.plusDays(10)), cardInstallmentFixture("paid", today, 0, settled = settled)), today)
        val observe = mockk<ObserveUpcomingDues> { every { this@mockk.invoke("owner") } returns flowOf(source) }
        val session = mockk<SessionCoordinator> { every { localAccess } returns MutableStateFlow(LocalAccess.Available("owner", RemoteSession.Absent)) }
        val vm = UpcomingDuesViewModel(session, observe, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }; runCurrent()
        for ((filter, statuses) in listOf(DueStatusFilter.PENDING to setOf(DueStatus.PENDING), DueStatusFilter.UPCOMING to setOf(DueStatus.UPCOMING),
            DueStatusFilter.PAID to setOf(DueStatus.PAID), DueStatusFilter.FUTURE to setOf(DueStatus.PENDING, DueStatus.UPCOMING))) {
            vm.selectStatus(filter); runCurrent()
            assertEquals(statuses, vm.state.value.rows.map { it.status }.toSet())
        }
    }
    @Test fun `payable totals keep PEN and USD separate under the same obligation direction`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = buildUpcomingDues(listOf(debtFixture("soles", 1500), debtFixture("dollars", 2000, "USD")), emptyList(),
            listOf(cardInstallmentFixture("pen", currency = Currency.PEN), cardInstallmentFixture("usd", currency = Currency.USD)), today)
        val observe = mockk<ObserveUpcomingDues> { every { this@mockk.invoke("owner") } returns flowOf(source) }
        val session = mockk<SessionCoordinator> { every { localAccess } returns MutableStateFlow(LocalAccess.Available("owner", RemoteSession.Absent)) }
        val vm = UpcomingDuesViewModel(session, observe, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }; runCurrent()
        assertEquals(mapOf(Currency.PEN to 2600L, Currency.USD to 3100L), vm.state.value.payableTotals.associate { it.currency to it.minorUnits })
        vm.selectCurrency(Currency.USD); runCurrent()
        assertEquals(listOf(Currency.USD), vm.state.value.payableTotals.map { it.currency })
        assertEquals(3100L, vm.state.value.payableTotals.single().minorUnits)
    }

}
