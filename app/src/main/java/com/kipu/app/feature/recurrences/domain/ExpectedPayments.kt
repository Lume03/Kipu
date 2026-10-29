package com.kipu.app.feature.recurrences.domain

import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.first

/** Materializes only the current or next occurrence, never a ledger entry. */
class GenerateExpectedPayments @Inject constructor(private val store: ExpectedPaymentStore, private val clock: Clock) {
    suspend operator fun invoke(rule: RecurrenceRule) {
        val amount = (rule.templateTransaction["amount_minor"] as? JsonPrimitive)?.content?.toBigDecimalOrNull()?.let { runCatching { it.longValueExact() }.getOrNull() }
        val currency = (rule.templateTransaction["currency_code"] as? JsonPrimitive)?.content ?: return
        val now = clock.millis()
        store.syncExpectedTemplate(rule.userId, rule.id, amount, currency, now)
        if (rule.status != RecurrenceStatus.ACTIVE) return
        val today = LocalDate.now(clock.withZone(ZoneId.of("America/Lima")))
        val due = if (rule.nextDueDate >= today) rule.nextDueDate else
            RecurrenceScheduleCalculator.firstOccurrenceAfter(rule.startDate, rule.frequency, rule.intervalCount, rule.customIntervalUnit, today.minusDays(1))
        if (rule.endDate != null && due > rule.endDate) return
        store.insertIfAbsent(ExpectedPayment(UUID.randomUUID().toString(), rule.userId, rule.id, due, amount, currency, ExpectedPaymentStatus.EXPECTED, null, null, now, now))
    }
}

/** Linking changes only the expected payment, never registers another movement. */
class MatchCapturedMovementToExpected @Inject constructor(
    private val store: ExpectedPaymentStore,
    private val gate: CaptureAccessGate,
    private val clock: Clock,
    private val candidates: CaptureCandidateStore,
) : CaptureCandidateResolvedListener {
    override val followupFlag = CaptureFollowup.EXPECTED_PAYMENT
    suspend fun options(candidate: CaptureCandidateRecord): List<ExpectedPayment> {
        val current = candidates.get(candidate.ownerId, candidate.id) ?: return emptyList()
        if (current.expectedMatchState in setOf(ExpectedMatchState.LINKED, ExpectedMatchState.NONE)) return emptyList()
        if (gate.evaluate(candidate.ownerId) !is CaptureAccessDecision.Allowed || current.direction != CaptureDirection.EXPENSE) return emptyList()
        if (store.observeByOwner(candidate.ownerId).first().any { it.matchedCandidateId == candidate.id }) return emptyList()
        val amount = current.amountMinor ?: return emptyList()
        val currency = current.currency ?: return emptyList()
        val date = Instant.ofEpochMilli(current.occurredAt ?: return emptyList()).atZone(ZoneId.of("America/Lima")).toLocalDate()
        return store.findExpectedByAmount(candidate.ownerId, amount, currency, date.minusDays(3), date.plusDays(3))
    }
    override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) {
        if (candidate.status !in setOf(CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED)) return
        val current = candidates.get(candidate.ownerId, candidate.id) ?: error("Capture unavailable")
        if (current.expectedMatchState in setOf(ExpectedMatchState.LINKED, ExpectedMatchState.NONE) || current.direction != CaptureDirection.EXPENSE) return
        check(gate.evaluate(candidate.ownerId) is CaptureAccessDecision.Allowed) { "Expected association awaits capture access" }
        val matches = options(candidate)
        check(gate.evaluate(candidate.ownerId) is CaptureAccessDecision.Allowed) { "Capture access changed during association" }
        if (matches.size == 1) check(link(candidate, transactionId, matches.single().id)) { "Expected association must be retried" }
        else if (matches.size > 1) candidates.setExpectedMatchState(candidate.ownerId, candidate.id, ExpectedMatchState.PENDING_CHOICE, clock.millis())
    }
    suspend fun chooseNone(candidate: CaptureCandidateRecord): Boolean {
        if (gate.currentOwnerId() != candidate.ownerId) return false
        val current = candidates.get(candidate.ownerId, candidate.id) ?: return false
        if (current.expectedMatchState == ExpectedMatchState.NONE) return true
        return candidates.setExpectedMatchState(candidate.ownerId, candidate.id, ExpectedMatchState.NONE, clock.millis())
    }
    suspend fun link(candidate: CaptureCandidateRecord, transactionId: String, expectedId: String): Boolean {
        if (candidate.status !in setOf(CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED) || candidate.resolvedTransactionId != transactionId) return false
        val previous = store.get(candidate.ownerId, expectedId) ?: return false
        if (previous.status == ExpectedPaymentStatus.ATTENDED) return previous.matchedCandidateId == candidate.id && previous.matchedTransactionId == transactionId
        if (options(candidate).none { it.id == expectedId }) return false
        return store.markAttended(candidate.ownerId, expectedId, transactionId, candidate.id, clock.millis())
    }
}
