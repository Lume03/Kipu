package com.kipu.app.feature.capture.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveCaptureCandidateTest {
    private val owner = "owner-1"
    private val candidate = captureCandidate(owner)
    private val store = FakeCaptureCandidateStore(candidate)
    private val clock = Clock.fixed(Instant.ofEpochMilli(CAPTURE_NOW), ZoneOffset.UTC)
    private val discard = com.kipu.app.feature.capture.domain.testDiscardCaptureCandidate(store, clock)
    private val gate = FakeCaptureAccessGate(owner, CaptureAccessDecision.PremiumRequired)
    private val convert = ConvertCandidateToManual(store, gate)

    @Test fun `discard erases evidence without a ledger and is idempotent`() = runTest {
        assertEquals(CaptureDiscardResult.Discarded(alreadyDiscarded = false), discard(owner, candidate.id))
        val row = store.row(owner, candidate.id)!!
        assertEquals(CaptureCandidateStatus.DISCARDED, row.status)
        assertNull(row.evidenceSnippet)
        assertNull(row.resolvedTransactionId)
        assertEquals(CaptureDiscardResult.Discarded(alreadyDiscarded = true), discard(owner, candidate.id))
    }

    @Test fun `discard never touches registered or foreign candidates`() = runTest {
        store.markResolved(owner, candidate.id, CaptureCandidateStatus.CONFIRMED, "tx-1", "key", emptyList(), CAPTURE_NOW)
        assertEquals(CaptureDiscardResult.AlreadyRegistered, discard(owner, candidate.id))
        assertEquals(CaptureDiscardResult.NotFound, discard("owner-2", candidate.id))
        assertEquals(CaptureCandidateStatus.CONFIRMED, store.row(owner, candidate.id)!!.status)
    }

    @Test fun `manual conversion keeps the candidate and reuses its idempotency key`() = runTest {
        val draft = (convert(owner, candidate.id) as CaptureManualConversionResult.Draft).draft
        assertEquals(CaptureResolutionKey.of(owner, candidate.id), draft.idempotencyKey)
        assertEquals(candidate.amountMinor, draft.amountMinor)
        assertEquals(candidate.merchantSourceText, draft.merchantText)
        assertEquals(candidate.accountId, draft.accountId)
        assertEquals(CaptureCandidateStatus.PENDING, store.row(owner, candidate.id)!!.status)
        assertEquals(candidate.evidenceSnippet, store.row(owner, candidate.id)!!.evidenceSnippet)
    }

    @Test fun `manual conversion requires the session owner and a pending candidate`() = runTest {
        assertEquals(CaptureManualConversionResult.NotFound, convert("owner-2", candidate.id))
        discard(owner, candidate.id)
        assertEquals(CaptureManualConversionResult.NotPending, convert(owner, candidate.id))
    }

    @Test fun `expired evidence is purged`() = runTest {
        assertEquals(1, store.purgeExpiredEvidence(CAPTURE_NOW + 1_000L))
        assertNull(store.row(owner, candidate.id)!!.evidenceSnippet)
    }
}
