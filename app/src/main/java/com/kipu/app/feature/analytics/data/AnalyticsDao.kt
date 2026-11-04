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
    @ColumnInfo(name = "occurred_at") val occurredAt: Long = 0L,
)

/** Operating income projection: same exclusions as the movement history, minus refunds and loan flows. */
data class AnalyticsIncomeEntity(
    val type: String, val status: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currency: String,
    @ColumnInfo(name = "account_id") val accountId: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)

/** Ledger effect dated by the movement that produced it, so back-dated entries land on the right day. */
data class AnalyticsLedgerRow(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "currency_code") val currency: String,
    @ColumnInfo(name = "delta_minor") val deltaMinor: Long,
    @ColumnInfo(name = "at_millis") val atMillis: Long,
)

/** Owner-scoped label for a filter option or a breakdown row. */
data class AnalyticsOptionRow(val id: String, val name: String)

data class AnalyticsCategoryRow(val id: String, val name: String, val icon: String?, val color: String?)

data class AnalyticsMerchantRow(
    val id: String, val name: String,
    @ColumnInfo(name = "logo_key") val logoKey: String?,
    @ColumnInfo(name = "brand_color") val brandColor: String?,
    @ColumnInfo(name = "logo_path") val logoPath: String?,
    @ColumnInfo(name = "logo_dark_path") val logoDarkPath: String?,
)

@Dao
interface AnalyticsDao {
    /** Category names come from the owner's presentation; income-only categories are not expense filters. */
    @Query("""
        SELECT c.id AS id, p.name AS name, p.icon AS icon, p.color AS color FROM categories c
        JOIN category_presentations p ON p.category_id = c.id AND p.user_id = :owner
        WHERE (c.user_id = :owner OR c.user_id IS NULL) AND c.category_type != 'INCOME'
        ORDER BY p.name COLLATE NOCASE ASC
    """)
    fun observeCategoryOptions(owner: String): Flow<List<AnalyticsCategoryRow>>

    @Query("SELECT id, alias AS name FROM accounts WHERE user_id = :owner ORDER BY is_archived ASC, alias COLLATE NOCASE ASC")
    fun observeAccountOptions(owner: String): Flow<List<AnalyticsOptionRow>>

    @Query("""
        SELECT id, COALESCE(NULLIF(TRIM(alias), ''), issuer) || ' •••• ' || last_four_digits AS name
        FROM cards WHERE user_id = :owner ORDER BY is_archived ASC, name COLLATE NOCASE ASC
    """)
    fun observeCardOptions(owner: String): Flow<List<AnalyticsOptionRow>>

    /** Only merchants already used by the owner's expenses, never the whole global catalog. */
    @Query("""
        SELECT DISTINCT m.id AS id, m.name AS name, m.logo_key AS logo_key, m.brand_color AS brand_color,
            m.logo_path AS logo_path, m.logo_dark_path AS logo_dark_path FROM transactions t
        JOIN merchant_catalog_cache m ON m.id = t.merchant_id AND (m.user_id IS NULL OR m.user_id = :owner)
        WHERE t.user_id = :owner AND t.type = 'EXPENSE'
        ORDER BY m.name COLLATE NOCASE ASC
    """)
    fun observeMerchantOptions(owner: String): Flow<List<AnalyticsMerchantRow>>

    @Query("""
        SELECT t.type, t.status, t.sync_status, t.operation_kind, t.legacy_kind, t.amount_minor, t.currency_code, t.category_id, t.source_account_id, t.card_id, t.merchant_id, t.occurred_at,
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

    @Query("""
        SELECT t.type, t.status, t.sync_status, t.amount_minor, t.currency_code,
          t.destination_account_id AS account_id, t.occurred_at
        FROM transactions t
        WHERE t.user_id = :owner AND t.type = 'INCOME'
          AND t.status IN ('ACTIVE','CONFIRMED','REVISED')
          AND t.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')
          AND t.occurred_at >= :fromMillis AND t.occurred_at < :toMillis
          AND COALESCE(UPPER(t.legacy_kind), '') NOT IN ('OPENING','ADJUSTMENT','REVERSAL','CARD_PAYMENT_CASH','REFUND','DEBT_DISBURSEMENT','DEBT_PAYMENT','GOAL_FUNDING')
          AND COALESCE(UPPER(t.operation_kind), '') NOT IN ('DEBT_DISBURSEMENT','DEBT_PAYMENT','REFUND','GOAL_FUNDING')
    """)
    fun observeIncomes(owner: String, fromMillis: Long, toMillis: Long): Flow<List<AnalyticsIncomeEntity>>

    /** Every ledger entry from [fromMillis] on, so the daily balance can be rebuilt backwards from today's balance. */
    @Query("""
        SELECT le.account_id AS account_id, le.currency_code AS currency_code, le.signed_amount_minor AS delta_minor,
          COALESCE(t.occurred_at, le.created_at) AS at_millis
        FROM ledger_entries le
        LEFT JOIN transactions t ON t.user_id = le.user_id AND t.id = le.transaction_id
        WHERE le.user_id = :owner AND COALESCE(t.occurred_at, le.created_at) >= :fromMillis
    """)
    fun observeLedgerRows(owner: String, fromMillis: Long): Flow<List<AnalyticsLedgerRow>>
}
