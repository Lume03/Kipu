package com.kipu.app.feature.statements.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

@Singleton
class RoomStatementImportStore @Inject constructor(
    private val database: KipuDatabase,
    private val dao: StatementImportDao,
    private val clock: Clock,
    private val matching: StatementImportMatching,
) : StatementImportStore {
    override suspend fun create(ownerId: String, parsed: ParsedStatement, matchedCardId: String?, lines: List<StatementImportCandidate>): StatementImportCreation {
        val owner = owner(ownerId)
        return database.withTransaction {
            dao.findByFingerprint(owner, parsed.fingerprint)?.let {
                if (it.status != StatementImportStatus.DISCARDED.name) return@withTransaction StatementImportCreation(it.toDomain(), false)
                check(dao.deleteDiscarded(owner, it.id) == 1)
            }
            require(matchedCardId == null || (parsed.source == StatementSource.CREDIT_CARD && validCard(owner, matchedCardId)))
            val now = clock.millis()
            val row = StatementImportEntity(UUID.randomUUID().toString(), owner, parsed.fingerprint, parsed.issuer,
                parsed.cardLastFour, parsed.cycleStart.toString(), parsed.cycleEnd.toString(), parsed.dueDate?.toString(),
                parsed.reconciliation[Currency.PEN]?.matches, parsed.reconciliation[Currency.USD]?.matches,
                matchedCardId, StatementImportStatus.PENDING.name, now, now + RETENTION_MILLIS, parsed.source.name, parsed.accountReference?.takeLast(4),
                previousBalancePenMinor = parsed.totals[Currency.PEN]?.previousBalanceMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                accountDebitsPenMinor = parsed.totals[Currency.PEN]?.accountDebitsMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                accountCreditsPenMinor = parsed.totals[Currency.PEN]?.accountCreditsMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                closingBalancePenMinor = parsed.totals[Currency.PEN]?.closingBalanceMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                previousBalanceUsdMinor = parsed.totals[Currency.USD]?.previousBalanceMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                accountDebitsUsdMinor = parsed.totals[Currency.USD]?.accountDebitsMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                accountCreditsUsdMinor = parsed.totals[Currency.USD]?.accountCreditsMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT },
                closingBalanceUsdMinor = parsed.totals[Currency.USD]?.closingBalanceMinor.takeIf { parsed.source == StatementSource.DEPOSIT_ACCOUNT })
            dao.insert(row)
            dao.insertLines(lines.filter { it.line.kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE }.mapIndexed { position, candidate ->
                val line = candidate.line
                StatementImportLineEntity(UUID.randomUUID().toString(), row.id, owner, line.fingerprint, position,
                    line.kind.name, line.processDate.toString(), line.occurredDate.toString(), line.description.take(120),
                    line.amountMinor, line.currency.name, line.installmentCount, candidate.selected, null, null, null,
                    candidate.possibleDuplicate, StatementImportLineStatus.PENDING.name, null, line.possibleOwnTransfer, originalDescription = line.description.take(120))
            })
            StatementImportCreation(row.toDomain(), true)
        }
    }

    override suspend fun findByFingerprint(ownerId: String, fingerprint: String) = dao.findByFingerprint(owner(ownerId), fingerprint)?.takeUnless { it.status == StatementImportStatus.DISCARDED.name }?.toDomain()

    override fun observe(ownerId: String, importId: String) = dao.observeSnapshot(owner(ownerId), importId).map { row ->
        row?.takeIf { it.statement.status != StatementImportStatus.PENDING.name || it.statement.expiresAt > clock.millis() }
            ?.let { StatementImportSnapshot(it.statement.toDomain(), it.lines.sortedBy { line -> line.position }.map(StatementImportLineEntity::toDomain)) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePending(ownerId: String) = dao.observePending(owner(ownerId)).flatMapLatest { rows ->
        flow {
            while (true) {
                val now = clock.millis()
                val active = rows.filter { it.expiresAt > now }
                emit(active.map(StatementImportEntity::toDomain))
                val next = active.minOfOrNull { it.expiresAt } ?: break
                delay((next - now).coerceAtLeast(1L))
            }
        }
    }

    override suspend fun setCard(ownerId: String, importId: String, cardId: String?): Boolean {
        val owner = owner(ownerId)
        return database.withTransaction {
            val existing = dao.get(owner, importId) ?: return@withTransaction false
            if (existing.status != StatementImportStatus.PENDING.name) return@withTransaction false
            val deposit = existing.source == StatementSource.DEPOSIT_ACCOUNT.name
            val debit = if (deposit && cardId != null) matching.activeDebitCards(owner).singleOrNull { it.id == cardId } else null
            if (cardId != null && (if (deposit) debit == null else !validCard(owner, cardId))) return@withTransaction false
            val lines = dao.observeLines(owner, importId).first()
            if (debit != null && lines.any { it.currency != debit.currency.name }) return@withTransaction false
            val base = existing.statementFingerprint.substringBefore(':')
            val fingerprint = if (deposit) existing.statementFingerprint else cardId?.let { statementCardFingerprint(base, it) } ?: base
            val duplicate = dao.findByFingerprint(owner, fingerprint)
            if (duplicate != null && duplicate.id != importId) {
                if (duplicate.status != StatementImportStatus.DISCARDED.name) return@withTransaction false
                check(dao.deleteDiscarded(owner, duplicate.id) == 1)
            }
            if (dao.setCard(owner, importId, cardId, fingerprint) != 1) return@withTransaction false
            if (debit != null) for (line in lines.filter { it.status == "PENDING" }) {
                val duplicateLine = matching.hasAccountDuplicate(owner, debit.linkedAccountId, line.amountMinor,
                    Currency.valueOf(line.currency), java.time.LocalDate.parse(line.occurredDate), StatementLineKind.valueOf(line.kind))
                dao.setDuplicate(owner, line.id, duplicateLine)
            }
            true
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

    override suspend fun setMerchantSuggestion(ownerId: String, lineId: String, merchantId: String?) =
        dao.setMerchantSuggestion(owner(ownerId), lineId, merchantId) == 1
    override suspend fun chooseMerchant(ownerId: String, lineId: String, merchantId: String?, categoryId: String?) =
        dao.chooseMerchant(owner(ownerId), lineId, merchantId, categoryId) == 1
    override suspend fun setRememberMerchantAlias(ownerId: String, lineId: String, remember: Boolean) =
        dao.setRememberMerchantAlias(owner(ownerId), lineId, remember) == 1
    override suspend fun setRememberMerchantCategory(ownerId: String, lineId: String, remember: Boolean) =
        dao.setRememberMerchantCategory(owner(ownerId), lineId, remember) == 1

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
