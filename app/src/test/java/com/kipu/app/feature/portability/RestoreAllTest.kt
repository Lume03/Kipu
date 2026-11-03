package com.kipu.app.feature.portability
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
class RestoreAllTest {
    @Test fun runsHistoryAndFinancialFactsAfterFailure() = runTest {
        var count = 0
        assertFalse(restoreAll({ false }, { count++; error("history") }, { count++; true }))
        assertEquals(2, count)
    }
}
