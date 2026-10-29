package com.kipu.app.feature.capture.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureConfidenceScorerTest {
    private val scorer = CaptureConfidenceScorer()

    @Test fun `fully resolved debit expense is HIGH without doubtful reasons`() {
        val result = scorer.score(captureCandidate().copy(confidence = CaptureConfidenceLevel.LOW))
        assertEquals(CaptureConfidenceLevel.HIGH, result.level)
        assertEquals(emptyList<CaptureReason>(), result.reasons)
    }

    @Test fun `exactly one doubtful element is MEDIUM and explains it`() {
        listOf(
            captureCandidate().copy(occurredAtInferred = true) to CaptureReason.DATE_INFERRED,
            captureCandidate().copy(categoryId = null) to CaptureReason.CATEGORY_UNRESOLVED,
            captureCandidate().copy(accountId = null, cardId = null) to CaptureReason.INSTRUMENT_UNRESOLVED,
            captureCandidate().copy(accountId = null, cardId = null, instrumentAmbiguous = true) to CaptureReason.INSTRUMENT_AMBIGUOUS,
            captureCandidate().copy(confidenceReasons = listOf(CaptureReason.POSSIBLE_DUPLICATE)) to CaptureReason.POSSIBLE_DUPLICATE,
        ).forEach { (candidate, reason) ->
            val result = scorer.score(candidate)
            assertEquals(reason.name, CaptureConfidenceLevel.MEDIUM, result.level)
            assertTrue(reason.name, reason in result.reasons)
        }
    }

    @Test fun `unknown merchant without category counts as one element`() {
        val result = scorer.score(captureCandidate().copy(merchantId = null, categoryId = null))
        assertEquals(CaptureConfidenceLevel.MEDIUM, result.level)
        assertTrue(CaptureReason.MERCHANT_UNRESOLVED in result.reasons)
        assertTrue(CaptureReason.CATEGORY_UNRESOLVED in result.reasons)
    }

    @Test fun `missing amount currency or direction is always LOW`() {
        listOf(
            captureCandidate().copy(amountMinor = null) to CaptureReason.AMOUNT_MISSING,
            captureCandidate().copy(currency = null) to CaptureReason.CURRENCY_MISSING,
            captureCandidate().copy(direction = null) to CaptureReason.DIRECTION_MISSING,
        ).forEach { (candidate, reason) ->
            val result = scorer.score(candidate)
            assertEquals(CaptureConfidenceLevel.LOW, result.level)
            assertTrue(reason in result.reasons)
        }
    }

    @Test fun `two doubtful elements are LOW`() {
        val result = scorer.score(captureCandidate().copy(occurredAtInferred = true, categoryId = null))
        assertEquals(CaptureConfidenceLevel.LOW, result.level)
    }

    @Test fun `credit card keeps its level but always explains the mandatory review`() {
        val result = scorer.score(captureCandidate().asCredit())
        assertEquals(CaptureConfidenceLevel.HIGH, result.level)
        assertTrue(CaptureReason.CREDIT_CARD_REQUIRES_REVIEW in result.reasons)
    }

    @Test fun `scoring is deterministic and carries non derivable reasons`() {
        val candidate = captureCandidate().copy(confidenceReasons = listOf(CaptureReason.MERCHANT_AMBIGUOUS), merchantId = null)
        assertEquals(scorer.score(candidate), scorer.score(candidate))
        assertTrue(CaptureReason.MERCHANT_AMBIGUOUS in scorer.score(candidate).reasons)
    }
}
