package com.kipu.app.feature.capture.domain

import org.junit.Assert.*
import org.junit.Test

class CaptureCorrelationTest {
    private val notification = CorrelationFacts("capture:1", "NOTIFICATION:YAPE", "00-123", 5000,
        "PEN", true, "José Pérez", 1_000_000)
    private val share = notification.copy(root = "receipt:1", source = "OCR:YAPE", reference = "00123")
    @Test fun sameReferenceInBothOrdersPreservesLeadingZeroes() {
        assertTrue(CaptureCorrelation.sameReference(notification, share))
        assertTrue(CaptureCorrelation.sameReference(share, notification))
        assertFalse(CaptureCorrelation.sameReference(notification, share.copy(reference = "123")))
    }
    @Test fun missingReferenceNeedsExactlyOneCompatibleRootInEitherOrder() {
        val weak = share.copy(reference = null, counterparty = "  jose   perez ")
        assertEquals(notification.root, CaptureCorrelation.suggestion(weak, listOf(notification)))
        assertEquals(weak.root, CaptureCorrelation.suggestion(notification, listOf(weak)))
        assertNull(CaptureCorrelation.suggestion(weak, listOf(notification, notification.copy(root = "capture:2"))))
        assertNull(CaptureCorrelation.suggestion(weak, emptyList()))
    }
    @Test fun tenMinuteWindowIsInclusiveInBothDirections() {
        for (offset in listOf(-600_000L, 600_000L)) {
            assertTrue(CaptureCorrelation.compatible(notification, share.copy(reference = null, time = notification.time!! + offset)))
        }
        for (offset in listOf(-600_001L, 600_001L)) {
            assertFalse(CaptureCorrelation.compatible(notification, share.copy(reference = null, time = notification.time!! + offset)))
        }
    }
    @Test fun realPaymentsWithDistinctReferencesAndHistoricalRefundStaySeparate() {
        assertNull(CaptureCorrelation.suggestion(notification, listOf(share.copy(reference = "other"))))
        assertNull(CaptureCorrelation.suggestion(notification, listOf(share.copy(reference = null, income = false))))
        assertNull(CaptureCorrelation.suggestion(notification, listOf(share.copy(reference = null, time = 1))))
    }
    @Test fun noAmountOnlyMatchAndNoRepeatedSourceJoin() {
        for (other in listOf(share.copy(reference = null, currency = "USD"), share.copy(reference = null, amount = null),
            share.copy(reference = null, counterparty = null), share.copy(reference = null, income = null),
            share.copy(reference = null, time = null), share.copy(reference = null, counterparty = "Otra persona"))) {
            assertNull(CaptureCorrelation.suggestion(notification, listOf(other)))
        }
        val weak = share.copy(reference = null)
        assertNull(CaptureCorrelation.suggestion(weak, listOf(notification, notification.copy(source = weak.source))))
    }
    @Test fun yapeIncomeNotificationFixtureExtractsAmountDirectionAndOperation() {
        val parsed = YapeNotificationParser().parse(CaptureSignal(CaptureProvider.YAPE,
            "com.bcp.innovacxion.yapeapp", "notification-1", 1000, "Yape",
            "José Pérez te yape\u00f3 S/ 50.00 el 03/11/2026 10:30\nOperaci\u00f3n: 00123456"))
        assertEquals(5000L, parsed.amountMinor)
        assertEquals(CaptureDirection.INCOME, parsed.direction)
        assertEquals("00123456", parsed.operationReference)
        assertTrue(parsed.occurredAtReliable)
    }
    @Test fun sameReferenceCannotHideDifferentMoneyOrDirection() {
        for (other in listOf(share.copy(amount = 6000), share.copy(currency = "USD"), share.copy(income = false), share.copy(amount = null))) {
            assertFalse(CaptureCorrelation.sameReference(notification, other))
        }
    }
    @Test fun providerReferencesAreConservative() {
        for (provider in listOf(CaptureProvider.BCP, CaptureProvider.BBVA, CaptureProvider.INTERBANK, CaptureProvider.YAPE)) {
            fun parse(text: String) = NotificationCaptureParsers.forProvider(provider).parse(CaptureSignal(provider,"pkg","id",1,"Pago","Pagaste S/ 10.00. $text")).operationReference
            for (text in listOf("operacion con tu tarjeta", "Operacion exitosa", "Operaciones", "referencia 1234 5678", "Operacion: ABCDEF", "referencia123456")) assertNull("$provider $text", parse(text))
            for (text in listOf("Operacion: 00123456", "Referencia AB-123456", "Nro: 00123456", "N\u00b0 00123456", "#00123456")) assertNotNull("$provider $text", parse(text))
        }
    }
}
