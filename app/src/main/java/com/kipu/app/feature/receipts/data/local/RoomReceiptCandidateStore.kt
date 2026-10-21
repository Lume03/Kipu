package com.kipu.app.feature.receipts.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit
import com.kipu.app.feature.receipts.domain.ReceiptCandidateSaveResult
import com.kipu.app.feature.receipts.domain.ReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.newReceiptCandidate
import com.kipu.app.feature.receipts.domain.YapeReceiptFields
import com.kipu.app.feature.receipts.domain.normalizedReference
import com.kipu.app.feature.receipts.domain.sha256
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomReceiptCandidateStore @Inject constructor(
    private val database: KipuDatabase,
    private val dao: ReceiptCandidateDao,
) : ReceiptCandidateStore {
    override suspend fun import(ownerId: String, imageFingerprint: String, fields: YapeReceiptFields, isIncome: Boolean?): ReceiptCandidateSaveResult {
        val owner = UUID.fromString(ownerId).toString()
        return database.withTransaction {
            val refFingerprint = fields.reference.normalizedReference()?.sha256()
            val duplicate = dao.findImage(owner, imageFingerprint) ?: refFingerprint?.let { dao.findReference(owner, it) }
            if (duplicate != null) return@withTransaction ReceiptCandidateSaveResult.Duplicate(duplicate.candidateId)
            val candidate = newReceiptCandidate(owner, imageFingerprint, fields, isIncome)
            val row = candidate.toEntity()
            if (dao.insert(row) != -1L) ReceiptCandidateSaveResult.Saved(candidate)
            else {
                val conflict = dao.findImage(owner, imageFingerprint)
                    ?: candidate.referenceFingerprint?.let { dao.findReference(owner, it) }
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
            val fingerprint = edit.reference.normalizedReference()?.sha256()
            val existing = fingerprint?.let { dao.findReference(owner, it) }
            if (existing != null && existing.candidateId != candidateId) return@withTransaction null
            val changed = dao.edit(owner, candidateId, edit.amountMinor, edit.occurredAt, edit.recipient, edit.reference,
                fingerprint, edit.isIncome, System.currentTimeMillis())
            if (changed == 1) get(owner, candidateId) else null
        }
    }

    override suspend fun markConfirmed(ownerId: String, candidateId: String): Boolean =
        dao.confirm(UUID.fromString(ownerId).toString(), candidateId, System.currentTimeMillis()) == 1

    override suspend fun discard(ownerId: String, candidateId: String): Boolean =
        dao.discard(UUID.fromString(ownerId).toString(), candidateId, System.currentTimeMillis()) == 1

    private fun ReceiptCandidate.toEntity() = ReceiptCandidateEntity(
        id, ownerId, provider, amountMinor, occurredAt, recipient, reference, referenceFingerprint,
        imageFingerprint, status.name, createdAt, updatedAt, isIncome,
    )
}
