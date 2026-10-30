package com.kipu.app.feature.receipts.domain

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Missing receipt fields, not financial account/category fields chosen at confirmation. */
val ReceiptCandidate.missingDataCount: Int get() = listOf(
    amountMinor?.takeIf { it > 0 }, occurredAt, recipient?.takeIf(String::isNotBlank),
    reference?.takeIf(String::isNotBlank), isIncome,
).count { it == null }

fun pendingReceipts(rows: List<ReceiptCandidate>): List<ReceiptCandidate> = rows
    .filter { it.status == ReceiptCandidateStatus.PENDING }
    .sortedWith(compareByDescending<ReceiptCandidate> { it.createdAt }.thenBy { it.id })

/** BigInteger keeps the preview exact even when many valid amounts are added. */
fun receiptBalanceEffect(rows: List<ReceiptCandidate>): BigInteger = pendingReceipts(rows)
    .filter { it.missingDataCount == 0 }
    .fold(BigInteger.ZERO) { sum, row ->
        val amount = BigInteger.valueOf(requireNotNull(row.amountMinor))
        sum + if (row.isIncome == true) amount else -amount
    }

data class ReceiptReviewQueue(val ids: List<String>, val index: Int = 0) {
    val currentId: String? get() = ids.getOrNull(index)
    val position: String get() = "${index + 1} de ${ids.size}"
    fun next(pendingIds: Set<String>): ReceiptReviewQueue? =
        ((index + 1)..ids.lastIndex).firstOrNull { ids[it] in pendingIds }?.let { copy(index = it) }

    companion object {
        fun start(rows: List<ReceiptCandidate>, firstId: String? = null): ReceiptReviewQueue {
            val ids = pendingReceipts(rows).map { it.id }
            return ReceiptReviewQueue(ids, ids.indexOf(firstId).coerceAtLeast(0))
        }
    }
}

data class RegisteredYapeReceipt(val candidate: ReceiptCandidate, val transactionId: String)

internal fun receiptRegistrationKey(ownerId: String, candidateId: String): String =
    UUID.nameUUIDFromBytes("$ownerId|YAPE|candidate|$candidateId".toByteArray(StandardCharsets.UTF_8)).toString()

enum class ReceiptImportNotice {
    NOT_YAPE, UNREADABLE, UNSUPPORTED_FORMAT, ALREADY_PENDING, ALREADY_REGISTERED, ALREADY_DISCARDED,
}

fun receiptImportNotice(result: ReceiptImportResult, duplicateStatus: ReceiptCandidateStatus? = null): ReceiptImportNotice? =
    when (result) {
        ReceiptImportResult.NotYape -> ReceiptImportNotice.NOT_YAPE
        ReceiptImportResult.Unreadable -> ReceiptImportNotice.UNREADABLE
        ReceiptImportResult.UnsupportedFormat -> ReceiptImportNotice.UNSUPPORTED_FORMAT
        is ReceiptImportResult.Duplicate -> when (duplicateStatus) {
            ReceiptCandidateStatus.PENDING -> ReceiptImportNotice.ALREADY_PENDING
            ReceiptCandidateStatus.CONFIRMED -> ReceiptImportNotice.ALREADY_REGISTERED
            ReceiptCandidateStatus.DISCARDED -> ReceiptImportNotice.ALREADY_DISCARDED
            null -> null
        }
        else -> null
    }

/** Finite scene timings shared by the guide and its tests. */
object ReceiptGuideTimeline {
    val durations = listOf(4.2f, 4.4f, 5.6f)
    fun footerVisible(step: Int, seconds: Float, manual: Boolean, reduced: Boolean): Boolean =
        reduced || (step == 2 && (manual || seconds >= 4.9f))
}
