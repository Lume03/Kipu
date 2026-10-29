package com.kipu.app.navigation

import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PostAuthDestinationTest {
    @Test fun `configured recurring account opens money`() = runTest {
        assertEquals(ACCOUNTS_DASHBOARD_ROUTE, resolvePostAuthDestination(null) { true })
    }
    @Test fun `new account without plan opens plan selection`() = runTest {
        assertEquals(PLAN_SELECTION_ROUTE, resolvePostAuthDestination(null) { false })
    }
    @Test fun `notification destination survives login without preference lookup`() = runTest {
        for (route in listOf("notifications/center", "debts/detail/id", "capture/candidate/id")) {
            assertEquals(route, resolvePostAuthDestination(route) { error("Unneeded lookup") })
        }
    }
    @Test fun `preference lookup suspends without blocking welcome animation`() = runTest {
        val preference = CompletableDeferred<Boolean>()
        val curtain = com.kipu.app.feature.auth.presentation.SessionEntryCurtain()
        curtain.start("test@example.com")
        curtain.authenticated()
        val route = async { resolvePostAuthDestination(null) { preference.await() } }
        assertNotNull(curtain.state.value)
        assertFalse(route.isCompleted)
        preference.complete(false)
        assertEquals(PLAN_SELECTION_ROUTE, route.await())
    }
}
