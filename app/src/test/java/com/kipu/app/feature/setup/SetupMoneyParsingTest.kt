package com.kipu.app.feature.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupMoneyParsingTest {
    @Test fun `comma decimal is parsed as cents`() {
        assertEquals(15050L, SetupAccountDraft(balanceText = "150,50").balanceMinorUnits)
    }
}
