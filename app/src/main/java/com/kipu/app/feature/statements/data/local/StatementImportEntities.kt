package com.kipu.app.feature.statements.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import java.time.LocalDate

data class StatementImportWithLines(
    @Embedded val statement: StatementImportEntity,
    @Relation(parentColumn = "id", entityColumn = "import_id") val lines: List<StatementImportLineEntity>,
)

@Entity(tableName = "statement_imports", indices = [
    Index(value = ["owner_id", "statement_fingerprint"], unique = true),
    Index(value = ["owner_id", "status", "created_at"]),
    Index(value = ["owner_id", "id"], unique = true),
])
data class StatementImportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "statement_fingerprint") val statementFingerprint: String,
    val issuer: String,
    @ColumnInfo(name = "card_last_four") val cardLastFour: String,
    @ColumnInfo(name = "cycle_start") val cycleStart: String,
    @ColumnInfo(name = "cycle_end") val cycleEnd: String,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "reconciled_pen") val reconciledPen: Boolean?,
    @ColumnInfo(name = "reconciled_usd") val reconciledUsd: Boolean?,
    @ColumnInfo(name = "card_id") val cardId: String?,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(defaultValue = "'CREDIT_CARD'") val source: String = "CREDIT_CARD",
    @ColumnInfo(name = "account_reference") val accountReference: String? = null,
    @ColumnInfo(name = "previous_balance_pen_minor") val previousBalancePenMinor: Long? = null,
    @ColumnInfo(name = "account_debits_pen_minor") val accountDebitsPenMinor: Long? = null,
    @ColumnInfo(name = "account_credits_pen_minor") val accountCreditsPenMinor: Long? = null,
    @ColumnInfo(name = "closing_balance_pen_minor") val closingBalancePenMinor: Long? = null,
    @ColumnInfo(name = "previous_balance_usd_minor") val previousBalanceUsdMinor: Long? = null,
    @ColumnInfo(name = "account_debits_usd_minor") val accountDebitsUsdMinor: Long? = null,
    @ColumnInfo(name = "account_credits_usd_minor") val accountCreditsUsdMinor: Long? = null,
    @ColumnInfo(name = "closing_balance_usd_minor") val closingBalanceUsdMinor: Long? = null,

) {
    fun toDomain() = StatementImport(id, ownerId, statementFingerprint, issuer, cardLastFour,
        LocalDate.parse(cycleStart), LocalDate.parse(cycleEnd), dueDate?.let(LocalDate::parse),
        reconciledPen, reconciledUsd, cardId, StatementImportStatus.valueOf(status), createdAt, expiresAt, StatementSource.valueOf(source), accountReference,
        listOf(
            Currency.PEN to StatementAccountTotals(previousBalancePenMinor, accountDebitsPenMinor, accountCreditsPenMinor, closingBalancePenMinor),
            Currency.USD to StatementAccountTotals(previousBalanceUsdMinor, accountDebitsUsdMinor, accountCreditsUsdMinor, closingBalanceUsdMinor),
        ).filter { (_, totals) -> listOf(totals.previousBalanceMinor, totals.debitsMinor, totals.creditsMinor, totals.closingBalanceMinor).any { it != null } }.toMap())
}

@Entity(tableName = "statement_import_lines", foreignKeys = [ForeignKey(
    entity = StatementImportEntity::class, parentColumns = ["owner_id", "id"],
    childColumns = ["owner_id", "import_id"], onDelete = ForeignKey.CASCADE,
)], indices = [
    Index(value = ["import_id", "line_fingerprint"], unique = true),
    Index(value = ["owner_id", "import_id"]),
])
data class StatementImportLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "import_id") val importId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "line_fingerprint") val lineFingerprint: String,
    val position: Int,
    val kind: String,
    @ColumnInfo(name = "process_date") val processDate: String,
    @ColumnInfo(name = "occurred_date") val occurredDate: String,
    val description: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "installment_count") val installmentCount: Int?,
    val selected: Boolean,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "merchant_id") val merchantId: String?,
    @ColumnInfo(name = "source_account_id") val sourceAccountId: String?,
    @ColumnInfo(name = "possible_duplicate") val possibleDuplicate: Boolean,
    val status: String,
    @ColumnInfo(name = "registered_movement_id") val registeredMovementId: String?,
    @ColumnInfo(name = "possible_own_transfer", defaultValue = "0") val possibleOwnTransfer: Boolean = false,
) {
    fun toDomain() = StatementImportLine(id, importId, ownerId, lineFingerprint, position,
        StatementLineKind.valueOf(kind), LocalDate.parse(processDate), LocalDate.parse(occurredDate),
        description, amountMinor, Currency.valueOf(currency), installmentCount, selected, categoryId,
        merchantId, sourceAccountId, possibleDuplicate, StatementImportLineStatus.valueOf(status), registeredMovementId, possibleOwnTransfer)
}
