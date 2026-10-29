package com.kipu.app.feature.capture.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureSourceEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: CaptureSourceEventEntity): Long

    @Query("SELECT * FROM capture_source_events WHERE owner_id=:ownerId AND event_fingerprint=:fingerprint")
    suspend fun find(ownerId: String, fingerprint: String): CaptureSourceEventEntity?

    @Query("UPDATE capture_source_events SET outcome=:outcome WHERE owner_id=:ownerId AND event_fingerprint=:fingerprint")
    suspend fun updateOutcome(ownerId: String, fingerprint: String, outcome: String): Int
}

@Dao
interface CaptureCandidateDao {
    @Query("SELECT * FROM capture_candidates WHERE owner_id=:ownerId AND followups_pending != 0 ORDER BY created_at")
    suspend fun pendingFollowups(ownerId: String): List<CaptureCandidateEntity>

    @Query("UPDATE capture_candidates SET followups_pending=(followups_pending & ~:flag) WHERE owner_id=:ownerId AND id=:id")
    suspend fun completeFollowup(ownerId: String, id: String, flag: Int)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: CaptureCandidateEntity): Long

    @Query("SELECT * FROM capture_candidates WHERE owner_id=:ownerId AND status='PENDING' ORDER BY created_at DESC")
    fun observePending(ownerId: String): Flow<List<CaptureCandidateEntity>>

    @Query("SELECT * FROM capture_candidates WHERE owner_id=:ownerId AND expected_match_state='PENDING_CHOICE' AND status IN ('CONFIRMED','AUTO_REGISTERED') ORDER BY created_at DESC")
    fun observePendingExpectedMatches(ownerId: String): Flow<List<CaptureCandidateEntity>>

    @Query("""UPDATE capture_candidates SET expected_match_state=:state, updated_at=:now
        WHERE owner_id=:ownerId AND id=:id AND status IN ('CONFIRMED','AUTO_REGISTERED')
        AND (expected_match_state IS NULL OR expected_match_state='PENDING_CHOICE')
        AND NOT EXISTS (SELECT 1 FROM recurrence_expected_payments WHERE owner_id=:ownerId AND matched_candidate_id=:id)""")
    suspend fun setExpectedMatchState(ownerId: String, id: String, state: String, now: Long): Int

    @Query("SELECT * FROM capture_candidates WHERE owner_id=:ownerId AND id=:id")
    suspend fun get(ownerId: String, id: String): CaptureCandidateEntity?

    @Query("SELECT * FROM capture_candidates WHERE owner_id=:ownerId AND source_event_fingerprint=:fingerprint LIMIT 1")
    suspend fun findBySourceFingerprint(ownerId: String, fingerprint: String): CaptureCandidateEntity?

    @Query(
        """UPDATE capture_candidates SET direction=:direction, amount_minor=:amountMinor, currency=:currency,
        occurred_at=:occurredAt, occurred_at_inferred=:occurredAtInferred, merchant_source_text=:merchantSourceText,
        merchant_id=:merchantId, category_id=:categoryId, card_last4=:cardLast4, instrument_kind=:instrumentKind,
        account_id=:accountId, card_id=:cardId, instrument_ambiguous=:instrumentAmbiguous, confidence=:confidence,
        confidence_reasons=:confidenceReasons, credit_mode=:creditMode, installment_count=:installmentCount,
        user_corrected_fields=:userCorrectedFields, updated_at=:now
        WHERE owner_id=:ownerId AND id=:id AND status='PENDING'""",
    )
    suspend fun updatePending(
        ownerId: String,
        id: String,
        direction: String?,
        amountMinor: Long?,
        currency: String?,
        occurredAt: Long?,
        occurredAtInferred: Boolean,
        merchantSourceText: String?,
        merchantId: String?,
        categoryId: String?,
        cardLast4: String?,
        instrumentKind: String?,
        accountId: String?,
        cardId: String?,
        instrumentAmbiguous: Boolean,
        confidence: String,
        confidenceReasons: String,
        creditMode: String?,
        installmentCount: Int?,
        userCorrectedFields: String,
        now: Long,
    ): Int

    @Query(
        "UPDATE capture_candidates SET confidence=:confidence, confidence_reasons=:reasons, updated_at=:now " +
            "WHERE owner_id=:ownerId AND id=:id AND status='PENDING'",
    )
    suspend fun updateConfidence(ownerId: String, id: String, confidence: String, reasons: String, now: Long): Int

    @Query(
        """UPDATE capture_candidates SET status=:status, resolved_transaction_id=:transactionId,
        resolution_key=:resolutionKey, user_corrected_fields=:userCorrectedFields, evidence_snippet=NULL,
        evidence_expires_at=NULL, resolved_at=:now, updated_at=:now, followups_pending=7
        WHERE owner_id=:ownerId AND id=:id AND status='PENDING'""",
    )
    suspend fun markResolved(
        ownerId: String,
        id: String,
        status: String,
        transactionId: String,
        resolutionKey: String,
        userCorrectedFields: String,
        now: Long,
    ): Int

    @Query(
        """UPDATE capture_candidates SET status='DISCARDED', evidence_snippet=NULL, evidence_expires_at=NULL,
        resolved_at=:now, updated_at=:now, followups_pending=8 WHERE owner_id=:ownerId AND id=:id AND status='PENDING'""",
    )
    suspend fun discard(ownerId: String, id: String, now: Long): Int

    @Query(
        "UPDATE capture_candidates SET evidence_snippet=NULL, evidence_expires_at=NULL " +
            "WHERE evidence_expires_at IS NOT NULL AND evidence_expires_at<=:now",
    )
    suspend fun purgeExpiredEvidence(now: Long): Int
}
