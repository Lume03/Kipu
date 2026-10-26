package com.kipu.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTabMotionTest {
    @Test
    fun everyBottomBarPageIsADashboardPageInBarOrder() {
        (0 until RootTab.COUNT).forEach { tab ->
            assertEquals(tab, RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = tab))
        }
        assertEquals(RootTab.MOVEMENTS, RootTabMotion.indexOf(MOVEMENTS_HISTORY_ROUTE, selectedDashboardTab = 0))
    }

    @Test
    fun planningAndAnalysisAreNotNavHostRoutes() {
        assertNull(RootTabMotion.indexOf(PLANNING_ROUTE, selectedDashboardTab = 0))
        assertNull(RootTabMotion.indexOf(ANALYSIS_ROUTE, selectedDashboardTab = 0))
        assertNull(RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = RootTab.COUNT))
    }

    @Test
    fun filteredHistoryToPlanningMovesForwardAndToMoneyBackward() {
        assertTrue(RootTabMotion.isRootTransition(MOVEMENTS_HISTORY_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, RootTab.PLANNING))
        assertTrue(RootTabMotion.isForward(MOVEMENTS_HISTORY_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, RootTab.PLANNING))
        assertTrue(RootTabMotion.isRootTransition(MOVEMENTS_HISTORY_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, RootTab.MONEY))
        assertFalse(RootTabMotion.isForward(MOVEMENTS_HISTORY_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, RootTab.MONEY))
    }

    @Test
    fun planningDetailsAreNotRootTransitions() {
        assertFalse(RootTabMotion.isRootTransition(ACCOUNTS_DASHBOARD_ROUTE, GOAL_CREATE_ROUTE, RootTab.PLANNING))
        assertFalse(RootTabMotion.isRootTransition(ACCOUNTS_DASHBOARD_ROUTE, BUDGET_DETAIL_ROUTE, RootTab.PLANNING))
    }
}
