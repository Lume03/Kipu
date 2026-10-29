package com.kipu.app.feature.recurrences.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.kipu.app.feature.capture.data.local.CaptureCandidateEntity
import kotlinx.coroutines.flow.Flow

/** Local-only expected occurrence of a recurrence; never synced and never a ledger movement. */
@Entity(
    tableName = "recurrence_expected_payments",
    indices = [
        Index(value = ["owner_id", "recurrence_id", "due_date"], unique = true),
        Index(value = ["owner_id", "status", "due_date"]),
        Index(value = ["owner_id", "matched_candidate_id"], unique = true),
    ],
)
data class ExpectedPaymentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "recurrence_id") val recurrenceId: String,
    /** ISO yyyy-MM-dd, same convention as RecurrenceEntity.next_due_date (sortable as text). */
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "expected_amount_minor") val expectedAmountMinor: Long?,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "matched_transaction_id") val matchedTransactionId: String?,
    @ColumnInfo(name = "matched_candidate_id") val matchedCandidateId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface ExpectedPaymentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: ExpectedPaymentEntity): Long

    @Query("SELECT * FROM recurrence_expected_payments WHERE owner_id=:ownerId AND id=:id")
    suspend fun get(ownerId: String, id: String): ExpectedPaymentEntity?

    @Query("SELECT * FROM recurrence_expected_payments WHERE owner_id=:ownerId ORDER BY due_date DESC")
    fun observeByOwner(ownerId: String): Flow<List<ExpectedPaymentEntity>>

    @Query(
        "SELECT * FROM recurrence_expected_payments WHERE owner_id=:ownerId AND recurrence_id=:recurrenceId " +
            "ORDER BY due_date DESC",
    )
    fun observeByRecurrence(ownerId: String, recurrenceId: String): Flow<List<ExpectedPaymentEntity>>

    @Query(
        """SELECT * FROM recurrence_expected_payments WHERE owner_id=:ownerId AND status='EXPECTED'
        AND expected_amount_minor=:amountMinor AND currency=:currency AND due_date BETWEEN :fromDay AND :toDay
        ORDER BY due_date ASC""",
    )
    suspend fun findExpectedByAmount(
        ownerId: String,
        amountMinor: Long,
        currency: String,
        fromDay: String,
        toDay: String,
    ): List<ExpectedPaymentEntity>

    @Query(
        """UPDATE recurrence_expected_payments SET status='ATTENDED', matched_transaction_id=:transactionId,
        matched_candidate_id=:candidateId, updated_at=:now
        WHERE owner_id=:ownerId AND id=:id AND status='EXPECTED'""",
    )
    suspend fun updateAttended(ownerId: String, id: String, transactionId: String, candidateId: String?, now: Long): Int

    @Query("SELECT * FROM recurrence_expected_payments WHERE owner_id=:ownerId AND matched_candidate_id=:candidateId LIMIT 1")
    suspend fun findLinked(ownerId: String, candidateId: String): ExpectedPaymentEntity?

    @Query("SELECT * FROM capture_candidates WHERE owner_id=:ownerId AND id=:candidateId")
    suspend fun getCandidate(ownerId: String, candidateId: String): CaptureCandidateEntity?

    @Query("UPDATE capture_candidates SET expected_match_state='LINKED', updated_at=:now WHERE owner_id=:ownerId AND id=:candidateId")
    suspend fun markCandidateLinked(ownerId: String, candidateId: String, now: Long): Int

    @Query("""UPDATE recurrence_expected_payments SET expected_amount_minor=:amountMinor, currency=:currency, updated_at=:now
        WHERE owner_id=:ownerId AND recurrence_id=:recurrenceId AND status='EXPECTED'""")
    suspend fun syncExpectedTemplate(ownerId: String, recurrenceId: String, amountMinor: Long?, currency: String, now: Long): Int

    /** Both records and the exclusion check share the same SQLite write transaction. */
    @Transaction
    suspend fun markAttended(ownerId: String, id: String, transactionId: String, candidateId: String?, now: Long): Int {
        if (candidateId == null) return updateAttended(ownerId, id, transactionId, null, now)
        val candidate = getCandidate(ownerId, candidateId) ?: return 0
        if (candidate.status !in setOf("CONFIRMED", "AUTO_REGISTERED") || candidate.resolvedTransactionId != transactionId || candidate.direction != "EXPENSE") return 0
        val linked = findLinked(ownerId, candidateId)
        if (linked != null) return if (linked.id == id && linked.status == "ATTENDED" && linked.matchedTransactionId == transactionId) 1 else 0
        if (candidate.expectedMatchState in setOf("LINKED", "NONE")) return 0
        val payment = get(ownerId, id) ?: return 0
        if (payment.status != "EXPECTED" || payment.expectedAmountMinor != candidate.amountMinor || payment.currency != candidate.currency) return 0
        val occurred = candidate.occurredAt ?: return 0
        val date = java.time.Instant.ofEpochMilli(occurred).atZone(java.time.ZoneId.of("America/Lima")).toLocalDate()
        if (java.time.LocalDate.parse(payment.dueDate) !in date.minusDays(3)..date.plusDays(3)) return 0
        if (updateAttended(ownerId, id, transactionId, candidateId, now) != 1) return 0
        check(markCandidateLinked(ownerId, candidateId, now) == 1)
        return 1
    }
}
