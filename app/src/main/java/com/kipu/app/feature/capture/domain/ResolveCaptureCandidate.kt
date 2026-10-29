package com.kipu.app.feature.capture.domain

import java.time.Clock
import javax.inject.Inject

fun interface CaptureCandidateDiscardedListener {
    suspend fun onDiscarded(candidate: CaptureCandidateRecord)
}

sealed interface CaptureDiscardResult {
    /** [alreadyDiscarded] is true on a repeated discard (idempotent). */
    data class Discarded(val alreadyDiscarded: Boolean) : CaptureDiscardResult
    data object AlreadyRegistered : CaptureDiscardResult
    data object NotFound : CaptureDiscardResult
}

/** HU-51: discards without touching the ledger and erases the evidence. Never teaches anything (HU-50). */
class DiscardCaptureCandidate @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val clock: Clock,
    private val discardedListeners: Set<@JvmSuppressWildcards CaptureCandidateDiscardedListener> = emptySet(),
    private val transactions: com.kipu.app.core.database.DatabaseTransactionRunner,
    private val receipts: CaptureRegistrationReceipts,
    private val followups: CaptureResolutionFollowups,
) {
    suspend operator fun invoke(ownerId: String, candidateId: String): CaptureDiscardResult {
        val result = transactions {
            if (receipts.find(ownerId, CaptureResolutionKey.of(ownerId, candidateId)) != null) {
                CaptureDiscardResult.AlreadyRegistered
            } else discardAtomic(ownerId, candidateId)
        }
        if (result is CaptureDiscardResult.Discarded) {
            try { followups.process(ownerId, candidateId) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* Cleanup remains durable and retryable. */ }
        }
        return result
    }

    private suspend fun discardAtomic(ownerId: String, candidateId: String): CaptureDiscardResult {
        if (discard(ownerId, candidateId)) return CaptureDiscardResult.Discarded(alreadyDiscarded = false)
        return when (candidates.get(ownerId, candidateId)?.status) {
            null -> CaptureDiscardResult.NotFound
            CaptureCandidateStatus.DISCARDED -> CaptureDiscardResult.Discarded(alreadyDiscarded = true)
            CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED -> CaptureDiscardResult.AlreadyRegistered
            // Lost a race with a concurrent edit; retrying is safe because discard only applies to PENDING.
            CaptureCandidateStatus.PENDING -> if (discard(ownerId, candidateId)) {
                CaptureDiscardResult.Discarded(alreadyDiscarded = false)
            } else {
                CaptureDiscardResult.NotFound
            }
        }
    }

    private suspend fun discard(ownerId: String, id: String): Boolean {
        return candidates.discard(ownerId, id, clock.millis())
    }
}

/** Prefilled manual form for a candidate. Confirm it with ConfirmCaptureCandidate in MANUAL_DRAFT mode. */
data class CaptureManualDraft(
    val candidateId: String,
    /** Same key as an inbox confirmation, so the candidate can never be registered twice. */
    val idempotencyKey: String,
    val direction: CaptureDirection?,
    val amountMinor: Long?,
    val currency: String?,
    val occurredAt: Long?,
    val merchantId: String?,
    val merchantText: String?,
    val categoryId: String?,
    val instrumentKind: CaptureInstrumentKind?,
    val accountId: String?,
    val cardId: String?,
    val creditMode: CreditMode?,
    val installmentCount: Int?,
)

sealed interface CaptureManualConversionResult {
    data class Draft(val draft: CaptureManualDraft) : CaptureManualConversionResult

    /** Already resolved or discarded; nothing to convert. */
    data object NotPending : CaptureManualConversionResult
    data object NotFound : CaptureManualConversionResult
}

/**
 * HU-51 with Premium no longer active: keeps the candidate and returns a manual draft from the values
 * already extracted (no new extraction, no notification content read again).
 */
class ConvertCandidateToManual @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val gate: CaptureAccessGate,
) {
    suspend operator fun invoke(ownerId: String, candidateId: String): CaptureManualConversionResult {
        if (gate.currentOwnerId() != ownerId) return CaptureManualConversionResult.NotFound
        val candidate = candidates.get(ownerId, candidateId) ?: return CaptureManualConversionResult.NotFound
        if (candidate.status != CaptureCandidateStatus.PENDING) return CaptureManualConversionResult.NotPending
        return CaptureManualConversionResult.Draft(
            CaptureManualDraft(
                candidateId = candidate.id,
                idempotencyKey = CaptureResolutionKey.of(ownerId, candidate.id),
                direction = candidate.direction,
                amountMinor = candidate.amountMinor,
                currency = candidate.currency,
                occurredAt = candidate.occurredAt,
                merchantId = candidate.merchantId,
                merchantText = candidate.merchantSourceText,
                categoryId = candidate.categoryId,
                instrumentKind = candidate.instrumentKind,
                accountId = candidate.accountId,
                cardId = candidate.cardId,
                creditMode = candidate.creditMode,
                installmentCount = candidate.installmentCount,
            ),
        )
    }
}
