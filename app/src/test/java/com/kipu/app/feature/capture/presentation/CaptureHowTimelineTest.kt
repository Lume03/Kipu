package com.kipu.app.feature.capture.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHowTimelineTest {
    private val t = CaptureHowTimeline

    @Test
    fun `progress is clamped between not started and finished`() {
        assertEquals(0f, t.progress(0f, 2f, 1f), 0f)
        assertEquals(0.5f, t.progress(2.5f, 2f, 1f), 0.0001f)
        assertEquals(1f, t.progress(9f, 2f, 1f), 0f)
    }

    @Test
    fun `each block enters only after the link that feeds it`() {
        // Bank block first, then link one, then Kipu, then link two, then the ledger.
        assertTrue(t.NOTIFICATION_BLOCK < t.NOTIFICATION)
        assertTrue(t.NOTIFICATION < t.HIGHLIGHT_AMOUNT && t.HIGHLIGHT_AMOUNT < t.HIGHLIGHT_MERCHANT)
        assertTrue(t.HIGHLIGHT_MERCHANT < t.LINK_ONE)
        assertTrue(t.LINK_ONE < t.KIPU_BLOCK)
        assertTrue(t.KIPU_BLOCK < t.TILES.first())
        assertTrue(t.TILES.toList() == t.TILES.sorted())
        assertTrue(t.TILES.last() < t.READING_OUT && t.READING_OUT < t.CONFIDENCE)
        assertTrue(t.CONFIDENCE < t.LINK_TWO)
        assertTrue(t.LINK_TWO < t.LEDGER_BLOCK)
        assertTrue(t.LEDGER_BLOCK < t.NEW_ROW)
    }

    @Test
    fun `footer buttons appear and become active only after the registration`() {
        assertTrue(t.NEW_ROW < t.FOOTER)
        assertTrue(t.DONE_BADGE < t.FOOTER)
        assertTrue(t.FOOTER < t.FOOTER_ACTIVE)
        assertTrue(t.NEW_CHIP_OUT < t.END)
        assertTrue(t.FOOTER_ACTIVE < t.END)
    }
}
