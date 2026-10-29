package com.kipu.app.feature.auth.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTimelineTest {
    private val t = OnboardingTimeline

    @Test
    fun `progress is clamped between not started and finished`() {
        assertEquals(0f, t.progress(0f, 2f, 1f), 0f)
        assertEquals(0.5f, t.progress(2.5f, 2f, 1f), 0.0001f)
        assertEquals(1f, t.progress(9f, 2f, 1f), 0f)
    }

    @Test
    fun `money scene brings the cards in after the total and the movement last`() {
        val m = OnboardingTimeline.Money
        assertTrue(m.TOTAL < m.COUNT_START)
        assertTrue(m.SAVINGS < m.CARD && m.CARD < m.TRANSACTION)
        assertTrue(m.TOTAL < m.LIVE)
    }

    @Test
    fun `ledger scene flows from sources to movement to Kipu before it reports organized`() {
        val l = OnboardingTimeline.Ledger
        assertTrue(l.SOURCES.toList() == l.SOURCES.sorted())
        assertTrue(l.SOURCES.last() < l.LINK_ONE)
        assertTrue(l.LINK_ONE < l.MOVEMENT && l.MOVEMENT < l.LINK_TWO)
        assertTrue(l.LINK_TWO < l.KIPU)
        assertTrue(l.KIPU < l.WORKING && l.WORKING < l.WORKING_END && l.WORKING_END < l.DONE)
    }

    @Test
    fun `privacy scene draws each line before its module appears`() {
        val p = OnboardingTimeline.Privacy
        for (i in p.LINES.indices) assertTrue(p.LINES[i] < p.MODULES[i])
        assertTrue(p.MODULES[1] < p.HIGHLIGHT)
        assertTrue(p.CORE < p.CHIP)
    }

    @Test
    fun `every scripted moment ends before the settled frame used without animations`() {
        val last = maxOf(OnboardingTimeline.Ledger.DONE + 0.5f, OnboardingTimeline.Privacy.HIGHLIGHT + 0.7f, OnboardingTimeline.Money.COUNT_START + OnboardingTimeline.Money.COUNT_DURATION)
        assertTrue(last < t.SETTLED)
        assertTrue(t.SETTLED < t.LOOP_END)
    }

    @Test
    fun `soles are formatted with fixed grouping`() {
        assertEquals("S/ 8,420.50", formatSoles(842_050L))
        assertEquals("S/ 0.00", formatSoles(0L))
        assertEquals("S/ 3,850.00", formatSoles(385_000L))
    }
}
