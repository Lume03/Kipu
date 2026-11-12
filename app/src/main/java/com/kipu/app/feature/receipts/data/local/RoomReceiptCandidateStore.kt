package com.kipu.app.feature.receipts.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit
import com.kipu.app.feature.receipts.domain.ReceiptCandidateSaveResult
import com.kipu.app.feature.receipts.domain.ReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.newReceiptCandidate
import com.kipu.app.feature.receipts.domain.YapeReceiptFields
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.kipu.app.feature.receipts.domain.RegisteredYapeReceipt
import com.kipu.app.feature.receipts.domain.receiptRegistrationKey

@Singleton
class RoomReceiptCandidateStore @Inject constructor(
    private val database: KipuDatabase,
    private val dao: ReceiptCandidateDao,
) : ReceiptCandidateStore {
    override suspend fun import(ownerId: String, imageFingerprint: String, fields: YapeReceiptFields, isIncome: Boolean?): ReceiptCandidateSaveResult {
        val owner = UUID.fromString(ownerId).toString()
        return database.withTransaction {
            val candidate = newReceiptCandidate(owner, imageFingerprint, fields, isIncome).copy(referenceFingerprint = null)
            val target = com.kipu.app.feature.capture.data.local.RoomCaptureCorrelation(database).attach(
                owner, "OCR:YAPE", imageFingerprint, "receipt:${candidate.id}", fields.reference,
                com.kipu.app.feature.capture.domain.CorrelationFacts("receipt:${candidate.id}", "OCR:YAPE", fields.reference,
                    fields.amountMinor, "PEN", isIncome, fields.recipient, fields.occurredAt))
            if (target != null) {
                com.kipu.app.feature.capture.data.local.RoomCaptureCorrelation(database).completeEmpty(owner, target,
                    com.kipu.app.feature.capture.domain.CorrelationFacts("receipt:${candidate.id}", "OCR:YAPE", fields.reference,
                        fields.amountMinor, "PEN", isIncome, fields.recipient, fields.occurredAt))
                return@withTransaction ReceiptCandidateSaveResult.Duplicate(target.substringAfter(':'))
            }
            // OCR does not identify an account, so a reference alone cannot prove identity.
            val duplicate = dao.findImage(owner, imageFingerprint)
            if (duplicate != null) return@withTransaction ReceiptCandidateSaveResult.Duplicate(duplicate.candidateId)

            val row = candidate.toEntity().copy(referenceFingerprint = null)
            if (dao.insert(row) != -1L) ReceiptCandidateSaveResult.Saved(candidate)
            else {
                val conflict = dao.findImage(owner, imageFingerprint)
                conflict?.let { ReceiptCandidateSaveResult.Duplicate(it.candidateId) }
                    ?: throw IllegalStateException("Candidate insert rejected without a matching duplicate")
            }
        }
    }

    override fun observe(ownerId: String): Flow<List<ReceiptCandidate>> =
        dao.observePending(UUID.fromString(ownerId).toString()).map { rows -> rows.map(ReceiptCandidateEntity::toDomain) }

    override suspend fun get(ownerId: String, candidateId: String): ReceiptCandidate? =
        dao.get(UUID.fromString(ownerId).toString(), candidateId)?.toDomain()

    override suspend fun edit(ownerId: String, candidateId: String, edit: ReceiptCandidateEdit): ReceiptCandidate? {
        val owner = UUID.fromString(ownerId).toString()
        return database.withTransaction {
            val fingerprint: String? = null
            val changed = dao.edit(owner, candidateId, edit.amountMinor, edit.occurredAt, edit.recipient, edit.reference,
                fingerprint, edit.isIncome, System.currentTimeMillis())
            if (changed == 1) get(owner, candidateId) else null
        }
    }

    override suspend fun markConfirmed(ownerId: String, candidateId: String): Boolean = database.withTransaction {
        val owner = UUID.fromString(ownerId).toString()
        val changed = dao.confirm(owner, candidateId, System.currentTimeMillis()) == 1
        if (changed) registeredMovementId(owner, candidateId)?.let {
            database.movementDao().setCaptureSource(owner, it, "OCR")
        }
        changed
    }

    override suspend fun discard(ownerId: String, candidateId: String): Boolean =
        dao.discard(UUID.fromString(ownerId).toString(), candidateId, System.currentTimeMillis()) == 1

    override suspend fun restore(ownerId: String, candidateId: String): Boolean =
        dao.restore(UUID.fromString(ownerId).toString(), candidateId, System.currentTimeMillis()) == 1

    override suspend fun registeredMovementId(ownerId: String, candidateId: String): String? {
        val owner = UUID.fromString(ownerId).toString()
        return database.movementDao().getReceipt(owner, receiptRegistrationKey(owner, candidateId))?.transactionId
    }

    override fun observeRegistered(ownerId: String): Flow<List<RegisteredYapeReceipt>> {
        val owner = UUID.fromString(ownerId).toString()
        val movements = database.movementDao()
        return combine(dao.observeConfirmed(owner), movements.observeTransactions(owner)) { rows, transactions ->
            val active = transactions.filter { it.status == "ACTIVE" }.associateBy { it.id }
            buildList {
                for (row in rows) {
                    val key = receiptRegistrationKey(owner, row.candidateId)
                    val transactionId = movements.getReceipt(owner, key)?.transactionId ?: continue
                    val movement = active[transactionId] ?: continue
                    add(RegisteredYapeReceipt(row.toDomain().copy(amountMinor = movement.amountMinor,
                        occurredAt = movement.occurredAt, recipient = movement.merchantProvisionalText ?: row.recipient,
                        isIncome = movement.type == "INCOME"), transactionId))
                    if (size == 3) break
                }
            }
        }
    }

    private fun ReceiptCandidate.toEntity() = ReceiptCandidateEntity(
        id, ownerId, provider, amountMinor, occurredAt, recipient, reference, referenceFingerprint,
        imageFingerprint, status.name, createdAt, updatedAt, isIncome,
    )
}
