package com.kipu.app.feature.accounts.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSensitiveAliasTest {
    @Test fun rejectsPanInAlias() {
        assertTrue(Card.isForbiddenSensitiveCardInput("1234567890123"))
        assertTrue(Card.isForbiddenSensitiveCardInput("1234 5678 9012 3456"))
        assertTrue(Card.isForbiddenSensitiveCardInput("1234-5678-9012-3456"))
        assertFalse(Card.isForbiddenSensitiveCardInput("Visa 1234"))
    }
}
