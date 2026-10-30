package com.kipu.app.feature.statements.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementScanTimelineTest {
    @Test fun counterStartsAtZeroClimbsMonotonicallyAndLandsOnTheRealCount() {
        assertEquals(0, StatementScanTimeline.count(0f, 53))
        assertEquals(0, StatementScanTimeline.count(StatementScanTimeline.SCAN_START, 53))
        val samples = (7..31).map { StatementScanTimeline.count(it / 10f, 53) }
        assertEquals(samples.sorted(), samples)
        assertEquals(53, StatementScanTimeline.count(StatementScanTimeline.SCAN_END, 53))
        assertEquals(53, StatementScanTimeline.count(StatementScanTimeline.TOTAL, 53))
    }

    @Test fun scanLastsAboutThreeSecondsAndTheCheckComesAfterIt() {
        assertTrue(StatementScanTimeline.SCAN_END - StatementScanTimeline.SCAN_START in 2.2f..2.6f)
        assertTrue(StatementScanTimeline.RING > StatementScanTimeline.SCAN_END)
        assertTrue(StatementScanTimeline.CHECK > StatementScanTimeline.RING)
        assertTrue(StatementScanTimeline.TOTAL > StatementScanTimeline.CHECK)
    }

    @Test fun neverFinishesBeforeTheMovementsAreKnownOrTheCheckIsDrawn() {
        assertFalse(StatementScanTimeline.canFinish(StatementScanTimeline.TOTAL, known = false))
        assertFalse(StatementScanTimeline.canFinish(StatementScanTimeline.CHECK, known = true))
        assertTrue(StatementScanTimeline.canFinish(StatementScanTimeline.TOTAL, known = true))
    }
}
