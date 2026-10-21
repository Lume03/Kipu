package com.kipu.app.feature.receipts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptCandidateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity: ReceiptCandidateEntity): Long
    @Query("SELECT * FROM receipt_candidates WHERE owner_id=:ownerId AND status='PENDING' ORDER BY created_at DESC")
    fun observePending(ownerId: String): Flow<List<ReceiptCandidateEntity>>
    @Query("SELECT * FROM receipt_candidates WHERE owner_id=:ownerId AND candidate_id=:id")
    suspend fun get(ownerId: String, id: String): ReceiptCandidateEntity?
    @Query("SELECT * FROM receipt_candidates WHERE owner_id=:ownerId AND image_fingerprint=:fingerprint LIMIT 1")
    suspend fun findImage(ownerId: String, fingerprint: String): ReceiptCandidateEntity?
    @Query("SELECT * FROM receipt_candidates WHERE owner_id=:ownerId AND reference_fingerprint=:fingerprint LIMIT 1")
    suspend fun findReference(ownerId: String, fingerprint: String): ReceiptCandidateEntity?
    @Query("UPDATE receipt_candidates SET amount_minor=:amount, occurred_at=:occurred, recipient=:recipient, reference=:reference, reference_fingerprint=:fingerprint, is_income=COALESCE(:isIncome,is_income), updated_at=:now WHERE owner_id=:owner AND candidate_id=:id AND status='PENDING'")
    suspend fun edit(owner: String, id: String, amount: Long?, occurred: Long?, recipient: String?, reference: String?, fingerprint: String?, isIncome: Boolean?, now: Long): Int
    @Query("UPDATE receipt_candidates SET status='CONFIRMED', updated_at=:now WHERE owner_id=:owner AND candidate_id=:id AND status='PENDING'")
    suspend fun confirm(owner: String, id: String, now: Long): Int
    @Query("UPDATE receipt_candidates SET status='DISCARDED', updated_at=:now WHERE owner_id=:owner AND candidate_id=:id AND status='PENDING'")
    suspend fun discard(owner: String, id: String, now: Long): Int
}
