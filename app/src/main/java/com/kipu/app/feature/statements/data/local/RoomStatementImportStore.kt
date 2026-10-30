package com.kipu.app.feature.statements.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class RoomStatementImportStore @Inject constructor(
    private val database: KipuDatabase,
    private val dao: StatementImportDao,
    private val clock: Clock,
) : StatementImportStore {
    override suspend fun create(ownerId: String, parsed: ParsedStatement, matchedCardId: String?, lines: List<StatementImportCandidate>): StatementImportCreation {
        val owner = owner(ownerId)
        return database.withTransaction {
            dao.findByFingerprint(owner, parsed.fingerprint)?.let { return@withTransaction StatementImportCreation(it.toDomain(), false) }
            require(matchedCardId == null || validCard(owner, matchedCardId))
            val now = clock.millis()
            val row = StatementImportEntity(UUID.randomUUID().toString(), owner, parsed.fingerprint, parsed.issuer,
                parsed.cardLastFour, parsed.cycleStart.toString(), parsed.cycleEnd.toString(), parsed.dueDate?.toString(),
                parsed.reconciliation[Currency.PEN]?.matches, parsed.reconciliation[Currency.USD]?.matches,
                matchedCardId, StatementImportStatus.PENDING.name, now, now + RETENTION_MILLIS)
            dao.insert(row)
            dao.insertLines(lines.filter { it.line.kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE }.mapIndexed { position, candidate ->
                val line = candidate.line
                StatementImportLineEntity(UUID.randomUUID().toString(), row.id, owner, line.fingerprint, position,
                    line.kind.name, line.processDate.toString(), line.occurredDate.toString(), line.description.take(120),
                    line.amountMinor, line.currency.name, line.installmentCount, candidate.selected, null, null, null,
                    candidate.possibleDuplicate, StatementImportLineStatus.PENDING.name, null)
            })
            StatementImportCreation(row.toDomain(), true)
        }
    }

    override suspend fun findByFingerprint(ownerId: String, fingerprint: String) = dao.findByFingerprint(owner(ownerId), fingerprint)?.toDomain()

    override fun observe(ownerId: String, importId: String) = dao.observeSnapshot(owner(ownerId), importId).map { row ->
        row?.let { StatementImportSnapshot(it.statement.toDomain(), it.lines.sortedBy { line -> line.position }.map(StatementImportLineEntity::toDomain)) }
    }

    override fun observePending(ownerId: String) = dao.observePending(owner(ownerId)).map { it.map(StatementImportEntity::toDomain) }

    override suspend fun setCard(ownerId: String, importId: String, cardId: String?): Boolean {
        val owner = owner(ownerId)
        return database.withTransaction {
            if (cardId != null && !validCard(owner, cardId)) return@withTransaction false
            dao.setCard(owner, importId, cardId) == 1
        }
    }

    override suspend fun setLineSelected(ownerId: String, lineId: String, selected: Boolean) = dao.setLineSelected(owner(ownerId), lineId, selected) == 1

    override suspend fun editLine(ownerId: String, lineId: String, edit: StatementImportLineEdit): Boolean {
        val owner = owner(ownerId)
        return database.withTransaction {
            val line = dao.getLine(owner, lineId) ?: return@withTransaction false
            // Structural financial operations cannot be converted into a different operation by editing.
            val editableKinds = setOf(StatementLineKind.PURCHASE, StatementLineKind.REFUND, StatementLineKind.INTEREST,
                StatementLineKind.INSURANCE, StatementLineKind.FEE, StatementLineKind.UNKNOWN)
            if (line.kind != edit.kind.name && (StatementLineKind.valueOf(line.kind) !in editableKinds || edit.kind !in editableKinds)) return@withTransaction false
            dao.editLine(owner, lineId, edit.description, edit.amountMinor, edit.occurredDate.toString(),
                edit.categoryId, edit.kind.name, edit.merchantId, edit.sourceAccountId) == 1
        }
    }

    override suspend fun setAllSelected(ownerId: String, importId: String, selected: Boolean) = dao.setAllSelected(owner(ownerId), importId, selected)
    override suspend fun discard(ownerId: String, importId: String) = dao.discard(owner(ownerId), importId) == 1
    override suspend fun markLineRegistered(ownerId: String, lineId: String, movementId: String): Boolean {
        require(movementId.isNotBlank())
        return dao.markLineRegistered(owner(ownerId), lineId, movementId) == 1
    }
    override suspend fun complete(ownerId: String, importId: String): Boolean = database.withTransaction {
        val owner = owner(ownerId)
        if (dao.complete(owner, importId) != 1) return@withTransaction false
        dao.excludeUnselected(owner, importId)
        true
    }
    override suspend fun purgeExpired(ownerId: String, now: Long) = dao.purgeExpired(owner(ownerId), now, now - RETENTION_MILLIS)

    private fun owner(value: String) = UUID.fromString(value).toString()
    private suspend fun validCard(owner: String, cardId: String) = database.cardDao().getById(owner, cardId)?.let {
        it.type == "CREDIT" && !it.isArchived
    } == true

    companion object { const val RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1000L }
}
