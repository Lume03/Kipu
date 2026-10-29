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
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMappersTest {
    private val record = CaptureCandidateRecord(
        id = "c1", ownerId = "o1", sourcePackage = "com.bcp.bank.bcp", provider = CaptureProvider.BCP,
        sourceEventFingerprint = "fp", status = CaptureCandidateStatus.PENDING, direction = CaptureDirection.EXPENSE,
        amountMinor = 12_000, currency = "PEN", occurredAt = 1_000, occurredAtInferred = true,
        merchantSourceText = "PLAZA VEA", merchantId = "m1", categoryId = "cat1", cardLast4 = "1234",
        instrumentKind = CaptureInstrumentKind.CREDIT_CARD, accountId = null, cardId = "card1",
        instrumentAmbiguous = false, confidence = CaptureConfidenceLevel.MEDIUM,
        confidenceReasons = listOf(CaptureReason.DATE_INFERRED, CaptureReason.CREDIT_CARD_REQUIRES_REVIEW),
        creditMode = CreditMode.INSTALLMENTS, installmentCount = 3, resolvedTransactionId = null,
        resolutionKey = null, evidenceSnippet = "Consumo de S/ 120.00", evidenceExpiresAt = 9_000,
        userCorrectedFields = listOf("amount", "merchant"), createdAt = 10, updatedAt = 11, resolvedAt = null,
    )

    @Test fun `candidate round trips through the entity`() {
        assertEquals(record, record.toEntity().toDomain())
    }

    @Test fun `candidate with nullable fields round trips`() {
        val sparse = record.copy(
            direction = null, amountMinor = null, currency = null, occurredAt = null, merchantSourceText = null,
            merchantId = null, categoryId = null, cardLast4 = null, instrumentKind = null, cardId = null,
            confidence = CaptureConfidenceLevel.LOW, confidenceReasons = emptyList(), creditMode = null,
            installmentCount = null, evidenceSnippet = null, evidenceExpiresAt = null, userCorrectedFields = emptyList(),
        )
        assertEquals(sparse, sparse.toEntity().toDomain())
        assertEquals("[]", sparse.toEntity().confidenceReasons)
    }

    @Test fun `reason codes are stored as a json array and unknown codes are dropped`() {
        assertEquals("[\"DATE_INFERRED\",\"CREDIT_CARD_REQUIRES_REVIEW\"]", record.toEntity().confidenceReasons)
        assertEquals(
            listOf(CaptureReason.DATE_INFERRED),
            CaptureReason.fromCodes("[\"DATE_INFERRED\",\"REMOVED_IN_FUTURE\"]".fromJsonCodes()),
        )
    }

    @Test fun `every reason has a spanish label`() {
        assertTrue(CaptureReason.entries.all { it.label.isNotBlank() })
    }

    @Test fun `source event round trips`() {
        val event = CaptureSourceEvent("o1", "fp", "com.bcp.bank.bcp", 5, CaptureSourceOutcome.DUPLICATE)
        assertEquals(event, event.toEntity().toDomain())
    }

    @Test fun `expected payment stores due date as sortable iso text`() {
        val payment = ExpectedPayment(
            "e1", "o1", "r1", LocalDate.of(2026, 11, 5), 4_990, "PEN", ExpectedPaymentStatus.EXPECTED, null, null, 1, 2,
        )
        assertEquals("2026-11-05", payment.toEntity().dueDate)
        assertEquals(payment, payment.toEntity().toDomain())
    }
}
