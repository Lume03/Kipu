package com.kipu.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PlanningTabsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun dashboardNav(): TestNavHostController =
        TestNavHostController(compose.activity).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setViewModelStore(ViewModelStore())
            graph = createGraph(startDestination = ACCOUNTS_DASHBOARD_ROUTE) {
                composable(ACCOUNTS_DASHBOARD_ROUTE) {}
                composable(GOAL_CREATE_ROUTE) {}
            }
        }

    @Test fun bottomTabsNeverNavigateAndKeepPlanningViewModelsAndSelection() {
        compose.runOnUiThread {
            val nav = dashboardNav()
            nav.navigateToPlanning(PlanningTab.DEBTS)
            val original = nav.currentBackStackEntry!!
            val depth = nav.currentBackStack.value.size
            val store = original.viewModelStore
            original.savedStateHandle["scroll_marker"] = 7
            repeat(4) {
                listOf(RootTab.ANALYSIS, RootTab.MONEY, RootTab.MOVEMENTS, RootTab.PLANNING).forEach { tab ->
                    nav.selectRootTab(tab)
                    // Same entry, same depth: the page switch is only an animation inside the dashboard.
                    assertSame(original, nav.currentBackStackEntry)
                    assertEquals(depth, nav.currentBackStack.value.size)
                    assertEquals(tab, original.savedStateHandle.get<Int>(ROOT_TAB_KEY))
                }
                assertSame(store, nav.currentBackStackEntry!!.viewModelStore)
                assertEquals(PlanningTab.DEBTS.ordinal, original.savedStateHandle.get<Int>(PLANNING_TAB_KEY))
                assertEquals(7, original.savedStateHandle.get<Int>("scroll_marker"))
            }
        }
    }

    @Test fun openingPlanningFromAChildScreenReturnsToTheDashboardPage() {
        compose.runOnUiThread {
            val nav = dashboardNav()
            nav.navigate(GOAL_CREATE_ROUTE)
            nav.navigateToPlanning(PlanningTab.BUDGETS)
            assertEquals(ACCOUNTS_DASHBOARD_ROUTE, nav.currentDestination?.route)
            val handle = nav.currentBackStackEntry!!.savedStateHandle
            assertEquals(RootTab.PLANNING, handle.get<Int>(ROOT_TAB_KEY))
            assertEquals(PlanningTab.BUDGETS.ordinal, handle.get<Int>(PLANNING_TAB_KEY))
        }
    }

    @Test fun segmentedTabsOfferGoalsBudgetsAndDebtsInOrderAndMoveSelection() {
        compose.setContent {
            KipuTheme {
                var selected by remember { mutableStateOf(PlanningTab.GOALS) }
                PlanningSegmentedTabs(selected = selected, onSelect = { selected = it })
            }
        }

        compose.onNodeWithTag("planning_tab_goals").assertIsSelected()
        compose.onNodeWithTag("planning_tab_budgets").performClick()
        compose.onNodeWithTag("planning_tab_budgets").assertIsSelected()
        compose.onNodeWithTag("planning_tab_goals").assertIsNotSelected()
        compose.onNodeWithTag("planning_tab_debts").performClick()
        compose.onNodeWithTag("planning_tab_debts").assertIsSelected()
        assertEquals(listOf("Metas", "Presupuestos", "Deudas"), PlanningTab.entries.map { it.label })
    }

    @Test fun switchingPlanningTabsStaysOnOneDestinationWithoutStackingEntries() {
        compose.runOnUiThread {
            val nav = dashboardNav()
            nav.navigateToPlanning(PlanningTab.GOALS)
            val depth = nav.currentBackStack.value.size
            nav.navigateToPlanning(PlanningTab.BUDGETS)
            nav.navigateToPlanning(PlanningTab.DEBTS)

            assertEquals(ACCOUNTS_DASHBOARD_ROUTE, nav.currentDestination?.route)
            assertEquals(depth, nav.currentBackStack.value.size)
            assertEquals(PlanningTab.DEBTS.ordinal, nav.currentBackStackEntry?.savedStateHandle?.get<Int>(PLANNING_TAB_KEY))
        }
    }
}
