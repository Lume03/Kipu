package com.kipu.app.navigation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.hilt.navigation.compose.hiltViewModel
import com.kipu.app.feature.movements.presentation.MovementEditorRoute
import com.kipu.app.feature.movements.presentation.MovementHistoryRoute
import com.kipu.app.feature.recurrences.presentation.RecurrencePresentationViewModel
import com.kipu.app.feature.settings.presentation.SettingsViewModel

const val MOVEMENTS_HISTORY_ROUTE = "movements/history"
const val MOVEMENTS_HISTORY_PATTERN = "movements/history?accountId={accountId}&categoryId={categoryId}&query={query}&paymentChannel={paymentChannel}"
const val MOVEMENT_EDITOR_ROUTE = "movements/editor/{transactionId}?merchantName={merchantName}"

fun NavController.navigateToMovementHistory(
    accountId: String? = null,
    categoryId: String? = null,
    query: String? = null,
    paymentChannel: String? = null,
) {
    val params = mutableListOf<String>()
    if (!accountId.isNullOrBlank()) params.add("accountId=${Uri.encode(accountId)}")
    if (!categoryId.isNullOrBlank()) params.add("categoryId=${Uri.encode(categoryId)}")
    if (!query.isNullOrBlank()) params.add("query=${Uri.encode(query)}")
    if (!paymentChannel.isNullOrBlank()) params.add("paymentChannel=${Uri.encode(paymentChannel)}")
    val route = if (params.isEmpty()) MOVEMENTS_HISTORY_ROUTE else "$MOVEMENTS_HISTORY_ROUTE?${params.joinToString("&")}"
    navigate(route)
}

fun NavController.navigateToMovementEditor(transactionId: String, merchantName: String? = null) {
    val merchantQuery = merchantName?.takeIf(String::isNotBlank)
        ?.let { "?merchantName=${Uri.encode(it)}" }
        .orEmpty()
    navigate("movements/editor/${Uri.encode(transactionId)}$merchantQuery")
}

fun NavGraphBuilder.movementsDestinations(
    navController: NavController,
) {
    composable(
        route = MOVEMENTS_HISTORY_PATTERN,
        arguments = listOf(
            navArgument("accountId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("categoryId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("query") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("paymentChannel") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "kipu://movements/history?accountId={accountId}&categoryId={categoryId}&query={query}&paymentChannel={paymentChannel}" },
            navDeepLink { uriPattern = "kipu://movements/history?accountId={accountId}" },
            navDeepLink { uriPattern = "kipu://movements/history?paymentChannel={paymentChannel}" },
        )
    ) { historyEntry ->
        MovementHistoryContent(navController, historyEntry)
    }
    composable(
        route = MOVEMENT_EDITOR_ROUTE,
        arguments = listOf(
            navArgument("transactionId") { type = NavType.StringType },
            navArgument("merchantName") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )
    ) { backStackEntry ->
        val transactionId = backStackEntry.arguments?.getString("transactionId").orEmpty()
        val historyEntry = navController.previousBackStackEntry
        val merchantName = backStackEntry.arguments?.getString("merchantName")
            ?: historyEntry?.savedStateHandle?.remove<String>("movement_editor_merchant_name")
        MovementEditorRoute(
            transactionId = transactionId,
            onDismiss = { navController.popBackStack() },
            onSaved = { navController.previousBackStackEntry?.savedStateHandle?.set("movement_saved", true); navController.popBackStack() },
        )
    }
}


@Composable
internal fun MovementHistoryContent(navController: NavController, historyEntry: NavBackStackEntry, movementsSelected: State<Boolean>? = null) {
        val saved by historyEntry.savedStateHandle.getStateFlow("movement_saved", false).collectAsStateWithLifecycle()
        val openRegisterMovement by historyEntry.savedStateHandle.getStateFlow("open_register_movement", false).collectAsStateWithLifecycle()
        MovementHistoryRoute(
            recurrenceViewModel = hiltViewModel<RecurrencePresentationViewModel>(),
            savedMessage = saved,
            onSavedMessageConsumed = { historyEntry.savedStateHandle["movement_saved"] = false },
            onNavigateToSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
            onNavigateToNewAccount = { navController.navigateToAccountForm() },
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
            onToggleMasked = hiltViewModel<SettingsViewModel>()::toggleHideBalances,
            openRegisterMovement = openRegisterMovement && (movementsSelected?.value != false),
            onConsumeRegisterMovement = { historyEntry.savedStateHandle["open_register_movement"] = false },
            prewarmQuickMovement = true,
        )
}
