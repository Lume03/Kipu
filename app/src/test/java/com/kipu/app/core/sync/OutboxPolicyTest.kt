package com.kipu.app.core.sync

import org.junit.Assert.*
import org.junit.Test

class OutboxPolicyTest {
    @Test fun classifiesFailures() {
        assertEquals(OutboxPolicy.Failure.WAITING_FOR_AUTH, OutboxPolicy.classify(httpStatus = 401))
        assertEquals(OutboxPolicy.Failure.WAITING_FOR_AUTH, OutboxPolicy.classify(hasSession = false))
        assertEquals(OutboxPolicy.Failure.CONFLICT, OutboxPolicy.classify(httpStatus = 409))
        assertEquals(OutboxPolicy.Failure.CONFLICT, OutboxPolicy.classify(status = "CONFLICT"))
        assertEquals(OutboxPolicy.Failure.PERMANENT, OutboxPolicy.classify(httpStatus = 422))
        assertEquals(OutboxPolicy.Failure.PERMANENT, OutboxPolicy.classify(invalidPayload = true))
        assertEquals(OutboxPolicy.Failure.PERMANENT, OutboxPolicy.classify(status = "REJECTED", retryable = false))
        for (code in listOf(404, 408, 425, 429, 503)) assertEquals(OutboxPolicy.Failure.RETRYABLE, OutboxPolicy.classify(httpStatus = code))
        assertEquals(OutboxPolicy.Failure.RETRYABLE, OutboxPolicy.classify())
    }
    @Test fun terminalCommandsCannotPrecedeNewCommandsOrBeRetried() {
        for (state in listOf("CONFLICT", "FAILED_PERMANENT", "BLOCKED", "DISCARDED")) {
            assertFalse(OutboxPolicy.canPrecede(state))
            assertFalse(OutboxPolicy.canRetry(state))
        }
        assertTrue(OutboxPolicy.canRetry("WAITING_FOR_AUTH"))
        assertTrue(OutboxPolicy.canPrecede("SYNCED"))
    }
}
