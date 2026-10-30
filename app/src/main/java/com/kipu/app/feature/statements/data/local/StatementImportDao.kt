package com.kipu.app.feature.statements.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementImportDao {
    @Transaction
    @Query("SELECT * FROM statement_imports WHERE owner_id=:ownerId AND id=:importId")
    fun observeSnapshot(ownerId: String, importId: String): Flow<StatementImportWithLines?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(statement: StatementImportEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<StatementImportLineEntity>)

    @Query("SELECT * FROM statement_imports WHERE owner_id=:ownerId AND statement_fingerprint=:fingerprint")
    suspend fun findByFingerprint(ownerId: String, fingerprint: String): StatementImportEntity?

    @Query("SELECT * FROM statement_imports WHERE owner_id=:ownerId AND id=:importId")
    suspend fun get(ownerId: String, importId: String): StatementImportEntity?

    @Query("SELECT * FROM statement_import_lines WHERE owner_id=:ownerId AND id=:lineId")
    suspend fun getLine(ownerId: String, lineId: String): StatementImportLineEntity?

    @Query("SELECT * FROM statement_imports WHERE owner_id=:ownerId AND id=:importId")
    fun observe(ownerId: String, importId: String): Flow<StatementImportEntity?>

    @Query("SELECT * FROM statement_import_lines WHERE owner_id=:ownerId AND import_id=:importId ORDER BY position, id")
    fun observeLines(ownerId: String, importId: String): Flow<List<StatementImportLineEntity>>

    @Query("SELECT * FROM statement_imports WHERE owner_id=:ownerId AND status='PENDING' ORDER BY created_at DESC, id")
    fun observePending(ownerId: String): Flow<List<StatementImportEntity>>

    @Query("UPDATE statement_imports SET card_id=:cardId WHERE owner_id=:ownerId AND id=:importId AND status='PENDING'")
    suspend fun setCard(ownerId: String, importId: String, cardId: String?): Int

    @Query("""UPDATE statement_import_lines SET selected=:selected WHERE owner_id=:ownerId AND id=:lineId
        AND status='PENDING' AND import_id IN (SELECT id FROM statement_imports WHERE owner_id=:ownerId AND status='PENDING')""")
    suspend fun setLineSelected(ownerId: String, lineId: String, selected: Boolean): Int

    @Query("""UPDATE statement_import_lines SET description=:description, amount_minor=:amountMinor,
        occurred_date=:date, category_id=:categoryId, kind=:kind, merchant_id=:merchantId, source_account_id=:sourceAccountId
        WHERE owner_id=:ownerId AND id=:lineId AND status='PENDING' AND import_id IN
        (SELECT id FROM statement_imports WHERE owner_id=:ownerId AND status='PENDING')""")
    suspend fun editLine(ownerId: String, lineId: String, description: String, amountMinor: Long, date: String,
        categoryId: String?, kind: String, merchantId: String?, sourceAccountId: String?): Int

    @Query("""UPDATE statement_import_lines SET selected=:selected WHERE owner_id=:ownerId AND import_id=:importId
        AND status='PENDING' AND import_id IN (SELECT id FROM statement_imports WHERE owner_id=:ownerId AND status='PENDING')""")
    suspend fun setAllSelected(ownerId: String, importId: String, selected: Boolean)

    @Query("UPDATE statement_imports SET status='DISCARDED' WHERE owner_id=:ownerId AND id=:importId AND status='PENDING'")
    suspend fun discard(ownerId: String, importId: String): Int

    @Query("""UPDATE statement_import_lines SET status='REGISTERED', registered_movement_id=:movementId, selected=0
        WHERE owner_id=:ownerId AND id=:lineId AND status='PENDING' AND import_id IN
        (SELECT id FROM statement_imports WHERE owner_id=:ownerId AND status='PENDING')""")
    suspend fun markLineRegistered(ownerId: String, lineId: String, movementId: String): Int

    @Query("""UPDATE statement_import_lines SET status='EXCLUDED', selected=0
        WHERE owner_id=:ownerId AND import_id=:importId AND status='PENDING' AND selected=0""")
    suspend fun excludeUnselected(ownerId: String, importId: String)

    @Query("""UPDATE statement_imports SET status='COMPLETED' WHERE owner_id=:ownerId AND id=:importId AND status='PENDING'
        AND NOT EXISTS (SELECT 1 FROM statement_import_lines WHERE owner_id=:ownerId AND import_id=:importId AND status='PENDING' AND selected=1)""")
    suspend fun complete(ownerId: String, importId: String): Int

    @Query("""DELETE FROM statement_imports WHERE owner_id=:ownerId AND
        (expires_at<=:now OR (status IN ('DISCARDED','COMPLETED') AND created_at<:cutoff))""")
    suspend fun purgeExpired(ownerId: String, now: Long, cutoff: Long): Int
}
