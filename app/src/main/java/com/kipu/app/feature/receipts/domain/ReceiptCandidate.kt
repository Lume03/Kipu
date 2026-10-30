package com.kipu.app.feature.receipts.domain

import java.time.Instant

enum class ReceiptCandidateStatus { PENDING, CONFIRMED, DISCARDED }

/** Persisted review data only; OCR text and source image bytes are deliberately never retained. */
data class ReceiptCandidate(
    val id: String,
    val ownerId: String,
    val provider: String = "YAPE",
    val amountMinor: Long?,
    val occurredAt: Long?,
    val recipient: String?,
    val reference: String?,
    /** Immutable fingerprint of the imported operation reference; edits cannot rotate dedup/idempotency identity. */
    val referenceFingerprint: String?,
    val imageFingerprint: String,
    val status: ReceiptCandidateStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val isIncome: Boolean? = null,
)

data class ReceiptCandidateEdit(
    val amountMinor: Long?,
    val occurredAt: Long?,
    val recipient: String?,
    val reference: String?,
    val isIncome: Boolean? = null,
)

sealed interface ReceiptCandidateSaveResult {
    data class Saved(val candidate: ReceiptCandidate) : ReceiptCandidateSaveResult
    data class Duplicate(val existingCandidateId: String) : ReceiptCandidateSaveResult
}

interface ReceiptCandidateStore {
    suspend fun import(ownerId: String, imageFingerprint: String, fields: YapeReceiptFields, isIncome: Boolean? = fields.isIncome): ReceiptCandidateSaveResult
    fun observe(ownerId: String): kotlinx.coroutines.flow.Flow<List<ReceiptCandidate>>
    suspend fun get(ownerId: String, candidateId: String): ReceiptCandidate?
    suspend fun edit(ownerId: String, candidateId: String, edit: ReceiptCandidateEdit): ReceiptCandidate?
    suspend fun markConfirmed(ownerId: String, candidateId: String): Boolean
    suspend fun discard(ownerId: String, candidateId: String): Boolean
    suspend fun restore(ownerId: String, candidateId: String): Boolean = false
    suspend fun registeredMovementId(ownerId: String, candidateId: String): String? = null
    fun observeRegistered(ownerId: String): kotlinx.coroutines.flow.Flow<List<RegisteredYapeReceipt>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
}

internal fun String?.normalizedReference(): String? = this?.trim()?.replace(Regex("\\s+"), " ")?.uppercase()
    ?.takeIf(String::isNotBlank)

internal fun newReceiptCandidate(
    ownerId: String,
    imageFingerprint: String,
    fields: YapeReceiptFields,
    isIncome: Boolean?,
    now: Long = Instant.now().toEpochMilli(),
) = ReceiptCandidate(
    id = java.util.UUID.randomUUID().toString(), ownerId = ownerId,
    amountMinor = fields.amountMinor, occurredAt = fields.occurredAt,
    recipient = fields.recipient, reference = fields.reference,
    referenceFingerprint = fields.reference.normalizedReference()?.sha256(),
    imageFingerprint = imageFingerprint, status = ReceiptCandidateStatus.PENDING,
    createdAt = now, updatedAt = now, isIncome = isIncome,
)

internal fun String.sha256(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
