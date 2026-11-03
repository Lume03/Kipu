package com.kipu.app.feature.capture.data.local

import androidx.room.*

/** Local provenance only: no raw notification, OCR text or image bytes. */
@Entity(tableName = "capture_evidence", primaryKeys = ["owner_id", "source", "event_id"], indices = [
    Index(value = ["owner_id", "reference", "source"], unique = true),
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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: CaptureEvidenceEntity): Long
    @Query("UPDATE capture_evidence SET separated=1 WHERE owner_id=:owner AND root=:root")
    suspend fun separate(owner: String, root: String)
    @Query("UPDATE capture_evidence SET root=:target WHERE owner_id=:owner AND root=:root")
    suspend fun move(owner: String, root: String, target: String)
}
