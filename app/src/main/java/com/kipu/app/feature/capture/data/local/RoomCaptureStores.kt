package com.kipu.app.feature.capture.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.feature.capture.domain.CaptureSourceEvent
import com.kipu.app.feature.capture.domain.CaptureSourceEventStore
import com.kipu.app.feature.capture.domain.CaptureSourceOutcome
import com.kipu.app.feature.capture.domain.ExpectedPayment
import com.kipu.app.feature.capture.domain.ExpectedPaymentStore
import com.kipu.app.feature.recurrences.data.local.ExpectedPaymentDao
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomCaptureCandidateStore @Inject constructor(
    private val database: KipuDatabase,
    private val dao: CaptureCandidateDao,
) : CaptureCandidateStore {
    override suspend fun pendingFollowups(ownerId: String) = dao.pendingFollowups(ownerId).map { it.toDomain() }
    override suspend fun completeFollowup(ownerId: String, id: String, flag: Int) = dao.completeFollowup(ownerId, id, flag)
    override fun observePending(ownerId: String): Flow<List<CaptureCandidateRecord>> =
        dao.observePending(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observePendingExpectedMatches(ownerId: String): Flow<List<CaptureCandidateRecord>> =
        dao.observePendingExpectedMatches(ownerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun setExpectedMatchState(ownerId: String, id: String, state: com.kipu.app.feature.capture.domain.ExpectedMatchState, now: Long): Boolean {
        require(state != com.kipu.app.feature.capture.domain.ExpectedMatchState.LINKED)
        return dao.setExpectedMatchState(ownerId, id, state.name, now) > 0
    }

    override suspend fun get(ownerId: String, id: String) = dao.get(ownerId, id)?.toDomain()

    override suspend fun findBySourceFingerprint(ownerId: String, fingerprint: String) =
        dao.findBySourceFingerprint(ownerId, fingerprint)?.toDomain()

    override suspend fun insertIfAbsent(record: CaptureCandidateRecord): Boolean =
        dao.insertIfAbsent(record.toEntity()) != -1L

    override suspend fun updatePending(record: CaptureCandidateRecord): Boolean {
        val e = record.toEntity()
        return dao.updatePending(
            ownerId = e.ownerId,
            id = e.id,
            direction = e.direction,
            amountMinor = e.amountMinor,
            currency = e.currency,
            occurredAt = e.occurredAt,
            occurredAtInferred = e.occurredAtInferred,
            merchantSourceText = e.merchantSourceText,
            merchantId = e.merchantId,
            categoryId = e.categoryId,
            cardLast4 = e.cardLast4,
            instrumentKind = e.instrumentKind,
            accountId = e.accountId,
            cardId = e.cardId,
            instrumentAmbiguous = e.instrumentAmbiguous,
            confidence = e.confidence,
            confidenceReasons = e.confidenceReasons,
            creditMode = e.creditMode,
            installmentCount = e.installmentCount,
            userCorrectedFields = e.userCorrectedFields,
            now = e.updatedAt,
        ) > 0
    }

    override suspend fun updateConfidence(
        ownerId: String,
        id: String,
        confidence: CaptureConfidenceLevel,
        reasons: List<CaptureReason>,
        now: Long,
    ): Boolean = dao.updateConfidence(ownerId, id, confidence.name, reasons.map { it.name }.toJsonCodes(), now) > 0

    override suspend fun markResolved(
        ownerId: String,
        id: String,
        status: CaptureCandidateStatus,
        transactionId: String,
        resolutionKey: String,
        userCorrectedFields: List<String>,
        now: Long,
    ): Boolean {
        require(status == CaptureCandidateStatus.CONFIRMED || status == CaptureCandidateStatus.AUTO_REGISTERED) {
            "A candidate is resolved only as CONFIRMED or AUTO_REGISTERED"
        }
        return dao.markResolved(ownerId, id, status.name, transactionId, resolutionKey, userCorrectedFields.toJsonCodes(), now) > 0
    }

    override suspend fun resolve(
        record: CaptureCandidateRecord,
        status: CaptureCandidateStatus,
        transactionId: String,
        resolutionKey: String,
        now: Long,
    ): Boolean = database.withTransaction {
        // Both statements are guarded by status='PENDING'; a concurrent resolution makes this a no-op.
        updatePending(record.copy(updatedAt = now)) &&
            markResolved(record.ownerId, record.id, status, transactionId, resolutionKey, record.userCorrectedFields, now)
    }

    override suspend fun discard(ownerId: String, id: String, now: Long): Boolean = dao.discard(ownerId, id, now) > 0

    override suspend fun purgeExpiredEvidence(now: Long): Int = dao.purgeExpiredEvidence(now)
}

@Singleton
class RoomCaptureSourceEventStore @Inject constructor(
    private val dao: CaptureSourceEventDao,
) : CaptureSourceEventStore {
    override suspend fun recordIfAbsent(event: CaptureSourceEvent): Boolean = dao.insertIfAbsent(event.toEntity()) != -1L

    override suspend fun find(ownerId: String, fingerprint: String) = dao.find(ownerId, fingerprint)?.toDomain()

    override suspend fun updateOutcome(ownerId: String, fingerprint: String, outcome: CaptureSourceOutcome) {
        dao.updateOutcome(ownerId, fingerprint, outcome.name)
    }
}

@Singleton
class RoomExpectedPaymentStore @Inject constructor(
    private val dao: ExpectedPaymentDao,
) : ExpectedPaymentStore {
    override suspend fun syncExpectedTemplate(ownerId: String, recurrenceId: String, amountMinor: Long?, currency: String, now: Long) {
        dao.syncExpectedTemplate(ownerId, recurrenceId, amountMinor, currency, now)
    }

    override suspend fun insertIfAbsent(payment: ExpectedPayment): Boolean = dao.insertIfAbsent(payment.toEntity()) != -1L

    override suspend fun get(ownerId: String, id: String) = dao.get(ownerId, id)?.toDomain()

    override fun observeByOwner(ownerId: String): Flow<List<ExpectedPayment>> =
        dao.observeByOwner(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeByRecurrence(ownerId: String, recurrenceId: String): Flow<List<ExpectedPayment>> =
        dao.observeByRecurrence(ownerId, recurrenceId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findExpectedByAmount(
        ownerId: String,
        amountMinor: Long,
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ExpectedPayment> =
        dao.findExpectedByAmount(ownerId, amountMinor, currency, from.toString(), to.toString()).map { it.toDomain() }

    override suspend fun markAttended(
        ownerId: String,
        id: String,
        transactionId: String,
        candidateId: String?,
        now: Long,
    ): Boolean = dao.markAttended(ownerId, id, transactionId, candidateId, now) > 0
}
