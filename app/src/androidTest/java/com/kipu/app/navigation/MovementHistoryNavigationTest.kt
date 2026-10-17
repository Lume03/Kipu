package com.kipu.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementHistoryNavigationTest {
    @Test
    fun bankNavigationPassesAccountIdWithoutAPaymentChannel() {
        val navController = testNavController()

        navController.navigateToMovementHistory(accountId = "bank-account-id")

        assertEquals(MOVEMENTS_HISTORY_PATTERN, navController.currentDestination?.route)
        assertEquals("bank-account-id", navController.currentBackStackEntry?.arguments?.getString("accountId"))
        assertNull(navController.currentBackStackEntry?.arguments?.getString("paymentChannel"))
    }

    @Test
    fun walletNavigationPassesPaymentChannelWithoutAnAccountId() {
        val navController = testNavController()

        navController.navigateToMovementHistory(paymentChannel = "YAPE")

        assertEquals(MOVEMENTS_HISTORY_PATTERN, navController.currentDestination?.route)
        assertEquals("YAPE", navController.currentBackStackEntry?.arguments?.getString("paymentChannel"))
        assertNull(navController.currentBackStackEntry?.arguments?.getString("accountId"))
    }

    @Test
    fun paymentChannelOnlyDeepLinkInitializesTheHistoryDestination() {
        val navController = testNavController()
        val handled = navController.handleDeepLink(
            Intent(Intent.ACTION_VIEW, Uri.parse("kipu://movements/history?paymentChannel=PLIN")),
        )

        assertTrue(handled)
        assertEquals(MOVEMENTS_HISTORY_PATTERN, navController.currentDestination?.route)
        assertEquals("PLIN", navController.currentBackStackEntry?.arguments?.getString("paymentChannel"))
        assertNull(navController.currentBackStackEntry?.arguments?.getString("accountId"))
    }

    private fun testNavController(): TestNavHostController {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        navController.graph = navController.createGraph(startDestination = "test/start") {
            composable("test/start") {}
            movementsDestinations(navController)
        }
        return navController
    }
}
