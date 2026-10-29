package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEntitlementRefreshTest {
    private val access = MutableStateFlow<LocalAccess>(LocalAccess.Available("owner", RemoteSession.Absent))
    private val session = mockk<SessionCoordinator> { every { localAccess } returns access }
    private val delegate = mockk<TestPlanRefresh>()
    private var now = 1_000_000L
    private val clock = mockk<Clock> { every { millis() } answers { now } }
    private val refresh = PlanEntitlementRefresh(delegate, session, clock)

    @Test fun `concurrent foreground and login triggers refresh once then allow after fifteen minutes`() = runTest {
        coEvery { delegate.refresh() } returns TestPlanRefreshResult(true)
        List(4) { async { refresh.refreshIfNeeded() } }.awaitAll()
        now += PlanEntitlementRefresh.THROTTLE_MILLIS - 1
        assertTrue(refresh.refreshIfNeeded().success)
        coVerify(exactly = 1) { delegate.refresh() }
        now++
        assertTrue(refresh.refreshIfNeeded().success)
        coVerify(exactly = 2) { delegate.refresh() }
    }

    @Test fun `failed refresh and exceptions do not throttle a later retry`() = runTest {
        coEvery { delegate.refresh() } returns TestPlanRefreshResult(false, "OFFLINE")
        assertFalse(refresh.refreshIfNeeded().success)
        coEvery { delegate.refresh() } throws IllegalStateException("offline")
        assertEquals("REFRESH_UNAVAILABLE", refresh.refreshIfNeeded().code)
        coEvery { delegate.refresh() } returns TestPlanRefreshResult(true)
        assertTrue(refresh.refreshIfNeeded().success)
        coVerify(exactly = 3) { delegate.refresh() }
    }

    @Test fun `throttle is isolated by owner and stale owner work does not refresh another account`() = runTest {
        coEvery { delegate.refresh() } returns TestPlanRefreshResult(true)
        refresh.refreshIfNeeded("owner")
        access.value = LocalAccess.Available("other", RemoteSession.Absent)
        assertEquals("SESSION_CHANGED", refresh.refreshIfNeeded("owner").code)
        assertTrue(refresh.refreshIfNeeded("other").success)
        access.value = LocalAccess.NoOwner
        assertEquals("UNAUTHENTICATED", refresh.refreshIfNeeded().code)
        coVerify(exactly = 2) { delegate.refresh() }
    }

    @Test fun `clock regression permits revalidation rather than suppressing refresh`() = runTest {
        coEvery { delegate.refresh() } returns TestPlanRefreshResult(true)
        refresh.refreshIfNeeded()
        now--
        refresh.refreshIfNeeded()
        coVerify(exactly = 2) { delegate.refresh() }
    }

    @Test fun `cancellation propagates and is not recorded as success`() = runTest {
        coEvery { delegate.refresh() } throws CancellationException("cancel")
        try {
            refresh.refreshIfNeeded()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) { }
        coEvery { delegate.refresh() } returns TestPlanRefreshResult(true)
        assertTrue(refresh.refreshIfNeeded().success)
        coVerify(exactly = 2) { delegate.refresh() }
    }
}
