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

    @Test fun bottomTabsRetainPlanningEntryViewModelsAndSelectionAcrossRepeatedVisits() {
        compose.runOnUiThread {
            val nav = TestNavHostController(compose.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                setViewModelStore(ViewModelStore())
            }
            nav.graph = nav.createGraph(startDestination = ACCOUNTS_DASHBOARD_ROUTE) {
                composable(ACCOUNTS_DASHBOARD_ROUTE) {}
                composable(PLANNING_ROUTE) {}
                composable(ANALYSIS_ROUTE) {}
            }
            nav.navigateToPlanning(PlanningTab.DEBTS)
            val original = nav.currentBackStackEntry!!
            val store = original.viewModelStore
            original.savedStateHandle["scroll_marker"] = 7
            repeat(4) {
                nav.navigateRootTab(ANALYSIS_ROUTE)
                nav.navigateRootTab(ACCOUNTS_DASHBOARD_ROUTE)
                nav.navigateRootTab(PLANNING_ROUTE)
                assertEquals(original.id, nav.currentBackStackEntry!!.id)
                assertSame(store, nav.currentBackStackEntry!!.viewModelStore)
                assertEquals(PlanningTab.DEBTS.ordinal, nav.currentBackStackEntry!!.savedStateHandle.get<Int>(PLANNING_TAB_KEY))
                assertEquals(7, nav.currentBackStackEntry!!.savedStateHandle.get<Int>("scroll_marker"))
            }
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
            val nav = TestNavHostController(compose.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                setViewModelStore(ViewModelStore())
            }
            nav.graph = nav.createGraph(startDestination = ACCOUNTS_DASHBOARD_ROUTE) {
                composable(ACCOUNTS_DASHBOARD_ROUTE) {}
                composable(PLANNING_ROUTE) {}
            }

            nav.navigateToPlanning(PlanningTab.GOALS)
            val depth = nav.currentBackStack.value.size
            nav.navigateToPlanning(PlanningTab.BUDGETS)
            nav.navigateToPlanning(PlanningTab.DEBTS)

            assertEquals(PLANNING_ROUTE, nav.currentDestination?.route)
            assertEquals(depth, nav.currentBackStack.value.size)
            assertEquals(PlanningTab.DEBTS.ordinal, nav.currentBackStackEntry?.savedStateHandle?.get<Int>(PLANNING_TAB_KEY))
            nav.popBackStack()
            assertEquals(ACCOUNTS_DASHBOARD_ROUTE, nav.currentDestination?.route)
        }
    }
}
