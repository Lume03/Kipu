package com.kipu.app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalNavigationTest {
    @Test
    fun planningRootDestinationIsRegisteredAndNavigableFromTheAuthenticatedGraph() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.graph = navController.createGraph(startDestination = "auth/start") {
                composable("auth/start") {}
                goalDestinations(navController)
            }

            navController.navigate(GOALS_LIST_ROUTE)

            assertEquals(GOALS_LIST_ROUTE, navController.currentDestination?.route)
        }
}
