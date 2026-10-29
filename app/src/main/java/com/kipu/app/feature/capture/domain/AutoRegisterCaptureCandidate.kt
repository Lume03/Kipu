package com.kipu.app.feature.capture.domain

import java.time.Clock
import javax.inject.Inject

sealed interface AutoRegisterResult {
    data class Registered(val transactionId: String) : AutoRegisterResult

    /** Not HIGH debit/expense; stays PENDING for the inbox. */
    data object NotEligible : AutoRegisterResult

    /** The gate denied right before saving (e.g. Premium expired); stays PENDING with AUTO_REGISTER_DENIED. */
    data object AccessDenied : AutoRegisterResult

    /** The ledger flagged a similar movement or rejected it; stays PENDING for the user to decide. */
    data object NeedsReview : AutoRegisterResult
}

/**
 * HU-47: registers a candidate without user action only when it is HIGH, an expense from a debit account or
 * wallet, with a unique instrument and a reliable date. Credit cards are never automatic (RN-17).
 */
class AutoRegisterCaptureCandidate @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val confirm: ConfirmCaptureCandidate,
    private val clock: Clock,
) {
    suspend operator fun invoke(candidate: CaptureCandidateRecord): AutoRegisterResult {
        if (!isEligible(candidate)) return AutoRegisterResult.NotEligible
        val command = ConfirmCaptureCommand.fromCandidate(candidate) ?: return AutoRegisterResult.NotEligible
        return when (val result = confirm(command, CaptureConfirmationMode.AUTOMATIC)) {
            is CaptureConfirmationResult.Registered -> AutoRegisterResult.Registered(result.transactionId)
            is CaptureConfirmationResult.AccessDenied -> {
                flag(candidate, CaptureReason.AUTO_REGISTER_DENIED, candidate.confidence)
                AutoRegisterResult.AccessDenied
            }
            is CaptureConfirmationResult.SimilarTransactionWarning -> {
                flag(candidate, CaptureReason.POSSIBLE_DUPLICATE, CaptureConfidenceLevel.MEDIUM)
                AutoRegisterResult.NeedsReview
            }
            is CaptureConfirmationResult.Rejected -> AutoRegisterResult.NeedsReview
            CaptureConfirmationResult.Discarded, CaptureConfirmationResult.NotFound -> AutoRegisterResult.NotEligible
        }
    }

    private suspend fun flag(candidate: CaptureCandidateRecord, reason: CaptureReason, level: CaptureConfidenceLevel) {
        candidates.updateConfidence(
            candidate.ownerId, candidate.id, level, (candidate.confidenceReasons + reason).distinct(), clock.millis(),
        )
    }

    companion object {
        fun isEligible(candidate: CaptureCandidateRecord): Boolean =
            candidate.status == CaptureCandidateStatus.PENDING &&
                candidate.confidence == CaptureConfidenceLevel.HIGH &&
                candidate.direction == CaptureDirection.EXPENSE &&
                (candidate.instrumentKind == CaptureInstrumentKind.DEBIT_ACCOUNT ||
                    candidate.instrumentKind == CaptureInstrumentKind.WALLET) &&
                !candidate.instrumentAmbiguous &&
                !candidate.occurredAtInferred &&
                candidate.accountId != null &&
                candidate.categoryId != null &&
                CaptureReason.POSSIBLE_DUPLICATE !in candidate.confidenceReasons
    }
}

/**
 * First link of the post-processing chain (order 0): persists the definitive confidence and reasons and
 * attempts HU-47 auto-registration. Later processors (review notice, order 100) see the resulting state.
 */
class ScoreAndAutoRegisterPostProcessor @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val scorer: CaptureConfidenceScorer,
    private val autoRegister: AutoRegisterCaptureCandidate,
    private val clock: Clock,
) : CaptureCandidatePostProcessor {
    override val order: Int = 0

    override suspend fun process(candidate: CaptureCandidateRecord) {
        if (candidate.status != CaptureCandidateStatus.PENDING) return
        val assessment = scorer.score(candidate)
        if (!candidates.updateConfidence(candidate.ownerId, candidate.id, assessment.level, assessment.reasons, clock.millis())) return
        autoRegister(candidate.copy(confidence = assessment.level, confidenceReasons = assessment.reasons))
    }
}
