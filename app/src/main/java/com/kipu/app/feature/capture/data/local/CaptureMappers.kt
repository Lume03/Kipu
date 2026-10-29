package com.kipu.app.feature.capture.data.local

import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.feature.capture.domain.CaptureSourceEvent
import com.kipu.app.feature.capture.domain.CaptureSourceOutcome
import com.kipu.app.feature.capture.domain.CreditMode
import com.kipu.app.feature.capture.domain.ExpectedPayment
import com.kipu.app.feature.capture.domain.ExpectedPaymentStatus
import com.kipu.app.feature.recurrences.data.local.ExpectedPaymentEntity
import java.time.LocalDate

/** Encodes stable ASCII codes (enum names, field names) as a JSON string array. */
internal fun List<String>.toJsonCodes(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }

internal fun String.fromJsonCodes(): List<String> =
    removePrefix("[").removeSuffix("]").split(',')
        .map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotEmpty() }

fun CaptureSourceEvent.toEntity() = CaptureSourceEventEntity(ownerId, eventFingerprint, sourcePackage, receivedAt, outcome.name)

fun CaptureSourceEventEntity.toDomain() = CaptureSourceEvent(
    ownerId = ownerId,
    eventFingerprint = eventFingerprint,
    sourcePackage = sourcePackage,
    receivedAt = receivedAt,
    outcome = CaptureSourceOutcome.valueOf(outcome),
)

fun CaptureCandidateRecord.toEntity() = CaptureCandidateEntity(
    id = id,
    ownerId = ownerId,
    sourcePackage = sourcePackage,
    provider = provider.name,
    sourceEventFingerprint = sourceEventFingerprint,
    status = status.name,
    direction = direction?.name,
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = occurredAt,
    occurredAtInferred = occurredAtInferred,
    merchantSourceText = merchantSourceText,
    merchantId = merchantId,
    categoryId = categoryId,
    cardLast4 = cardLast4,
    instrumentKind = instrumentKind?.name,
    accountId = accountId,
    cardId = cardId,
    instrumentAmbiguous = instrumentAmbiguous,
    confidence = confidence.name,
    confidenceReasons = confidenceReasons.map { it.name }.toJsonCodes(),
    creditMode = creditMode?.name,
    installmentCount = installmentCount,
    resolvedTransactionId = resolvedTransactionId,
    resolutionKey = resolutionKey,
    evidenceSnippet = evidenceSnippet,
    evidenceExpiresAt = evidenceExpiresAt,
    userCorrectedFields = userCorrectedFields.toJsonCodes(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolvedAt = resolvedAt,
    expectedMatchState = expectedMatchState?.name,
    followupsPending = followupsPending,
)

fun CaptureCandidateEntity.toDomain() = CaptureCandidateRecord(
    id = id,
    ownerId = ownerId,
    sourcePackage = sourcePackage,
    provider = CaptureProvider.valueOf(provider),
    sourceEventFingerprint = sourceEventFingerprint,
    status = CaptureCandidateStatus.valueOf(status),
    direction = direction?.let(CaptureDirection::valueOf),
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = occurredAt,
    occurredAtInferred = occurredAtInferred,
    merchantSourceText = merchantSourceText,
    merchantId = merchantId,
    categoryId = categoryId,
    cardLast4 = cardLast4,
    instrumentKind = instrumentKind?.let(CaptureInstrumentKind::valueOf),
    accountId = accountId,
    cardId = cardId,
    instrumentAmbiguous = instrumentAmbiguous,
    confidence = CaptureConfidenceLevel.valueOf(confidence),
    confidenceReasons = CaptureReason.fromCodes(confidenceReasons.fromJsonCodes()),
    creditMode = creditMode?.let(CreditMode::valueOf),
    installmentCount = installmentCount,
    resolvedTransactionId = resolvedTransactionId,
    resolutionKey = resolutionKey,
    evidenceSnippet = evidenceSnippet,
    evidenceExpiresAt = evidenceExpiresAt,
    userCorrectedFields = userCorrectedFields.fromJsonCodes(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolvedAt = resolvedAt,
    expectedMatchState = expectedMatchState?.let(com.kipu.app.feature.capture.domain.ExpectedMatchState::valueOf),
    followupsPending = followupsPending,
)

fun ExpectedPayment.toEntity() = ExpectedPaymentEntity(
    id = id,
    ownerId = ownerId,
    recurrenceId = recurrenceId,
    dueDate = dueDate.toString(),
    expectedAmountMinor = expectedAmountMinor,
    currency = currency,
    status = status.name,
    matchedTransactionId = matchedTransactionId,
    matchedCandidateId = matchedCandidateId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ExpectedPaymentEntity.toDomain() = ExpectedPayment(
    id = id,
    ownerId = ownerId,
    recurrenceId = recurrenceId,
    dueDate = LocalDate.parse(dueDate),
    expectedAmountMinor = expectedAmountMinor,
    currency = currency,
    status = ExpectedPaymentStatus.valueOf(status),
    matchedTransactionId = matchedTransactionId,
    matchedCandidateId = matchedCandidateId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
