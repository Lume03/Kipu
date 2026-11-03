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

    /** Caller wraps this and candidate insertion in the SAME Room transaction. */
    suspend fun attach(owner: String, source: String, event: String, root: String, reference: String?, incoming: CorrelationFacts): String? {
        val ref = CaptureCorrelation.reference(reference)
        val target = evidence.event(owner, source, event)?.root
            ?: ref?.let { key -> evidence.byReference(owner, key).firstOrNull { row ->
                facts(owner, row.root, row.source, row.reference)?.let { existing ->
                    CaptureCorrelation.sameReference(incoming, existing)
                } == true
            }?.root }
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
        val ownRows = evidence.byRoot(owner, root)
        if (ownRows.any { it.separated }) return@withTransaction null
        val own = ownRows.firstOrNull() ?: return@withTransaction null
        val incoming = facts(owner, root, own.source, ownRows.firstOrNull { it.reference != null }?.reference) ?: return@withTransaction null
        val amount = incoming.amount ?: return@withTransaction null
        val time = incoming.time ?: return@withTransaction null
        val rows = evidence.nearbyFacts(owner, amount, time - 600_000, time + 600_000)
        // Resolve confirmed receipt transactions in two batch reads, never per candidate.
        val receiptKeys = rows.filter { it.root.startsWith("receipt:") }.associate {
            receiptRegistrationKey(owner, it.root.substringAfter(':')) to it.root
        }
        val receipts = if (receiptKeys.isEmpty()) emptyList() else db.movementDao().getCorrelationReceipts(owner, receiptKeys.keys.toList())
        val transactions = if (receipts.isEmpty()) emptyMap() else db.movementDao().getCorrelationTransactions(owner, receipts.mapNotNull { it.transactionId }).associateBy { it.id }
        val receiptTransactions = receipts.mapNotNull { receipt ->
            val key = receiptKeys[receipt.idempotencyKey] ?: return@mapNotNull null
            val transaction = transactions[receipt.transactionId] ?: return@mapNotNull null
            key to transaction
        }.toMap()
        CaptureCorrelation.suggestion(incoming, rows.filter { it.root != root }.mapNotNull { row ->
            val transaction = receiptTransactions[row.root] ?: return@mapNotNull row
            if (transaction.status != "ACTIVE" || transaction.type !in listOf("INCOME", "EXPENSE") || transaction.operationKind == "REFUND") return@mapNotNull null
            row.copy(amount = transaction.amountMinor, currency = transaction.currencyCode, income = transaction.type == "INCOME", counterparty = transaction.merchantProvisionalText ?: row.counterparty, time = transaction.occurredAt)
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
        val sourceEvidence = evidence.byRoot(owner, root).first()
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
