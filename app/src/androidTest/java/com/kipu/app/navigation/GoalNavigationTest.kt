package com.kipu.app.navigation

import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalNavigationTest {
    @Test
    fun goalsOpenAsThePlanningPageOfTheDashboardInsteadOfASeparateDestination() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.setViewModelStore(ViewModelStore())
            navController.graph = navController.createGraph(startDestination = "auth/start") {
                composable("auth/start") {}
                accountsDestinations(navController)
                goalDestinations(navController)
            }

            navController.navigateToGoals()

            assertEquals(ACCOUNTS_DASHBOARD_ROUTE, navController.currentDestination?.route)
            val handle = navController.currentBackStackEntry!!.savedStateHandle
            assertEquals(RootTab.PLANNING, handle.get<Int>(ROOT_TAB_KEY))
            assertEquals(PlanningTab.GOALS.ordinal, handle.get<Int>(PLANNING_TAB_KEY))
            assertNull(navController.graph.findNode(PLANNING_ROUTE))
        }
}
