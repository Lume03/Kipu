package com.kipu.app.feature.capture.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Every operation is scoped to [ownerId]; candidates never leave the device. */
interface CaptureCandidateStore {
    suspend fun pendingFollowups(ownerId: String): List<CaptureCandidateRecord>
    suspend fun completeFollowup(ownerId: String, id: String, flag: Int)
    fun observePending(ownerId: String): Flow<List<CaptureCandidateRecord>>
    fun observePendingExpectedMatches(ownerId: String): Flow<List<CaptureCandidateRecord>>
    /** Only unresolved association decisions on confirmed captures may change here. LINKED is atomic with payment. */
    suspend fun setExpectedMatchState(ownerId: String, id: String, state: ExpectedMatchState, now: Long): Boolean
    suspend fun get(ownerId: String, id: String): CaptureCandidateRecord?
    suspend fun findBySourceFingerprint(ownerId: String, fingerprint: String): CaptureCandidateRecord?

    /** Returns false when the owner already has a candidate for the same source event fingerprint. */
    suspend fun insertIfAbsent(record: CaptureCandidateRecord): Boolean

    /** Rewrites the editable/assessed fields of a candidate that is still PENDING. */
    suspend fun updatePending(record: CaptureCandidateRecord): Boolean
    suspend fun updateConfidence(
        ownerId: String,
        id: String,
        confidence: CaptureConfidenceLevel,
        reasons: List<CaptureReason>,
        now: Long,
    ): Boolean

    /** Resolves a PENDING candidate into CONFIRMED/AUTO_REGISTERED and clears its evidence. */
    suspend fun markResolved(
        ownerId: String,
        id: String,
        status: CaptureCandidateStatus,
        transactionId: String,
        resolutionKey: String,
        userCorrectedFields: List<String>,
        now: Long,
    ): Boolean

    /**
     * Atomically persists the final (user-edited) fields of a PENDING [record] and resolves it
     * into [status], clearing its evidence. Returns false if it was no longer PENDING.
     */
    suspend fun resolve(
        record: CaptureCandidateRecord,
        status: CaptureCandidateStatus,
        transactionId: String,
        resolutionKey: String,
        now: Long,
    ): Boolean

    /** Discards a PENDING candidate and clears its evidence. */
    suspend fun discard(ownerId: String, id: String, now: Long): Boolean

    /** Nulls the evidence of every candidate whose retention window ended. Returns rows cleared. */
    suspend fun purgeExpiredEvidence(now: Long): Int
}

interface CaptureSourceEventStore {
    /** Returns false when the event fingerprint was already seen for this owner. */
    suspend fun recordIfAbsent(event: CaptureSourceEvent): Boolean
    suspend fun find(ownerId: String, fingerprint: String): CaptureSourceEvent?
    suspend fun updateOutcome(ownerId: String, fingerprint: String, outcome: CaptureSourceOutcome)
}

interface ExpectedPaymentStore {
    /** Updates only EXPECTED occurrences; attended/skipped history is immutable. */
    suspend fun syncExpectedTemplate(ownerId: String, recurrenceId: String, amountMinor: Long?, currency: String, now: Long)
    /** Returns false when (owner, recurrence, due date) already has an expected payment. */
    suspend fun insertIfAbsent(payment: ExpectedPayment): Boolean
    suspend fun get(ownerId: String, id: String): ExpectedPayment?
    fun observeByOwner(ownerId: String): Flow<List<ExpectedPayment>>
    fun observeByRecurrence(ownerId: String, recurrenceId: String): Flow<List<ExpectedPayment>>

    /** EXPECTED payments with the same amount and currency whose due date is within [from]..[to]. */
    suspend fun findExpectedByAmount(
        ownerId: String,
        amountMinor: Long,
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ExpectedPayment>

    /** Moves EXPECTED -> ATTENDED. Returns false if it was not EXPECTED (idempotent link). */
    suspend fun markAttended(
        ownerId: String,
        id: String,
        transactionId: String,
        candidateId: String?,
        now: Long,
    ): Boolean
}
