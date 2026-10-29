package com.kipu.app.feature.auth.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCelebrationTimelineTest {
    private val created = AccountCreatedTimeline
    private val welcome = WelcomeBackTimeline

    /** Latest pop among the 18 knots (cord, index) of the field. */
    private fun lastPop(timing: FieldTiming): Float {
        val knotsPerCord = intArrayOf(3, 2, 4, 2, 3, 2, 3)
        var last = 0f
        for (cord in knotsPerCord.indices) for (index in 0 until knotsPerCord[cord]) {
            last = maxOf(last, timing.knotPop(cord, index))
        }
        return last
    }

    @Test
    fun `account created weaves the cords before the knots gather into the seal`() {
        assertTrue(created.mainStart < created.pendantStart(0))
        assertTrue(created.pendantStart(0) < created.pendantStart(6))
        assertTrue(lastPop(created) + created.popDuration <= created.GATHER_START)
        assertTrue(created.GATHER_START < created.DISC)
    }

    @Test
    fun `account created copy arrives in reading order after the seal`() {
        val order = listOf(created.CHECK, created.TITLE, created.SUBTITLE, created.EMAIL, created.NEXT, created.CTA)
        assertEquals(order.sorted(), order)
        assertTrue(created.DISC < created.CHECK)
        assertTrue(created.CTA < OnboardingTimeline.SETTLED)
    }

    @Test
    fun `welcome back hangs its cords before the light wave starts and keeps looping`() {
        val knotsPerCord = intArrayOf(3, 2, 4, 2, 3, 2, 3)
        for (cord in knotsPerCord.indices) for (index in 0 until knotsPerCord[cord]) {
            val wave = welcome.WAVE_START + cord * welcome.WAVE_CORD_STEP + index * welcome.WAVE_KNOT_STEP
            assertTrue("knot $cord/$index lights up only once it has popped", wave >= welcome.knotPop(cord, index) + welcome.popDuration)
        }
        assertTrue(welcome.TILE < welcome.TITLE && welcome.TITLE < welcome.EMAIL && welcome.EMAIL < welcome.CAPTION)
        assertTrue(welcome.WAVE_PERIOD > 0f)
    }

    @Test
    fun `welcome back exit gathers the knots before the iris closes`() {
        val lastGather = welcome.EXIT_GATHER + 17 * welcome.EXIT_GATHER_STEP + welcome.EXIT_GATHER_DURATION
        assertTrue(lastGather <= welcome.EXIT_IRIS + 0.01f)
        assertTrue(welcome.EXIT_LOGO_FADE < welcome.EXIT_CHECK)
        assertTrue(welcome.EXIT_CAPTION_OUT < welcome.EXIT_CAPTION_IN)
        assertEquals(welcome.EXIT_IRIS + welcome.EXIT_IRIS_DURATION, welcome.EXIT_TOTAL, 0.0001f)
    }

    @Test
    fun `the settled frame used without animations is past both entrances`() {
        assertTrue(created.CTA + 0.6f < OnboardingTimeline.SETTLED)
        assertTrue(welcome.CAPTION + 0.6f < OnboardingTimeline.SETTLED)
        assertTrue(welcome.EXIT_TOTAL < OnboardingTimeline.SETTLED)
    }

    @Test
    fun `back easing overshoots and then lands exactly on one`() {
        assertEquals(0f, easeOutBack(0f), 0.0001f)
        assertEquals(1f, easeOutBack(1f), 0.0001f)
        assertTrue(easeOutBack(0.6f) > 1f)
    }
}

class SessionEntryTimingTest {
    private val dashboard = "accounts/dashboard"

    @Test
    fun `curtain waits for both the minimum time and the dashboard`() {
        assertFalse(SessionEntryTiming.isReady(minElapsed = false, authenticated = true, homeReady = true))
        assertFalse(SessionEntryTiming.isReady(minElapsed = true, authenticated = true, homeReady = false))
        assertTrue(SessionEntryTiming.isReady(minElapsed = true, authenticated = true, homeReady = true))
    }

    @Test
    fun `elapsed time never reveals a destination before authentication or data`() {
        assertFalse(SessionEntryTiming.isReady(minElapsed = true, authenticated = false, homeReady = true))
        assertFalse(SessionEntryTiming.isReady(minElapsed = true, authenticated = true, homeReady = false))
    }

    @Test
    fun `readiness is ignored when no curtain is showing and each start is a new session`() {
        val curtain = SessionEntryCurtain()
        curtain.markHomeReady()
        assertEquals(null, curtain.state.value)
        curtain.start("  ana@correo.com ")
        val first = curtain.state.value!!
        assertEquals("ana@correo.com", first.email)
        assertFalse(first.homeReady)
        curtain.markHomeReady()
        assertTrue(curtain.state.value!!.homeReady)
        curtain.dismiss()
        curtain.start("ana@correo.com")
        assertTrue(curtain.state.value!!.id != first.id)
        assertFalse(curtain.state.value!!.homeReady)
    }
}
