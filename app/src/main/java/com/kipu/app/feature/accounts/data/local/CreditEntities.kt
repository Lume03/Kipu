package com.kipu.app.feature.accounts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded
import com.kipu.app.feature.movements.data.local.TransactionEntity

@Entity(
    tableName = "credit_installments",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id", "transaction_id", "installment_number"], unique = true),
        Index(value = ["user_id", "due_date", "status"]),
    ],
)
data class CreditInstallmentEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "installment_number") val installmentNumber: Int,
    /** Epoch day; domain and API boundaries convert this to ISO `date`. */
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "principal_minor") val principalMinor: Long,
    @ColumnInfo(name = "interest_minor") val interestMinor: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "purchase_type", defaultValue = "'DIRECT'") val purchaseType: String = "DIRECT",
    @ColumnInfo(name = "financing", defaultValue = "'INTEREST_FREE'") val financing: String = "INTEREST_FREE",
    @ColumnInfo(name = "installments_count", defaultValue = "1") val installmentsCount: Int = 1,
    @ColumnInfo(name = "tea_bps") val teaBps: Int? = null,
    @ColumnInfo(name = "rate_source") val rateSource: String? = null,
    @ColumnInfo(name = "rounding_policy", defaultValue = "'LAST_INSTALLMENT_RESIDUAL'")
    val roundingPolicy: String = "LAST_INSTALLMENT_RESIDUAL",
    @ColumnInfo(name = "billing_closing_date") val billingClosingDate: Long? = null,
)

@Entity(
    tableName = "credit_payment_allocations",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "payment_transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CreditInstallmentEntity::class,
            onUpdate = ForeignKey.CASCADE,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "installment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id", "payment_transaction_id"]),
        Index(value = ["user_id", "installment_id"]),
    ],
)
data class CreditPaymentAllocationEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "payment_transaction_id") val paymentTransactionId: String,
    @ColumnInfo(name = "installment_id") val installmentId: String,
    @ColumnInfo(name = "allocated_minor") val allocatedMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class CreditInstallmentOutstanding(
    @Embedded val installment: CreditInstallmentEntity,
    @ColumnInfo(name = "outstanding_minor") val outstandingMinor: Long,
    @ColumnInfo(name = "purchase_occurred_at") val purchaseOccurredAt: Long,
)

data class CreditInstallmentBalance(
    @Embedded val installment: CreditInstallmentEntity,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "card_name") val cardName: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "merchant") val merchant: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "paid_minor") val paidMinor: Long,
    @ColumnInfo(name = "refunded_minor") val refundedMinor: Long,
    @ColumnInfo(name = "outstanding_minor") val outstandingMinor: Long,
    @ColumnInfo(name = "settled_at_millis") val settledAtMillis: Long? = null,
)

/** Refund credit is consumed once, FIFO over the unpaid portions of one purchase. */
internal const val CREDIT_INSTALLMENT_BALANCES_SQL = """
WITH paid AS (
    SELECT a.user_id, a.installment_id, SUM(a.allocated_minor) AS paid_minor, MAX(payment.occurred_at) AS paid_at
    FROM credit_payment_allocations a
    JOIN transactions payment ON payment.user_id = a.user_id AND payment.id = a.payment_transaction_id
    WHERE payment.status IN ('ACTIVE','CONFIRMED','REVISED')
      AND payment.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')
    GROUP BY a.user_id, a.installment_id
), refunds AS (
    SELECT links.user_id, links.source_transaction_id, SUM(refund.amount_minor) AS refunded_minor, MAX(refund.occurred_at) AS refund_at
    FROM transaction_links links
    JOIN transactions refund ON refund.user_id = links.user_id AND refund.id = links.target_transaction_id
    WHERE links.link_type = 'REFUND' AND refund.type = 'INCOME' AND refund.operation_kind = 'REFUND'
      AND refund.status IN ('ACTIVE','CONFIRMED','REVISED')
      AND refund.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')
    GROUP BY links.user_id, links.source_transaction_id
), base AS (
    SELECT i.*, t.card_id, t.currency_code, t.occurred_at AS purchase_occurred_at,
           COALESCE(t.merchant_provisional_text, merchant.name) AS merchant, t.note AS description,
           COALESCE(c.alias, c.issuer) AS card_name, c.is_archived AS card_archived,
           COALESCE(paid.paid_minor, 0) AS paid_minor, paid.paid_at, refunds.refund_at,
           COALESCE(refunds.refunded_minor, 0) AS purchase_refunded_minor,
           MAX(i.principal_minor - COALESCE(paid.paid_minor, 0), 0) AS before_refund_minor
    FROM credit_installments i
    JOIN transactions t ON t.user_id = i.user_id AND t.id = i.transaction_id
    JOIN cards c ON c.user_id = t.user_id AND c.id = t.card_id
    LEFT JOIN merchant_catalog_cache merchant ON merchant.id = t.merchant_id
    LEFT JOIN paid ON paid.user_id = i.user_id AND paid.installment_id = i.id
    LEFT JOIN refunds ON refunds.user_id = i.user_id AND refunds.source_transaction_id = i.transaction_id
    WHERE i.deleted_at IS NULL AND i.status != 'VOIDED'
      AND t.status IN ('ACTIVE','CONFIRMED','REVISED') AND t.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')
), credited AS (
    SELECT b.*, MIN(b.before_refund_minor, MAX(b.purchase_refunded_minor - COALESCE((
        SELECT SUM(prior.before_refund_minor) FROM base prior
        WHERE prior.user_id = b.user_id AND prior.transaction_id = b.transaction_id
          AND prior.installment_number < b.installment_number
    ), 0), 0)) AS refunded_minor
    FROM base b
), balances AS (
    SELECT credited.*, before_refund_minor - refunded_minor AS outstanding_minor,
           CASE WHEN before_refund_minor - refunded_minor = 0 THEN
               CASE WHEN refunded_minor > 0 THEN NULLIF(MAX(COALESCE(paid_at, 0), COALESCE(refund_at, 0)), 0)
                    ELSE paid_at END
               ELSE NULL END AS settled_at_millis
    FROM credited
)
"""

@androidx.room.Dao
interface CreditDao {
    @androidx.room.Query(CREDIT_INSTALLMENT_BALANCES_SQL +
        "SELECT * FROM balances WHERE user_id = :userId AND card_id = :cardId ORDER BY due_date, purchase_occurred_at, installment_number, id")
    fun observeInstallmentBalancesForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<List<CreditInstallmentBalance>>

    @androidx.room.Query(CREDIT_INSTALLMENT_BALANCES_SQL +
        "SELECT * FROM balances WHERE user_id = :userId ORDER BY due_date, purchase_occurred_at, installment_number, id")
    fun observeInstallmentBalancesForOwner(userId: String): kotlinx.coroutines.flow.Flow<List<CreditInstallmentBalance>>

    @androidx.room.Query(CREDIT_INSTALLMENT_BALANCES_SQL +
        "SELECT * FROM balances WHERE user_id = :userId AND card_id = :cardId ORDER BY due_date, installment_number, id")
    fun observeInstallmentsForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<List<CreditInstallmentEntity>>

    @androidx.room.Query(CREDIT_INSTALLMENT_BALANCES_SQL +
        "SELECT COALESCE(SUM(outstanding_minor), 0) FROM balances WHERE user_id = :userId AND card_id = :cardId")
    fun observeOutstandingBalanceForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<Long>

    fun observeOutstandingPrincipalForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<Long> =
        observeOutstandingBalanceForCard(userId, cardId)

    @androidx.room.Query(CREDIT_INSTALLMENT_BALANCES_SQL +
        "SELECT COALESCE(SUM(outstanding_minor), 0) FROM balances WHERE user_id = :userId AND card_id = :cardId")
    suspend fun getOutstandingBalanceForCard(userId: String, cardId: String): Long

    suspend fun getOutstandingPrincipalForCard(userId: String, cardId: String): Long =
        getOutstandingBalanceForCard(userId, cardId)

    @androidx.room.Query(
        """SELECT CASE WHEN COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0) < 0
             THEN -COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0)
             ELSE 0 END
           FROM cards c
           LEFT JOIN ledger_entries le ON le.user_id = c.user_id AND le.account_id = c.account_id
                AND le.role = 'LIABILITY'
           LEFT JOIN transactions t ON t.user_id = le.user_id AND t.id = le.transaction_id
           WHERE c.user_id = :userId AND c.id = :cardId AND c.type = 'CREDIT'""",
    )
    fun observeLedgerOutstandingPrincipalForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<Long>

    @androidx.room.Query(
        """SELECT CASE WHEN COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0) < 0
             THEN -COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0)
             ELSE 0 END
           FROM cards c
           LEFT JOIN ledger_entries le ON le.user_id = c.user_id AND le.account_id = c.account_id
                AND le.role = 'LIABILITY'
           LEFT JOIN transactions t ON t.user_id = le.user_id AND t.id = le.transaction_id
           WHERE c.user_id = :userId AND c.id = :cardId AND c.type = 'CREDIT'""",
    )
    suspend fun getLedgerOutstandingPrincipalForCard(userId: String, cardId: String): Long

    @androidx.room.Query(CREDIT_INSTALLMENT_BALANCES_SQL +
        "SELECT * FROM balances WHERE user_id = :userId AND card_id = :cardId AND outstanding_minor > 0 ORDER BY due_date, purchase_occurred_at, installment_number, id")
    suspend fun getOutstandingInstallmentsForCard(userId: String, cardId: String): List<CreditInstallmentOutstanding>

    @androidx.room.Query(
        """SELECT * FROM credit_installments
           WHERE user_id = :userId AND transaction_id = :transactionId AND deleted_at IS NULL
           ORDER BY installment_number""",
    )
    suspend fun getInstallmentsForTransaction(userId: String, transactionId: String): List<CreditInstallmentEntity>

    @androidx.room.Query("SELECT * FROM credit_installments WHERE user_id = :userId AND id = :installmentId AND deleted_at IS NULL")
    suspend fun getInstallmentById(userId: String, installmentId: String): CreditInstallmentEntity?

    @androidx.room.Query(
        """SELECT * FROM credit_payment_allocations
           WHERE user_id = :userId AND payment_transaction_id = :paymentTransactionId
           ORDER BY created_at, installment_id""",
    )
    suspend fun getAllocationsForPayment(userId: String, paymentTransactionId: String): List<CreditPaymentAllocationEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertInstallments(installments: List<CreditInstallmentEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertAllocations(allocations: List<CreditPaymentAllocationEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertPulledInstallments(installments: List<CreditInstallmentEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertPulledAllocations(allocations: List<CreditPaymentAllocationEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertInstallment(installment: CreditInstallmentEntity)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: CreditPaymentAllocationEntity)

    @androidx.room.Query(
        """UPDATE credit_installments SET status = :status, revision = :revision, updated_at = :updatedAt
           WHERE user_id = :userId AND id = :installmentId""",
    )
    suspend fun updateInstallmentStatus(
        userId: String,
        installmentId: String,
        status: String,
        revision: Long,
        updatedAt: Long,
    ): Int

    @androidx.room.Query(
        """UPDATE credit_installments SET id = :remoteId, due_date = :dueDate,
           principal_minor = :principalMinor, interest_minor = :interestMinor, status = :status,
           revision = :revision, updated_at = :updatedAt
           WHERE user_id = :userId AND transaction_id = :transactionId AND installment_number = :installmentNumber""",
    )
    suspend fun adoptRemoteInstallmentId(
        userId: String,
        transactionId: String,
        installmentNumber: Int,
        remoteId: String,
        dueDate: Long,
        principalMinor: Long,
        interestMinor: Long,
        status: String,
        revision: Long,
        updatedAt: Long,
    ): Int

    @androidx.room.Query(REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL)
    suspend fun refreshInstallmentStatuses(userId: String)

    @androidx.room.Query(
        """SELECT COALESCE(SUM(a.allocated_minor), 0) FROM credit_payment_allocations a
           JOIN transactions payment ON payment.user_id=a.user_id AND payment.id=a.payment_transaction_id
           WHERE a.user_id = :userId AND a.installment_id = :installmentId
             AND payment.status IN ('ACTIVE','CONFIRMED','REVISED') AND payment.sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')""",
    )
    suspend fun getAllocatedMinorForInstallment(userId: String, installmentId: String): Long

}

/** Correlated update so allocation and movement-status changes keep stored states consistent. */
internal const val REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL = """
UPDATE credit_installments SET status = COALESCE((
    SELECT CASE WHEN balance.outstanding_minor = 0 THEN 'PAID'
                WHEN balance.paid_minor + balance.refunded_minor > 0 THEN 'PARTIAL' ELSE 'PENDING' END
    FROM (
""" + CREDIT_INSTALLMENT_BALANCES_SQL + "SELECT * FROM balances" + """
    ) balance WHERE balance.user_id = credit_installments.user_id AND balance.id = credit_installments.id
), status)
WHERE user_id = :userId AND deleted_at IS NULL AND status != 'VOIDED'
"""
