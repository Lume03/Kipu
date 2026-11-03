package com.kipu.app.feature.capture.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.receipts.domain.receiptRegistrationKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCaptureCorrelation @Inject constructor(private val db: KipuDatabase) {
    private val evidence get() = db.captureEvidenceDao()

    private suspend fun seed(owner: String) {
        for (c in db.captureCandidateDao().all(owner)) evidence.insert(CaptureEvidenceEntity(
            owner, "NOTIFICATION:${c.sourcePackage}", c.sourceEventFingerprint, "capture:${c.id}", null))
        // Existing receipts from v35 participate without changing their historical identity.
        for (r in db.receiptCandidateDao().all(owner)) {
            val ref = CaptureCorrelation.reference(r.reference)
            evidence.insert(CaptureEvidenceEntity(owner, "OCR:YAPE", r.imageFingerprint,
                "receipt:${r.candidateId}", ref))
            if (ref != null) evidence.insert(CaptureEvidenceEntity(owner, "OCR:YAPE", "${r.imageFingerprint}:ref:$ref",
                "receipt:${r.candidateId}", ref))
        }
    }

    /** Caller wraps this and candidate insertion in the SAME Room transaction. */
    suspend fun attach(owner: String, source: String, event: String, root: String, reference: String?): String? {
        seed(owner)
        val ref = CaptureCorrelation.reference(reference)
        val rows = evidence.all(owner)
        val target = rows.firstOrNull { it.source == source && it.eventId == event }?.root
            ?: ref?.let { key -> rows.firstOrNull { it.reference == key }?.root }
        evidence.insert(CaptureEvidenceEntity(owner, source, event, target ?: root, ref))
        return target
    }

    suspend fun completeEmpty(owner: String, target: String, incoming: CorrelationFacts) {
        val id = target.substringAfter(':')
        if (target.startsWith("receipt:")) db.receiptCandidateDao().completeEmpty(owner, id,
            incoming.amount, incoming.time, incoming.counterparty, incoming.income)
        else db.captureCandidateDao().completeEmpty(owner, id, incoming.amount, incoming.currency,
            incoming.income?.let { if (it) "INCOME" else "EXPENSE" }, incoming.time, incoming.counterparty)
    }

    suspend fun facts(owner: String, root: String, source: String, reference: String?): CorrelationFacts? {
        val id = root.substringAfter(':')
        val transaction = movement(owner, root)?.let { db.movementDao().getTransactionById(owner, it) }
        if (transaction != null) {
            if (transaction.status != "ACTIVE" || transaction.type !in listOf("INCOME", "EXPENSE") || transaction.operationKind == "REFUND") return null
            return CorrelationFacts(root, source, reference, transaction.amountMinor, transaction.currencyCode,
                transaction.type == "INCOME", transaction.merchantProvisionalText ?: if (root.startsWith("receipt:"))
                    db.receiptCandidateDao().get(owner, id)?.recipient else db.captureCandidateDao().get(owner, id)?.merchantSourceText,
                transaction.occurredAt)
        }
        return if (root.startsWith("receipt:")) {
            val r = db.receiptCandidateDao().get(owner, id) ?: return null
            if (r.status == "DISCARDED") return null
            CorrelationFacts(root, source, reference, r.amountMinor, "PEN", r.isIncome, r.recipient, r.occurredAt)
        } else {
            val r = db.captureCandidateDao().get(owner, id) ?: return null
            if (r.status == "DISCARDED") return null
            CorrelationFacts(root, source, reference, r.amountMinor, r.currency, r.direction?.let { it == "INCOME" }, r.merchantSourceText, r.occurredAt)
        }
    }

    suspend fun suggestion(owner: String, root: String): String? = db.withTransaction {
        seed(owner)
        val rows = evidence.all(owner)
        if (rows.any { it.root == root && it.separated }) return@withTransaction null
        val own = rows.firstOrNull { it.root == root } ?: return@withTransaction null
        val incoming = facts(owner, root, own.source, rows.firstOrNull { it.root == root && it.reference != null }?.reference) ?: return@withTransaction null
        val separatedRoots = rows.filter { it.separated }.map { it.root }.toSet()
        CaptureCorrelation.suggestion(incoming, rows.filter { it.root != root && it.root !in separatedRoots }.mapNotNull {
            facts(owner, it.root, it.source, it.reference)
        })
    }

    suspend fun separate(owner: String, root: String) = db.withTransaction { evidence.separate(owner, root) }

    /** Explicit user choice; revalidated under the write lock, no financial write. */
    suspend fun merge(owner: String, root: String, target: String): Boolean = db.withTransaction {
        if (suggestion(owner, root) != target) return@withTransaction false
        val id = root.substringAfter(':')
        val pending = if (root.startsWith("receipt:")) db.receiptCandidateDao().get(owner, id)?.status == "PENDING"
            else db.captureCandidateDao().get(owner, id)?.status == "PENDING"
        if (!pending) return@withTransaction false
        val sourceEvidence = evidence.all(owner).first { it.root == root }
        facts(owner, root, sourceEvidence.source, sourceEvidence.reference)?.let { completeEmpty(owner, target, it) }
        if (root.startsWith("receipt:")) db.receiptCandidateDao().discard(owner, id, System.currentTimeMillis())
        else db.captureCandidateDao().discard(owner, id, System.currentTimeMillis())
        evidence.move(owner, root, target)
        true
    }

    suspend fun movement(owner: String, root: String): String? = if (root.startsWith("receipt:"))
        db.movementDao().getReceipt(owner, receiptRegistrationKey(owner, root.substringAfter(':')))?.transactionId
    else db.captureCandidateDao().get(owner, root.substringAfter(':'))?.resolvedTransactionId
}
