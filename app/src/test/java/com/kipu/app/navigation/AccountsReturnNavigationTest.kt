package com.kipu.app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountsReturnNavigationTest {
    @Test
    fun successFromWizardDetailReturnsDashboardWithFeedbackAndRemovesWizard() = onMain {
        val navController = testNavController()
        navController.navigate("wizard")
        navController.navigate("card-detail")

        navController.returnToAccountsDashboard("Tarjeta archivada")

        assertDashboardFeedback(navController)
        assertFalse(navController.popBackStack())
    }

    @Test
    fun successFromDirectDetailReturnsDashboardWithFeedback() = onMain {
        val navController = testNavController()
        navController.navigate("card-detail")

        navController.returnToAccountsDashboard("Tarjeta archivada")

        assertDashboardFeedback(navController)
        assertFalse(navController.popBackStack())
    }

    @Test
    fun successWithoutDashboardUsesDashboardFallbackAndFeedback() = onMain {
        val navController = testNavController(startAtWizard = true)

        navController.returnToAccountsDashboard("Tarjeta archivada")

        assertDashboardFeedback(navController)
        assertFalse(navController.popBackStack())
    }

    private fun assertDashboardFeedback(navController: TestNavHostController) {
        assertEquals(ACCOUNTS_DASHBOARD_ROUTE, navController.currentDestination?.route)
        assertEquals(
            "Tarjeta archivada",
            navController.currentBackStackEntry?.savedStateHandle
                ?.get<String>(ACCOUNT_DASHBOARD_FEEDBACK_KEY),
        )
    }

    private fun testNavController(startAtWizard: Boolean = false): TestNavHostController {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            setViewModelStore(ViewModelStore())
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        navController.graph = navController.createGraph(
            startDestination = if (startAtWizard) "wizard" else ACCOUNTS_DASHBOARD_ROUTE,
        ) {
            composable(ACCOUNTS_DASHBOARD_ROUTE) {}
            composable("wizard") {}
            composable("card-detail") {}
        }
        return navController
    }

    private fun <T> onMain(block: () -> T): T = block()
}
