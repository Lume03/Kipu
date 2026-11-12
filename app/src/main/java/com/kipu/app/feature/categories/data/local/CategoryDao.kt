package com.kipu.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MovementClassificationTuple(
    val id: String,
    val category_id: String?,
    val merchant_id: String?,
    val merchant_provisional_text: String?,
    val merchant_raw_text: String? = null,
)

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category_sync_outbox WHERE user_id=:userId AND state NOT IN ('COMPLETED','DISCARDED') ORDER BY created_at,rowid")
    suspend fun getUnresolvedCommands(userId: String): List<CategorySyncOutboxEntity>


    @androidx.room.Transaction
    suspend fun complete(command: CategorySyncOutboxEntity, state: String, error: String?, nextAttemptAt: Long?, now: Long) {
        val stored=getCommand(command.userId,command.operationId) ?: return
        if(stored.state!="IN_FLIGHT" || stored.updatedAt!=command.updatedAt || stored.nextAttemptAt!=command.nextAttemptAt) return
        updateOutboxCommand(command.copy(state=state,errorCode=error,nextAttemptAt=nextAttemptAt,attemptCount=command.attemptCount+1,updatedAt=now))
        if(com.kipu.app.core.sync.OutboxPolicy.isTerminal(state)) blockSuccessors(command.userId,command.aggregateType,command.aggregateId,command.createdAt,command.operationId)
    }


    @Query("SELECT EXISTS(SELECT 1 FROM category_sync_outbox WHERE user_id=:userId AND state IN ('PENDING','ERROR','IN_FLIGHT'))")
    suspend fun hasOutstanding(userId: String): Boolean


    @Query("UPDATE category_sync_outbox SET state='BLOCKED', error_code='BLOCKED_BY_PREVIOUS' WHERE user_id=:userId AND state IN ('PENDING','ERROR','WAITING_FOR_AUTH') AND EXISTS (SELECT 1 FROM category_sync_outbox p WHERE p.user_id=category_sync_outbox.user_id AND p.aggregate_id=category_sync_outbox.aggregate_id AND p.aggregate_type=category_sync_outbox.aggregate_type AND (p.created_at<category_sync_outbox.created_at OR (p.created_at=category_sync_outbox.created_at AND p.rowid<category_sync_outbox.rowid)) AND p.state IN ('FAILED','FAILED_PERMANENT','CONFLICT'))")
    suspend fun blockBehindFailures(userId: String)


    @Query("UPDATE category_sync_outbox SET state='WAITING_FOR_AUTH' WHERE user_id=:userId AND state IN ('PENDING','ERROR')")
    suspend fun waitForAuth(userId: String)


    @Query("SELECT * FROM category_sync_outbox WHERE user_id=:userId AND state='PENDING' ORDER BY created_at,rowid")
    suspend fun getPendingOutboxCommands(userId: String): List<CategorySyncOutboxEntity>


    @Query("SELECT * FROM category_sync_outbox WHERE user_id=:userId AND operation_id=:id")
    suspend fun getCommand(userId: String, id: String): CategorySyncOutboxEntity?

    @androidx.room.Transaction
    suspend fun claim(userId: String, now: Long): CategorySyncOutboxEntity? {
        val command = getReadyCommands(userId, now).firstOrNull() ?: return null
        val leased = command.copy(state="IN_FLIGHT", nextAttemptAt=now+60_000, updatedAt=now)
        updateOutboxCommand(leased)
        return leased
    }

    @Query("UPDATE category_sync_outbox SET state='BLOCKED', error_code='BLOCKED_BY_PREVIOUS', next_attempt_at=NULL WHERE user_id=:userId AND aggregate_type=:type AND aggregate_id=:aggregateId AND (created_at>:createdAt OR (created_at=:createdAt AND rowid>(SELECT rowid FROM category_sync_outbox WHERE user_id=:userId AND operation_id=:id))) AND state IN ('PENDING','ERROR','WAITING_FOR_AUTH')")
    suspend fun blockSuccessors(userId: String, type: String, aggregateId: String, createdAt: Long, id: String)

    @Query("UPDATE category_sync_outbox SET state='PENDING', error_code=NULL, next_attempt_at=NULL WHERE user_id=:userId AND aggregate_type=:type AND aggregate_id=:aggregateId AND state='BLOCKED' AND NOT EXISTS (SELECT 1 FROM category_sync_outbox p WHERE p.user_id=:userId AND p.aggregate_type=:type AND p.aggregate_id=:aggregateId AND p.state IN ('FAILED','FAILED_PERMANENT','CONFLICT'))")
    suspend fun unblock(userId: String, type: String, aggregateId: String)

    @Query("UPDATE category_sync_outbox SET state='PENDING', next_attempt_at=NULL WHERE user_id=:userId AND state='WAITING_FOR_AUTH'")
    suspend fun resumeAuth(userId: String)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIfAbsent(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE user_id = :userId OR user_id IS NULL ORDER BY created_at ASC")
    fun observeCategoriesForUser(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_id = :userId OR user_id IS NULL")
    suspend fun getCategoriesForUser(userId: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE user_id = :userId AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active = 1")
    suspend fun countActiveCustomRoots(userId: String): Int

    // Presentation
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresentation(presentation: CategoryPresentationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPresentationsIfAbsent(presentations: List<CategoryPresentationEntity>)

    @Update
    suspend fun updatePresentation(presentation: CategoryPresentationEntity)

    @Query("SELECT * FROM category_presentations WHERE user_id = :userId AND category_id = :categoryId")
    suspend fun getPresentation(userId: String, categoryId: String): CategoryPresentationEntity?

    @Query("SELECT * FROM category_presentations WHERE user_id = :userId")
    fun observePresentationsForUser(userId: String): Flow<List<CategoryPresentationEntity>>

    // Conflicts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: CategoryConflictEntity)

    @Update
    suspend fun updateConflict(conflict: CategoryConflictEntity)

    @Query("SELECT * FROM category_conflicts WHERE user_id = :userId AND status = 'OPEN' ORDER BY created_at DESC")
    fun observeOpenConflicts(userId: String): Flow<List<CategoryConflictEntity>>

    @Query("SELECT * FROM category_conflicts WHERE id = :id")
    suspend fun getConflict(id: String): CategoryConflictEntity?

    // Movement Classification
    @Query("SELECT id, category_id, merchant_id, merchant_provisional_text, merchant_raw_text FROM financial_movements WHERE id = :movementId AND user_id = :userId")
    fun observeMovementClassification(movementId: String, userId: String): Flow<MovementClassificationTuple?>

    @Query("SELECT merchant_raw_text FROM financial_movements WHERE id = :movementId AND user_id = :userId")
    suspend fun getMerchantRawText(movementId: String, userId: String): String?

    @Query("UPDATE financial_movements SET merchant_raw_text = :rawText WHERE id = :movementId AND user_id = :userId AND merchant_raw_text IS NULL")
    suspend fun setMerchantRawTextIfAbsent(movementId: String, userId: String, rawText: String): Int

    @Query("""
        UPDATE financial_movements 
        SET category_id = :categoryId, 
            merchant_id = :merchantId, 
            merchant_provisional_text = :provisionalText 
        WHERE id = :movementId AND user_id = :userId
    """)
    suspend fun updateMovementClassification(
        movementId: String,
        userId: String,
        categoryId: String?,
        merchantId: String?,
        provisionalText: String?
    ): Int

    @Query("UPDATE financial_movements SET category_id = NULL WHERE id = :movementId AND user_id = :userId")
    suspend fun clearCategoryClassification(movementId: String, userId: String): Int

    @Query("UPDATE financial_movements SET merchant_id = NULL, merchant_provisional_text = NULL WHERE id = :movementId AND user_id = :userId")
    suspend fun clearMerchantClassification(movementId: String, userId: String): Int

    // Outbox
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxCommand(command: CategorySyncOutboxEntity)

    @Query("SELECT c.* FROM category_sync_outbox c WHERE c.user_id=:userId AND c.state IN ('PENDING','ERROR','IN_FLIGHT') AND (c.next_attempt_at IS NULL OR c.next_attempt_at<=:now) AND NOT EXISTS (SELECT 1 FROM category_sync_outbox p WHERE p.user_id=c.user_id AND p.aggregate_type=c.aggregate_type AND p.aggregate_id=c.aggregate_id AND (p.created_at<c.created_at OR (p.created_at=c.created_at AND p.rowid<c.rowid)) AND p.state NOT IN ('COMPLETED','DISCARDED')) ORDER BY c.created_at,c.rowid LIMIT 1")
    suspend fun getReadyCommands(userId: String, now: Long): List<CategorySyncOutboxEntity>

    @Update
    suspend fun updateOutboxCommand(command: CategorySyncOutboxEntity)
}
