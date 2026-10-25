package com.kipu.app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: the dashboard "Calendario financiero" entry crashed because the app graph lacked the route. */
class CalendarNavigationTest {
    @Test
    fun calendarEntryFromTheDashboardIsRegisteredInTheAppGraph() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.graph = navController.createGraph(startDestination = AUTH_START_ROUTE) {
                appDestinations(navController = navController, onAuthenticated = {}, onSignOut = {})
            }
            navController.navigate(ACCOUNTS_DASHBOARD_ROUTE)

            navController.navigateToCalendar()

            assertEquals(CALENDAR_ROUTE, navController.currentDestination?.route)
            navController.popBackStack()
            assertEquals(ACCOUNTS_DASHBOARD_ROUTE, navController.currentDestination?.route)
        }
}
