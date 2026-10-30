package com.kipu.app.feature.statements.domain

import com.kipu.app.core.finance.domain.model.Currency
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class StatementImportStatus { PENDING, COMPLETED, DISCARDED }
enum class StatementImportLineStatus { PENDING, REGISTERED, EXCLUDED }

data class StatementImport(
    val id: String,
    val ownerId: String,
    val statementFingerprint: String,
    val issuer: String,
    val cardLastFour: String,
    val cycleStart: LocalDate,
    val cycleEnd: LocalDate,
    val dueDate: LocalDate?,
    val reconciledPen: Boolean?,
    val reconciledUsd: Boolean?,
    val cardId: String?,
    val status: StatementImportStatus,
    val createdAt: Long,
    val expiresAt: Long,
)

data class StatementImportLine(
    val id: String,
    val importId: String,
    val ownerId: String,
    val lineFingerprint: String,
    val position: Int,
    val kind: StatementLineKind,
    val processDate: LocalDate,
    val occurredDate: LocalDate,
    val description: String,
    val amountMinor: Long,
    val currency: Currency,
    val installmentCount: Int?,
    val selected: Boolean,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val sourceAccountId: String? = null,
    val possibleDuplicate: Boolean = false,
    val status: StatementImportLineStatus = StatementImportLineStatus.PENDING,
    val registeredMovementId: String? = null,
)

data class StatementImportSnapshot(val statement: StatementImport, val lines: List<StatementImportLine>)
data class StatementImportCandidate(val line: StatementLine, val selected: Boolean, val possibleDuplicate: Boolean)
data class StatementImportCreation(val statement: StatementImport, val created: Boolean)
data class StatementImportLineEdit(
    val description: String,
    val amountMinor: Long,
    val occurredDate: LocalDate,
    val categoryId: String?,
    val kind: StatementLineKind,
    val merchantId: String? = null,
    val sourceAccountId: String? = null,
) {
    init {
        require(description.isNotBlank() && description.length <= 120)
        require(amountMinor > 0)
        require(kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE)
    }
}

interface StatementImportStore {
    suspend fun create(ownerId: String, parsed: ParsedStatement, matchedCardId: String?, lines: List<StatementImportCandidate>): StatementImportCreation
    suspend fun findByFingerprint(ownerId: String, fingerprint: String): StatementImport?
    fun observe(ownerId: String, importId: String): Flow<StatementImportSnapshot?>
    fun observePending(ownerId: String): Flow<List<StatementImport>>
    suspend fun setCard(ownerId: String, importId: String, cardId: String?): Boolean
    suspend fun setLineSelected(ownerId: String, lineId: String, selected: Boolean): Boolean
    suspend fun editLine(ownerId: String, lineId: String, edit: StatementImportLineEdit): Boolean
    suspend fun setAllSelected(ownerId: String, importId: String, selected: Boolean)
    suspend fun discard(ownerId: String, importId: String): Boolean
    suspend fun markLineRegistered(ownerId: String, lineId: String, movementId: String): Boolean
    suspend fun complete(ownerId: String, importId: String): Boolean
    suspend fun purgeExpired(ownerId: String, now: Long): Int
}
