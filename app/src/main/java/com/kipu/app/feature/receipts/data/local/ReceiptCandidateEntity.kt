package com.kipu.app.feature.receipts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateStatus

@Entity(
    tableName = "receipt_candidates",
    indices = [
        Index(value = ["owner_id", "status", "created_at"]),
        Index(value = ["owner_id", "image_fingerprint"], unique = true),
        Index(value = ["owner_id", "reference_fingerprint"], unique = true),
    ],
)
data class ReceiptCandidateEntity(
    @PrimaryKey @ColumnInfo(name = "candidate_id") val candidateId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val provider: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long?,
    val recipient: String?,
    val reference: String?,
    @ColumnInfo(name = "reference_fingerprint") val referenceFingerprint: String?,
    @ColumnInfo(name = "image_fingerprint") val imageFingerprint: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_income") val isIncome: Boolean?,
) {
    fun toDomain() = ReceiptCandidate(candidateId, ownerId, provider, amountMinor, occurredAt, recipient, reference,
        referenceFingerprint, imageFingerprint, ReceiptCandidateStatus.valueOf(status), createdAt, updatedAt, isIncome)
}
