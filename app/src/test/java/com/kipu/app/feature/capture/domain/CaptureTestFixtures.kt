package com.kipu.app.feature.capture.domain

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal val CAPTURE_NOW: Long = Instant.parse("2026-10-28T17:00:00Z").toEpochMilli()

/** A HIGH-ready debit expense: every field resolved, explicit date, unique instrument. */
internal fun captureCandidate(
    ownerId: String = "owner-1",
    id: String = UUID.randomUUID().toString(),
    provider: CaptureProvider = CaptureProvider.BCP,
) = CaptureCandidateRecord(
    id = id, ownerId = ownerId, sourcePackage = "com.bcp.bank.bcp", provider = provider,
    sourceEventFingerprint = "fp-$id", status = CaptureCandidateStatus.PENDING, direction = CaptureDirection.EXPENSE,
    amountMinor = 12_000L, currency = "PEN", occurredAt = CAPTURE_NOW - 60_000L, occurredAtInferred = false,
    merchantSourceText = "PLAZA VEA", merchantId = "merchant-1", categoryId = "category-1", cardLast4 = "1234",
    instrumentKind = CaptureInstrumentKind.DEBIT_ACCOUNT, accountId = "account-1", cardId = "card-1",
    instrumentAmbiguous = false, confidence = CaptureConfidenceLevel.HIGH, confidenceReasons = emptyList(),
    creditMode = null, installmentCount = null, resolvedTransactionId = null, resolutionKey = null,
    evidenceSnippet = "Consumo de S/ 120.00 en PLAZA VEA", evidenceExpiresAt = CAPTURE_NOW + 1_000L,
    userCorrectedFields = emptyList(), createdAt = CAPTURE_NOW - 60_000L, updatedAt = CAPTURE_NOW - 60_000L, resolvedAt = null,
)

internal fun CaptureCandidateRecord.asCredit() = copy(
    instrumentKind = CaptureInstrumentKind.CREDIT_CARD, accountId = "liability-1", cardId = "61000000-0000-4000-8000-000000000010",
    creditMode = CreditMode.DIRECT,
)

/** In-memory store with the same PENDING guards as the Room DAO. */
internal class FakeCaptureCandidateStore(vararg initial: CaptureCandidateRecord) : CaptureCandidateStore {
    val rows = MutableStateFlow(initial.associateBy { it.ownerId to it.id })
    var resolveCalls = 0

    fun row(ownerId: String, id: String) = rows.value[ownerId to id]
    private fun put(record: CaptureCandidateRecord) { rows.value = rows.value + ((record.ownerId to record.id) to record) }
    override suspend fun pendingFollowups(ownerId: String) = rows.value.values.filter { it.ownerId == ownerId && it.followupsPending != 0 }
    override suspend fun completeFollowup(ownerId: String, id: String, flag: Int) {
        row(ownerId, id)?.let { put(it.copy(followupsPending = it.followupsPending and flag.inv())) }
    }

    override fun observePending(ownerId: String): Flow<List<CaptureCandidateRecord>> =
        rows.map { all -> all.values.filter { it.ownerId == ownerId && it.status == CaptureCandidateStatus.PENDING } }

    override fun observePendingExpectedMatches(ownerId: String): Flow<List<CaptureCandidateRecord>> =
        rows.map { all -> all.values.filter { it.ownerId == ownerId && it.expectedMatchState == ExpectedMatchState.PENDING_CHOICE } }

    override suspend fun setExpectedMatchState(ownerId: String, id: String, state: ExpectedMatchState, now: Long): Boolean {
        val current = row(ownerId, id) ?: return false
        if (current.status !in setOf(CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED) ||
            current.expectedMatchState in setOf(ExpectedMatchState.LINKED, ExpectedMatchState.NONE)) return false
        put(current.copy(expectedMatchState = state, updatedAt = now))
        return true
    }

    override suspend fun get(ownerId: String, id: String) = row(ownerId, id)

    override suspend fun findBySourceFingerprint(ownerId: String, fingerprint: String) =
        rows.value.values.firstOrNull { it.ownerId == ownerId && it.sourceEventFingerprint == fingerprint }

    override suspend fun insertIfAbsent(record: CaptureCandidateRecord): Boolean {
        if (findBySourceFingerprint(record.ownerId, record.sourceEventFingerprint) != null) return false
        put(record)
        return true
    }

    override suspend fun updatePending(record: CaptureCandidateRecord): Boolean {
        val current = row(record.ownerId, record.id)?.takeIf { it.status == CaptureCandidateStatus.PENDING } ?: return false
        put(record.copy(status = current.status, evidenceSnippet = current.evidenceSnippet))
        return true
    }

    override suspend fun updateConfidence(
        ownerId: String, id: String, confidence: CaptureConfidenceLevel, reasons: List<CaptureReason>, now: Long,
    ): Boolean {
        val current = row(ownerId, id)?.takeIf { it.status == CaptureCandidateStatus.PENDING } ?: return false
        put(current.copy(confidence = confidence, confidenceReasons = reasons, updatedAt = now))
        return true
    }

    override suspend fun markResolved(
        ownerId: String, id: String, status: CaptureCandidateStatus, transactionId: String,
        resolutionKey: String, userCorrectedFields: List<String>, now: Long,
    ): Boolean {
        val current = row(ownerId, id)?.takeIf { it.status == CaptureCandidateStatus.PENDING } ?: return false
        put(current.copy(status = status, resolvedTransactionId = transactionId, resolutionKey = resolutionKey,
            userCorrectedFields = userCorrectedFields, evidenceSnippet = null, evidenceExpiresAt = null, resolvedAt = now, updatedAt = now,
            followupsPending = CaptureFollowup.RESOLVED))
        return true
    }

    override suspend fun resolve(
        record: CaptureCandidateRecord, status: CaptureCandidateStatus, transactionId: String, resolutionKey: String, now: Long,
    ): Boolean {
        resolveCalls++
        return updatePending(record) &&
            markResolved(record.ownerId, record.id, status, transactionId, resolutionKey, record.userCorrectedFields, now)
    }

    override suspend fun discard(ownerId: String, id: String, now: Long): Boolean {
        val current = row(ownerId, id)?.takeIf { it.status == CaptureCandidateStatus.PENDING } ?: return false
        put(current.copy(status = CaptureCandidateStatus.DISCARDED, evidenceSnippet = null, evidenceExpiresAt = null, resolvedAt = now,
            followupsPending = CaptureFollowup.DISCARD_NOTICE))
        return true
    }

    override suspend fun purgeExpiredEvidence(now: Long): Int {
        val expired = rows.value.values.filter { it.evidenceExpiresAt != null && it.evidenceExpiresAt <= now }
        expired.forEach { put(it.copy(evidenceSnippet = null, evidenceExpiresAt = null)) }
        return expired.size
    }
}

internal object TestCaptureTransactions : com.kipu.app.core.database.DatabaseTransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = block()
}

internal val noCaptureReceipts = CaptureRegistrationReceipts { _, _ -> null }

internal fun testConfirmCaptureCandidate(
    store: CaptureCandidateStore,
    gate: CaptureAccessGate,
    register: com.kipu.app.feature.movements.domain.RegisterTransaction,
    credit: com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase,
    channels: CaptureWalletChannelResolver,
    clock: java.time.Clock,
    listeners: Set<CaptureCandidateResolvedListener>,
    transactions: com.kipu.app.core.database.DatabaseTransactionRunner = TestCaptureTransactions,
    receipts: CaptureRegistrationReceipts = noCaptureReceipts,
) = ConfirmCaptureCandidate(store, gate, register, credit, channels, clock, listeners, transactions, receipts,
    CaptureResolutionFollowups(store, listeners, emptySet()))

internal fun testDiscardCaptureCandidate(store: CaptureCandidateStore, clock: java.time.Clock) =
    DiscardCaptureCandidate(store, clock, emptySet(), TestCaptureTransactions, noCaptureReceipts,
        CaptureResolutionFollowups(store, emptySet(), emptySet()))

internal class FakeCaptureAccessGate(
    private val ownerId: String = "owner-1",
    var decision: CaptureAccessDecision = CaptureAccessDecision.Allowed(
        com.kipu.app.feature.plans.domain.model.EffectiveEntitlement(true, null),
    ),
) : CaptureAccessGate {
    var evaluations = 0
    override fun currentOwnerId() = ownerId
    override suspend fun evaluateCurrent() = decision
    override suspend fun evaluate(ownerId: String): CaptureAccessDecision {
        evaluations++
        return if (ownerId == this.ownerId) decision else CaptureAccessDecision.NoSession
    }
}
