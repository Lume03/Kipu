package com.kipu.app.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordUnicodeTest {
    @Test fun unicodeAndNfc() {
        assertTrue(PasswordValidator.validatePassword("\u00e1\u00e1\u00e1\u00e1\u00e1\u00e1\u00e1\u0661").isValid)
        assertFalse(PasswordValidator.validatePassword("e\u0301".repeat(6) + "1").isValid)
        assertFalse(PasswordValidator.validatePassword("12345678").isValid)
    }
}
