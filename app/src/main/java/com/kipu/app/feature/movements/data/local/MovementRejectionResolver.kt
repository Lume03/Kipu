package com.kipu.app.feature.movements.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.movements.domain.MovementRevisionPlanner
import com.kipu.app.feature.movements.domain.model.MovementRevisionPayload
import com.kipu.app.feature.movements.domain.model.MovementType

/** P5: append auditable reversals just like a void; never delete the optimistic history. */
internal class MovementRejectionResolver(
    private val database: KipuDatabase,
    private val dao: MovementDao,
) {
    suspend fun discardTerminal(userId: String, transactionId: String): Boolean = database.withTransaction {
        val commands = dao.getAllOutboxForTransaction(userId, transactionId)
        if (commands.any { it.state in setOf("PENDING", "RETRY", "IN_FLIGHT") }) return@withTransaction false
        val rejected = commands.filter { it.state == "FAILED_PERMANENT" }
        val command = rejected.firstOrNull() ?: return@withTransaction false
        val tx = dao.getTransactionById(userId, transactionId) ?: return@withTransaction false
        // Previously compensated attempts already have immutable reconciliation evidence.
        if (dao.getRevisions(userId, transactionId).none { it.revisionId == "rejection:${command.idempotencyKey}" }) {
            if (!reject(command, command.lastErrorCode ?: "REJECTED", terminal = true)) return@withTransaction false
        }
        val restored = checkNotNull(dao.getTransactionById(userId, transactionId))
        if (commands.any { it.commandType == "REGISTER" && it.state == "FAILED_PERMANENT" }) {
            // Keep an audit tombstone (revision FKs are RESTRICT), remove it from financial history.
            check((tx.acknowledgedRevision ?: 0L) == 0L)
            dao.updateTransaction(restored.copy(status = "FAILED"))
        }
        rejected.forEach {
            dao.updateOutboxResult(userId, it.id, "DISCARDED", null, it.lastErrorCode, System.currentTimeMillis())
            dao.updateCommandReceiptStatus(userId, it.idempotencyKey, "REJECTED", System.currentTimeMillis())
        }
        true
    }

    suspend fun rejectClaimed(command: MovementOutboxEntity, errorCode: String): Boolean = reject(command, errorCode)

    private suspend fun reject(command: MovementOutboxEntity, errorCode: String, terminal: Boolean = false): Boolean = database.withTransaction {
        // Authentication failures retain the local commit for reauthentication/retry.
        require(errorCode !in setOf("HTTP_401", "HTTP_403"))
        val userId = command.userId
        val stored = dao.getOutboxById(userId, command.id) ?: return@withTransaction false
        if (stored.state != (if (terminal) "FAILED_PERMANENT" else "IN_FLIGHT") || stored.leaseUntil != command.leaseUntil ||
            stored.updatedAt != command.updatedAt) return@withTransaction false
        require(command.commandType in setOf("REGISTER", "REVISE_TRANSACTION", "VOID_TRANSACTION"))
        val transaction = checkNotNull(dao.getTransactionById(userId, command.aggregateId))
        // Specialized account/card handlers own their own compensation.
        if (transaction.cardId != null || transaction.installmentCount != null || !transaction.legacyKind.isNullOrBlank() ||
            (transaction.operationKind != null && !transaction.operationKind.equals("STANDARD", true))) {
            if (terminal) return@withTransaction false
            return@withTransaction dao.completeClaimedOutbox(command, "FAILED_PERMANENT", null, errorCode, "FAILED_PERMANENT")
        }
        val pending = if (terminal) dao.getAllOutboxForTransaction(userId, transaction.id).filter { it.state == "FAILED_PERMANENT" }
            else dao.getPendingOutboxForTransaction(userId, transaction.id)
        val revisions = dao.getRevisions(userId, transaction.id)
        val entries = dao.getLedgerEntriesForTransaction(userId, transaction.id)
        val effectsByEntry = dao.getLedgerEffects(userId, transaction.id).associateBy { it.ledgerEntryId }
        val rejectedCommands: List<MovementOutboxEntity>
        val reversals: List<LedgerEntryEntity>
        val restored: TransactionEntity
        val now = System.currentTimeMillis()
        val reconciliationId = "rejection:${command.idempotencyKey}"

        if (command.commandType == "REGISTER") {
            rejectedCommands = pending
            // Neutralize the entire still-applied net, including both transfer accounts and local children.
            reversals = entries.groupBy { it.accountId to it.currencyCode }.mapNotNull { (_, group) ->
                val net = group.fold(0L) { sum, entry -> Math.addExact(sum, entry.signedAmountMinor) }
                if (net == 0L) null else group.first().copy(signedAmountMinor = net)
            }
            restored = transaction.copy(status = "VOIDED", syncStatus = "FAILED_PERMANENT",
                revision = Math.addExact(transaction.revision, 1L), currentRevisionId = reconciliationId, updatedAt = now)
        } else {
            val rejected = checkNotNull(revisions.singleOrNull { it.commandId == command.idempotencyKey })
            check(rejected.provenance == "LOCAL" && (command.revisionId == null || rejected.revisionId == command.revisionId))
            // Follow the applied local branch, including unsent children; an unknown branch stays pending.
            val rejectedRevisions = revisions.filter { it.localRevision >= rejected.localRevision && it.provenance == "LOCAL" &&
                pending.any { command -> command.idempotencyKey == it.commandId } }
                .sortedBy { it.localRevision }
            check(rejectedRevisions.firstOrNull()?.revisionId == rejected.revisionId)
            check(transaction.currentRevisionId == rejectedRevisions.last().revisionId)
            check(rejectedRevisions.zipWithNext().all { (parent, child) -> child.baseRevision == parent.localRevision })
            rejectedCommands = rejectedRevisions.map { revision ->
                checkNotNull(pending.singleOrNull { it.idempotencyKey == revision.commandId })
            }
            val previous = checkNotNull(rejected.previousPayload?.let(MovementRevisionSnapshotCodec::decodeSnapshot))
            check(previous.id == transaction.id && previous.revision == rejected.baseRevision)
            if (terminal) check(previous.revision <= (transaction.acknowledgedRevision ?: 0L) ||
                dao.getOfficialRevision(userId, transaction.id, previous.revision) != null) {
                "Discard requires an acknowledged server head"
            }
            check(revisions.none { it.localRevision == previous.revision &&
                pending.any { command -> command.idempotencyKey == it.commandId } })
            val previousRevision = dao.getOfficialRevision(userId, transaction.id, previous.revision)?.revisionId
                ?: revisions.filter { it.localRevision == previous.revision && it.provenance != "RECONCILIATION" }
                    .singleOrNull()?.revisionId
            val rejectedIds = rejectedRevisions.map { it.revisionId }.toSet()
            val rejectedEffects = effectsByEntry.values.filter { it.revisionId in rejectedIds }
            reversals = rejectedEffects.map { effect -> checkNotNull(entries.singleOrNull { it.id == effect.ledgerEntryId }) }
            restored = transaction.copy(type = previous.type, operationKind = previous.operationKind,
                legacyKind = previous.legacyKind, amountMinor = previous.amountMinor, currencyCode = previous.currencyCode,
                sourceAccountId = previous.sourceAccountId, destinationAccountId = previous.destinationAccountId,
                categoryId = previous.categoryId, merchantId = previous.merchantId,
                merchantProvisionalText = previous.merchantProvisionalText, cardId = previous.cardId,
                installmentCount = previous.installmentCount, occurredAt = previous.occurredAt, note = previous.note,
                status = previous.status, revision = previous.revision, currentRevisionId = previousRevision,
                acknowledgedRevision = previous.revision, syncStatus = "SYNCED", updatedAt = now)

            // Missing effect identities must not silently leave part of an optimistic revision applied.
            val remaining = net(entries + reversals.map { it.copy(signedAmountMinor = Math.negateExact(it.signedAmountMinor)) })
            val officialEffects = if (restored.status == "VOIDED") emptyList() else MovementRevisionPlanner().financialEffects(
                MovementRevisionPayload(type = MovementType.valueOf(restored.type), operationKind = restored.operationKind,
                    amountMinor = restored.amountMinor, currency = restored.currencyCode,
                    sourceAccountId = restored.sourceAccountId, destinationAccountId = restored.destinationAccountId,
                    occurredAt = restored.occurredAt))
            val expected = officialEffects.associate { (it.accountId to it.currency) to it.signedAmountMinor }.filterValues { it != 0L }
            check(remaining == expected) { "Rejected revision requires ledger reconciliation" }
        }

        // Separate VOIDED evidence describes the discarded attempt, never the valid official head.
        dao.insertRevision(TransactionRevisionEntity(userId, reconciliationId, transaction.id, reconciliationId,
            "VOID", transaction.revision, Math.addExact(transaction.revision, 1L),
            MovementRevisionSnapshotCodec.encode(transaction),
            MovementRevisionSnapshotCodec.encode(transaction, status = "VOIDED", revision = Math.addExact(transaction.revision, 1L)),
            errorCode, "RECONCILIATION", now))
        reversals.forEachIndexed { ordinal, original ->
            val entryId = "$reconciliationId:$ordinal"
            dao.insertLedgerEntries(listOf(original.copy(id = entryId,
                signedAmountMinor = Math.negateExact(original.signedAmountMinor), createdAt = now)))
            // REGISTER may predate logical effect identities; revisions always retain their exact identity.
            val reversed = if (command.commandType == "REGISTER") null else effectsByEntry[original.id]
            dao.insertLedgerEffect(MovementLedgerEffectEntity(userId, reconciliationId, ordinal, transaction.id,
                reconciliationId, entryId, reversed?.commandId, reversed?.effectOrdinal))
            dao.insertLedgerAlias(MovementLedgerAliasEntity(userId, entryId, reconciliationId, ordinal))
        }
        dao.updateTransaction(restored)
        for ((accountId, currency) in entries.map { it.accountId to it.currencyCode }.distinct()) {
            BalanceProjectionStore(dao).rebuildBalanceFromLedger(userId, accountId, currency)
        }
        rejectedCommands.forEach { rejected ->
            dao.updateOutboxResult(userId, rejected.id, "FAILED_PERMANENT", null,
                if (rejected.id == command.id) errorCode else "DEPENDENCY_FAILED", now)
            dao.updateCommandReceiptStatus(userId, rejected.idempotencyKey, "REJECTED", now)
        }
        true
    }

    private fun net(entries: List<LedgerEntryEntity>): Map<Pair<String, String>, Long> =
        entries.groupBy { it.accountId to it.currencyCode }.mapValues { (_, group) ->
            group.fold(0L) { sum, entry -> Math.addExact(sum, entry.signedAmountMinor) }
        }.filterValues { it != 0L }
}
