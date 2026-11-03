package com.kipu.app.feature.capture.data.local

import androidx.room.*

/** Local provenance only: no raw notification, OCR text or image bytes. */
@Entity(tableName = "capture_evidence", primaryKeys = ["owner_id", "source", "event_id"], indices = [
    Index(value = ["owner_id", "reference", "source"]),
    Index(value = ["owner_id", "root"]),
])
data class CaptureEvidenceEntity(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val source: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val root: String,
    val reference: String?,
    @ColumnInfo(defaultValue = "0") val separated: Boolean = false,
)

@Dao
interface CaptureEvidenceDao {
    @Query("SELECT * FROM capture_evidence WHERE owner_id=:owner")
    suspend fun all(owner: String): List<CaptureEvidenceEntity>
    @Query("SELECT * FROM capture_evidence WHERE owner_id=:owner AND source=:source AND event_id=:event LIMIT 1")
    suspend fun event(owner: String, source: String, event: String): CaptureEvidenceEntity?
    @Query("SELECT * FROM capture_evidence WHERE owner_id=:owner AND reference=:reference")
    suspend fun byReference(owner: String, reference: String): List<CaptureEvidenceEntity>
    @Query("SELECT * FROM capture_evidence WHERE owner_id=:owner AND root=:root")
    suspend fun byRoot(owner: String, root: String): List<CaptureEvidenceEntity>
    @Query("""SELECT e.root,e.source,e.reference,
        COALESCE(t.amount_minor,c.amount_minor,r.amount_minor) AS amount,
        COALESCE(t.currency_code,c.currency,CASE WHEN r.candidate_id IS NOT NULL THEN 'PEN' END) AS currency,
        CASE WHEN t.id IS NOT NULL THEN t.type='INCOME' WHEN c.id IS NOT NULL THEN CASE WHEN c.direction IS NULL THEN NULL ELSE c.direction='INCOME' END ELSE r.is_income END AS income,
        COALESCE(t.merchant_provisional_text,c.merchant_source_text,r.recipient) AS counterparty,
        COALESCE(t.occurred_at,c.occurred_at,r.occurred_at) AS time
        FROM capture_evidence e
        LEFT JOIN capture_candidates c ON c.owner_id=e.owner_id AND e.root='capture:' || c.id
        LEFT JOIN receipt_candidates r ON r.owner_id=e.owner_id AND e.root='receipt:' || r.candidate_id
        LEFT JOIN transactions t ON t.user_id=c.owner_id AND t.id=c.resolved_transaction_id
        WHERE e.owner_id=:owner AND NOT EXISTS(SELECT 1 FROM capture_evidence separated WHERE separated.owner_id=e.owner_id AND separated.root=e.root AND separated.separated=1)
        AND COALESCE(c.status,r.status) != 'DISCARDED'
        AND (t.id IS NULL OR (t.status='ACTIVE' AND t.type IN ('INCOME','EXPENSE') AND COALESCE(t.operation_kind,'') != 'REFUND'))
        AND COALESCE(t.amount_minor,c.amount_minor,r.amount_minor)=:amount
        AND COALESCE(t.occurred_at,c.occurred_at,r.occurred_at) BETWEEN :start AND :end""")
    suspend fun nearbyFacts(owner: String, amount: Long, start: Long, end: Long): List<com.kipu.app.feature.capture.domain.CorrelationFacts>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: CaptureEvidenceEntity): Long
    @Query("UPDATE capture_evidence SET separated=1 WHERE owner_id=:owner AND root=:root")
    suspend fun separate(owner: String, root: String)
    @Query("UPDATE capture_evidence SET root=:target WHERE owner_id=:owner AND root=:root")
    suspend fun move(owner: String, root: String, target: String)
}
