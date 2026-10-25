package com.kipu.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTabMotionTest {
    @Test
    fun rootTabsFollowTheBottomBarOrder() {
        assertEquals(0, RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 0))
        assertEquals(1, RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 1))
        assertEquals(2, RootTabMotion.indexOf(PLANNING_ROUTE, selectedDashboardTab = 0))
        assertEquals(3, RootTabMotion.indexOf(ANALYSIS_ROUTE, selectedDashboardTab = 0))
    }

    @Test
    fun dashboardNoLongerHostsADebtTab() {
        assertNull(RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 2))
    }

    @Test
    fun planningToMoneyAndMovementsMovesBackward() {
        listOf(0, 1).forEach { destinationTab ->
            assertTrue(RootTabMotion.isRootTransition(PLANNING_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, destinationTab))
            assertFalse(RootTabMotion.isForward(PLANNING_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, destinationTab))
        }
    }

    @Test
    fun planningToAnalysisMovesForward() {
        assertTrue(RootTabMotion.isRootTransition(PLANNING_ROUTE, ANALYSIS_ROUTE, selectedDashboardTab = 0))
        assertTrue(RootTabMotion.isForward(PLANNING_ROUTE, ANALYSIS_ROUTE, selectedDashboardTab = 0))
        assertFalse(RootTabMotion.isForward(ANALYSIS_ROUTE, PLANNING_ROUTE, selectedDashboardTab = 0))
    }

    @Test
    fun planningDetailsAreNotRootTransitions() {
        assertFalse(RootTabMotion.isRootTransition(PLANNING_ROUTE, GOAL_CREATE_ROUTE, selectedDashboardTab = 0))
        assertFalse(RootTabMotion.isRootTransition(PLANNING_ROUTE, BUDGET_DETAIL_ROUTE, selectedDashboardTab = 0))
    }
}
