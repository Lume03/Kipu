package com.kipu.app.feature.movements.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kipu.app.feature.movements.data.local.TransactionEntity

@Entity(
    tableName = "transaction_links",
    primaryKeys = ["user_id", "source_transaction_id", "target_transaction_id", "link_type"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "source_transaction_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "target_transaction_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["user_id", "source_transaction_id"]),
        Index(value = ["user_id", "target_transaction_id"], unique = true),
    ],
)
data class TransactionLinkEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "source_transaction_id") val sourceTransactionId: String,
    @ColumnInfo(name = "target_transaction_id") val targetTransactionId: String,
    @ColumnInfo(name = "link_type") val linkType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class RefundConsumptionRow(
    @ColumnInfo(name = "purchase_id") val purchaseId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

@Dao
interface TransactionLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(link: TransactionLinkEntity)

    @Query("SELECT * FROM transaction_links WHERE user_id=:userId AND target_transaction_id=:refundId")
    suspend fun getByRefundId(userId: String, refundId: String): TransactionLinkEntity?

    @Query(
        """
        SELECT links.source_transaction_id AS purchase_id, SUM(refund.amount_minor) AS amount_minor
        FROM transaction_links AS links
        INNER JOIN transactions AS purchase
            ON purchase.user_id = links.user_id AND purchase.id = links.source_transaction_id
        INNER JOIN transactions AS refund
            ON refund.user_id = links.user_id AND refund.id = links.target_transaction_id
        WHERE links.user_id = :userId
          AND links.link_type = 'REFUND'
          AND links.source_transaction_id IN (:purchaseIds)
          AND purchase.type = 'EXPENSE'
          AND purchase.status IN ('ACTIVE', 'CONFIRMED', 'REVISED')
          AND refund.type = 'INCOME'
          AND refund.operation_kind = 'REFUND'
          AND refund.status IN ('ACTIVE', 'CONFIRMED', 'REVISED')
          AND refund.sync_status != 'FAILED_PERMANENT'
        GROUP BY links.source_transaction_id
        """,
    )
    suspend fun getValidRefundAmounts(userId: String, purchaseIds: List<String>): List<RefundConsumptionRow>
}
