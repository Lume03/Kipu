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
    fun recurrenceEventOpensItsOwnRegisteredDetailRouteWithId() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.graph = navController.createGraph(startDestination = AUTH_START_ROUTE) {
                appDestinations(navController = navController, onAuthenticated = {}, onSignOut = {})
            }
            navController.navigateToCalendar()
            navController.navigateToCalendarEventSource(recurrenceEvent("rule / 1"))
            assertEquals("calendar/recurrence/{recurrenceId}", navController.currentDestination?.route)
            assertEquals("rule / 1", navController.currentBackStackEntry?.arguments?.getString("recurrenceId"))
            navController.popBackStack()
            assertEquals(CALENDAR_ROUTE, navController.currentDestination?.route)
        }

    @Test
    fun unavailableRecurrenceEventLeavesCalendarOpen() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.graph = navController.createGraph(startDestination = AUTH_START_ROUTE) {
                appDestinations(navController = navController, onAuthenticated = {}, onSignOut = {})
            }
            navController.navigateToCalendar()
            val before = navController.currentBackStackEntry
            navController.navigateToCalendarEventSource(recurrenceEvent("gone").copy(isSourceAvailable = false))
            assertEquals(before, navController.currentBackStackEntry)
        }

    private fun recurrenceEvent(id: String) = com.kipu.app.feature.calendar.domain.CalendarEvent(
        id = "event-$id", date = java.time.LocalDate.parse("2026-11-05"),
        kind = com.kipu.app.feature.calendar.domain.CalendarEventKind.RECURRENCE,
        status = com.kipu.app.feature.calendar.domain.CalendarEventStatus.PLANNED,
        title = "Pago esperado", source = com.kipu.app.feature.calendar.domain.CalendarEventSource.Recurrence(id),
    )

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
