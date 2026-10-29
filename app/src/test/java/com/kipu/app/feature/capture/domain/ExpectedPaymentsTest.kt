package com.kipu.app.feature.capture.domain

import com.kipu.app.feature.recurrences.domain.GenerateExpectedPayments
import com.kipu.app.feature.recurrences.domain.MatchCapturedMovementToExpected
import com.kipu.app.feature.recurrences.domain.model.*
import io.mockk.*
import java.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ExpectedPaymentsTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)
    private val store = mockk<ExpectedPaymentStore>()
    private val gate = FakeCaptureAccessGate("owner-1")
    private val candidates = FakeCaptureCandidateStore()
    private val matcher = MatchCapturedMovementToExpected(store, gate, clock, candidates)
    init {
        every { store.observeByOwner(any()) } returns flowOf(emptyList())
        coEvery { store.syncExpectedTemplate(any(), any(), any(), any(), any()) } just Runs
    }
    private val candidate = captureCandidate("owner-1").copy(status = CaptureCandidateStatus.CONFIRMED, resolvedTransactionId = "tx", occurredAt = clock.millis(), amountMinor = 1000, currency = "PEN")
    private fun payment(id: String) = ExpectedPayment(id, "owner-1", "rec-$id", LocalDate.parse("2026-10-28"), 1000, "PEN", ExpectedPaymentStatus.EXPECTED, null, null, clock.millis(), clock.millis())
    private fun rule() = RecurrenceRule("rec", "owner-1", buildJsonObject { put("amount_minor", 1000); put("currency_code", "PEN") }, "FIXED", "MONTHLY", 1, null, LocalDate.parse("2026-01-28"), null, LocalDate.parse("2026-09-28"), 2, RecurrenceStatus.ACTIVE, 1, RecurrenceSyncState.SYNCED, clock.instant(), clock.instant())

    @Test fun `generation is idempotent and skips missed periods`() = runTest {
        val inserted = mutableSetOf<Triple<String, String, LocalDate>>()
        val rows = mutableListOf<ExpectedPayment>()
        coEvery { store.insertIfAbsent(any()) } answers { val row = firstArg<ExpectedPayment>(); if (inserted.add(Triple(row.ownerId, row.recurrenceId, row.dueDate))) { rows.add(row); true } else false }
        val generate = GenerateExpectedPayments(store, clock)
        generate(rule()); generate(rule())
        assertEquals(1, rows.size)
        assertEquals(LocalDate.parse("2026-10-28"), rows.single().dueDate)
        assertEquals(ExpectedPaymentStatus.EXPECTED, rows.single().status)
        coVerify(exactly = 2) { store.insertIfAbsent(any()) }
    }

    @Test fun `one match is attended using existing transaction`() = runTest {
        val row = payment("one")
        coEvery { store.findExpectedByAmount(any(), any(), any(), any(), any()) } returns listOf(row)
        coEvery { store.get("owner-1", "one") } returns row
        coEvery { store.markAttended(any(), any(), any(), any(), any()) } returns true
        candidates.insertIfAbsent(candidate)
        matcher.onResolved(candidate, "tx")
        coVerify(exactly = 1) { store.markAttended("owner-1", "one", "tx", candidate.id, clock.millis()) }
    }

    @Test fun `ambiguous matches require an explicit selection`() = runTest {
        val rows = listOf(payment("one"), payment("two"))
        coEvery { store.findExpectedByAmount(any(), any(), any(), any(), any()) } returns rows
        coEvery { store.get("owner-1", "two") } returns rows[1]
        coEvery { store.markAttended(any(), any(), any(), any(), any()) } returns true
        candidates.insertIfAbsent(candidate)
        matcher.onResolved(candidate, "tx")
        coVerify(exactly = 0) { store.markAttended(any(), any(), any(), any(), any()) }
        assertEquals(2, matcher.options(candidate).size)
        assertTrue(matcher.link(candidate, "tx", "two"))
    }

    @Test fun `no match does nothing`() = runTest {
        coEvery { store.findExpectedByAmount(any(), any(), any(), any(), any()) } returns emptyList()
        candidates.insertIfAbsent(candidate)
        matcher.onResolved(candidate, "tx")
        coVerify(exactly = 0) { store.markAttended(any(), any(), any(), any(), any()) }
    }

    @Test fun `ambiguous auto registration persists choice and new matcher recovers it`() = runTest {
        val auto = candidate.copy(status = CaptureCandidateStatus.AUTO_REGISTERED)
        candidates.insertIfAbsent(auto)
        coEvery { store.findExpectedByAmount(any(), any(), any(), any(), any()) } returns listOf(payment("one"), payment("two"))
        matcher.onResolved(auto, "tx")
        assertEquals(ExpectedMatchState.PENDING_CHOICE, candidates.get(auto.ownerId, auto.id)?.expectedMatchState)
        val restored = candidates.observePendingExpectedMatches(auto.ownerId).first().single()
        val restarted = MatchCapturedMovementToExpected(store, gate, clock, candidates)
        assertEquals(2, restarted.options(restored).size)
        assertTrue(restarted.chooseNone(restored))
        assertEquals(ExpectedMatchState.NONE, candidates.get(auto.ownerId, auto.id)?.expectedMatchState)
        assertTrue(candidates.observePendingExpectedMatches(auto.ownerId).first().isEmpty())
        restarted.onResolved(auto, "tx")
        assertEquals(ExpectedMatchState.NONE, candidates.get(auto.ownerId, auto.id)?.expectedMatchState)
        coVerify(exactly = 0) { store.markAttended(any(), any(), any(), any(), any()) }
    }

    @Test fun `updating paused recurrence still synchronizes expected template without generating`() = runTest {
        val changed = rule().copy(status = RecurrenceStatus.PAUSED, templateTransaction = buildJsonObject { put("amount_minor", 2500); put("currency_code", "USD") })
        GenerateExpectedPayments(store, clock)(changed)
        coVerify { store.syncExpectedTemplate("owner-1", "rec", 2500, "USD", clock.millis()) }
        coVerify(exactly = 0) { store.insertIfAbsent(any()) }
    }
}
