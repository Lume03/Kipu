package com.kipu.app.feature.analytics.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Expense projection follows MovementDao.getExpensesInPeriod and TransactionLinkDao refund rules. */
data class AnalyticsExpenseEntity(
    val type: String, val status: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    @ColumnInfo(name = "operation_kind") val operationKind: String?,
    @ColumnInfo(name = "legacy_kind") val legacyKind: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "refunded_minor") val refundedMinor: Long,
    @ColumnInfo(name = "currency_code") val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "source_account_id") val accountId: String?,
    @ColumnInfo(name = "card_id") val cardId: String?,
    @ColumnInfo(name = "merchant_id") val merchantId: String?,
)

/** Owner-scoped label for a filter option or a breakdown row. */
data class AnalyticsOptionRow(val id: String, val name: String)

@Dao
interface AnalyticsDao {
    /** Category names come from the owner's presentation; income-only categories are not expense filters. */
    @Query("""
        SELECT c.id AS id, p.name AS name FROM categories c
        JOIN category_presentations p ON p.category_id = c.id AND p.user_id = :owner
        WHERE (c.user_id = :owner OR c.user_id IS NULL) AND c.category_type != 'INCOME'
        ORDER BY p.name COLLATE NOCASE ASC
    """)
    fun observeCategoryOptions(owner: String): Flow<List<AnalyticsOptionRow>>

    @Query("SELECT id, alias AS name FROM accounts WHERE user_id = :owner ORDER BY is_archived ASC, alias COLLATE NOCASE ASC")
    fun observeAccountOptions(owner: String): Flow<List<AnalyticsOptionRow>>

    @Query("""
        SELECT id, COALESCE(NULLIF(TRIM(alias), ''), issuer) || ' •••• ' || last_four_digits AS name
        FROM cards WHERE user_id = :owner ORDER BY is_archived ASC, name COLLATE NOCASE ASC
    """)
    fun observeCardOptions(owner: String): Flow<List<AnalyticsOptionRow>>

    /** Only merchants already used by the owner's expenses, never the whole global catalog. */
    @Query("""
        SELECT DISTINCT m.id AS id, m.name AS name FROM transactions t
        JOIN merchant_catalog_cache m ON m.id = t.merchant_id AND (m.user_id IS NULL OR m.user_id = :owner)
        WHERE t.user_id = :owner AND t.type = 'EXPENSE'
        ORDER BY m.name COLLATE NOCASE ASC
    """)
    fun observeMerchantOptions(owner: String): Flow<List<AnalyticsOptionRow>>

    @Query("""
        SELECT t.type, t.status, t.sync_status, t.operation_kind, t.legacy_kind, t.amount_minor, t.currency_code, t.category_id, t.source_account_id, t.card_id, t.merchant_id,
          COALESCE((SELECT SUM(r.amount_minor) FROM transaction_links l
            JOIN transactions r ON r.user_id = l.user_id AND r.id = l.target_transaction_id
            WHERE l.user_id = t.user_id AND l.source_transaction_id = t.id AND l.link_type = 'REFUND'
              AND r.type = 'INCOME' AND r.operation_kind = 'REFUND'
              AND r.status IN ('ACTIVE','CONFIRMED','REVISED')
              AND r.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')), 0) AS refunded_minor
        FROM transactions t
        WHERE t.user_id = :owner AND t.type = 'EXPENSE'
          AND t.status IN ('ACTIVE','CONFIRMED','REVISED')
          AND t.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')
          AND t.occurred_at >= :fromMillis AND t.occurred_at < :toMillis
          AND (t.operation_kind IS NULL OR UPPER(t.operation_kind) IN ('STANDARD','CARD_PURCHASE','DEBT_AMORTIZATION'))
          AND COALESCE(UPPER(t.legacy_kind), '') NOT IN ('DEBT_DISBURSEMENT','DEBT_PAYMENT','GOAL_FUNDING','REFUND','OPENING','ADJUSTMENT','REVERSAL','CARD_PAYMENT_CASH')
    """)
    fun observeExpenses(owner: String, fromMillis: Long, toMillis: Long): Flow<List<AnalyticsExpenseEntity>>
}
