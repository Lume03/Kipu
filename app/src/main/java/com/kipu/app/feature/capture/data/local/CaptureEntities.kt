package com.kipu.app.feature.capture.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "capture_source_events",
    primaryKeys = ["owner_id", "event_fingerprint"],
)
data class CaptureSourceEventEntity(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "event_fingerprint") val eventFingerprint: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "outcome") val outcome: String,
)

@Entity(
    tableName = "capture_candidates",
    indices = [
        Index(value = ["owner_id", "status", "created_at"]),
        Index(value = ["owner_id", "source_event_fingerprint"], unique = true),
    ],
)
data class CaptureCandidateEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "source_event_fingerprint") val sourceEventFingerprint: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "direction") val direction: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long?,
    @ColumnInfo(name = "occurred_at_inferred") val occurredAtInferred: Boolean,
    @ColumnInfo(name = "merchant_source_text") val merchantSourceText: String?,
    @ColumnInfo(name = "merchant_id") val merchantId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "card_last4") val cardLast4: String?,
    @ColumnInfo(name = "instrument_kind") val instrumentKind: String?,
    @ColumnInfo(name = "account_id") val accountId: String?,
    @ColumnInfo(name = "card_id") val cardId: String?,
    @ColumnInfo(name = "instrument_ambiguous") val instrumentAmbiguous: Boolean,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "confidence_reasons") val confidenceReasons: String,
    @ColumnInfo(name = "credit_mode") val creditMode: String?,
    @ColumnInfo(name = "installment_count") val installmentCount: Int?,
    @ColumnInfo(name = "resolved_transaction_id") val resolvedTransactionId: String?,
    @ColumnInfo(name = "resolution_key") val resolutionKey: String?,
    @ColumnInfo(name = "evidence_snippet") val evidenceSnippet: String?,
    @ColumnInfo(name = "evidence_expires_at") val evidenceExpiresAt: Long?,
    @ColumnInfo(name = "user_corrected_fields") val userCorrectedFields: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
    @ColumnInfo(name = "expected_match_state") val expectedMatchState: String? = null,
    @ColumnInfo(name = "followups_pending", defaultValue = "0") val followupsPending: Int = 0,
)
