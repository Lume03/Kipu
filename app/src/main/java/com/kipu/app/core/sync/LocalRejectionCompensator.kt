package com.kipu.app.core.sync

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.movements.data.local.*
import java.util.UUID

/** Must run inside the caller's Room transaction, together with the outbox rejection. */
internal class LocalRejectionCompensator(private val database: KipuDatabase) {
    suspend fun neutralize(userId: String, transactionId: String, reason: String) {
        val dao = database.movementDao()
        val tx = dao.getTransactionById(userId, transactionId) ?: return
        if (tx.source == "LOCAL_REJECTION") return
        val now = System.currentTimeMillis()
        val revisionId = "local-rejection:$transactionId"
        val next = tx.copy(status = "VOIDED", syncStatus = "FAILED_PERMANENT",
            source = "LOCAL_REJECTION", revision = Math.addExact(tx.revision, 1L),
            currentRevisionId = revisionId, updatedAt = now)
        dao.insertRevision(TransactionRevisionEntity(userId, revisionId, tx.id, revisionId,
            "LOCAL_REJECTION_VOID", tx.revision, next.revision,
            MovementRevisionSnapshotCodec.encode(tx), MovementRevisionSnapshotCodec.encode(next),
            reason, "RECONCILIATION", now))
        val entries = dao.getLedgerEntriesForTransaction(userId, tx.id)
        entries.groupBy { it.accountId to it.currencyCode }.values.forEachIndexed { ordinal, group ->
            val net = group.fold(0L) { sum, entry -> Math.addExact(sum, entry.signedAmountMinor) }
            if (net != 0L) {
                val id = UUID.nameUUIDFromBytes("$userId:$revisionId:$ordinal".toByteArray()).toString()
                dao.insertLedgerEntries(listOf(group.first().copy(id = id, role = "REVERSAL",
                    signedAmountMinor = Math.negateExact(net), createdAt = now)))
                dao.insertLedgerEffect(MovementLedgerEffectEntity(userId, revisionId, ordinal, tx.id,
                    revisionId, id, null, null))
                dao.insertLedgerAlias(MovementLedgerAliasEntity(userId, id, revisionId, ordinal))
            }
        }
        dao.updateTransaction(next)
        entries.map { it.accountId }.distinct().forEach {
            database.financialMovementDao().rebuildAccountProjection(userId, it, now)
        }
        dao.markReceiptsRejectedForTransaction(userId, tx.id, now)
    }
}
