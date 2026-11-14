package com.kipu.app.feature.movements.presentation

import org.junit.Assert.*
import org.junit.Test

class RefundRejectionMessageTest {
    @Test fun protocolRejectionsHaveDistinctActionableMessages() {
        val codes = listOf("REFUND_EXCEEDS_REMAINDER", "PURCHASE_NOT_FOUND", "PURCHASE_NOT_ELIGIBLE", "REFUND_REQUIRES_CARD_PURCHASE", "CURRENCY_MISMATCH", "INVALID_AMOUNT_OR_CURRENCY", "INVALID_REFUND_DATE", "INVALID_TIMESTAMP_PRECISION", "INVALID_REQUEST_HASH", "IDEMPOTENCY_CONFLICT", "TRANSACTION_ID_ALREADY_EXISTS", "UNAUTHORIZED", "HTTP_401", "INVALID_COMMAND")
        assertEquals(codes.size, codes.map(::refundRejectionMessage).toSet().size)
        assertTrue(refundRejectionMessage("FUTURE_CODE").contains("FUTURE_CODE"))
    }
}
