package com.kipu.app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsNavigationTest {
    @Test fun directEntryAndBackUseRegisteredGraph() = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = AUTH_START_ROUTE) {
            appDestinations(navController = nav, onAuthenticated = {}, onSignOut = {})
        }
        nav.navigate(ACCOUNTS_DASHBOARD_ROUTE)
        nav.navigateToAnalytics()
        assertEquals(ANALYTICS_ROUTE, nav.currentDestination?.route)
        nav.navigateToAnalytics()
        nav.popBackStack()
        assertEquals(ACCOUNTS_DASHBOARD_ROUTE, nav.currentDestination?.route)
    }
}
