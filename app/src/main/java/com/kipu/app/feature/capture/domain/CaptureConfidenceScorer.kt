package com.kipu.app.feature.capture.domain

import javax.inject.Inject

data class CaptureConfidenceAssessment(
    val level: CaptureConfidenceLevel,
    val reasons: List<CaptureReason>,
)

/**
 * Deterministic, explainable confidence (HU-47). Reasons are derived from the candidate fields so the
 * same record always yields the same level:
 * - LOW when amount, currency or direction is missing, or two or more elements are doubtful.
 * - MEDIUM when exactly one non-critical element (instrument, date, category, merchant match,
 *   possible duplicate) is doubtful.
 * - HIGH otherwise. Credit cards may score HIGH but are never registered automatically.
 */
class CaptureConfidenceScorer @Inject constructor() {
    fun score(candidate: CaptureCandidateRecord): CaptureConfidenceAssessment {
        val reasons = mutableListOf<CaptureReason>()
        if (candidate.amountMinor == null) reasons += CaptureReason.AMOUNT_MISSING
        if (candidate.currency == null) reasons += CaptureReason.CURRENCY_MISSING
        if (candidate.direction == null) reasons += CaptureReason.DIRECTION_MISSING
        when {
            candidate.instrumentAmbiguous -> reasons += CaptureReason.INSTRUMENT_AMBIGUOUS
            candidate.accountId == null && candidate.cardId == null -> reasons += CaptureReason.INSTRUMENT_UNRESOLVED
        }
        if (candidate.occurredAtInferred) reasons += CaptureReason.DATE_INFERRED
        // Merchant ambiguity cannot be derived from the fields; it is carried from extraction.
        if (CaptureReason.MERCHANT_AMBIGUOUS in candidate.confidenceReasons) reasons += CaptureReason.MERCHANT_AMBIGUOUS
        else if (candidate.merchantId == null) reasons += CaptureReason.MERCHANT_UNRESOLVED
        if (candidate.categoryId == null) reasons += CaptureReason.CATEGORY_UNRESOLVED
        if (CaptureReason.POSSIBLE_DUPLICATE in candidate.confidenceReasons) reasons += CaptureReason.POSSIBLE_DUPLICATE
        if (candidate.instrumentKind == CaptureInstrumentKind.CREDIT_CARD) reasons += CaptureReason.CREDIT_CARD_REQUIRES_REVIEW
        if (CaptureReason.AUTO_REGISTER_DENIED in candidate.confidenceReasons) reasons += CaptureReason.AUTO_REGISTER_DENIED

        val doubtful = reasons.count { it in DOUBTFUL }
        val level = when {
            reasons.any { it in CRITICAL } || doubtful >= 2 -> CaptureConfidenceLevel.LOW
            doubtful == 1 -> CaptureConfidenceLevel.MEDIUM
            else -> CaptureConfidenceLevel.HIGH
        }
        return CaptureConfidenceAssessment(level, reasons)
    }

    private companion object {
        val CRITICAL = setOf(CaptureReason.AMOUNT_MISSING, CaptureReason.CURRENCY_MISSING, CaptureReason.DIRECTION_MISSING)

        // MERCHANT_UNRESOLVED is informative only: a category is resolved solely through a known merchant,
        // so a missing merchant is already counted as CATEGORY_UNRESOLVED.
        val DOUBTFUL = setOf(
            CaptureReason.INSTRUMENT_AMBIGUOUS,
            CaptureReason.INSTRUMENT_UNRESOLVED,
            CaptureReason.DATE_INFERRED,
            CaptureReason.MERCHANT_AMBIGUOUS,
            CaptureReason.CATEGORY_UNRESOLVED,
            CaptureReason.POSSIBLE_DUPLICATE,
        )
    }
}
