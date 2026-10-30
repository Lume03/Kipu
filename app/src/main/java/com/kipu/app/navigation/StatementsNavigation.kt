package com.kipu.app.navigation

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kipu.app.feature.statements.presentation.ImportMovementsScreen
import com.kipu.app.feature.statements.presentation.StatementReviewScreen
import com.kipu.app.feature.statements.presentation.StatementReviewViewModel

const val STATEMENT_REVIEW_ROUTE = "statements/review/{importId}"
const val IMPORT_HUB_ROUTE = "import"

fun NavController.navigateToStatementReview(importId: String) {
    navigate("statements/review/${Uri.encode(importId)}") { launchSingleTop = true }
}

fun NavGraphBuilder.statementDestinations(navController: NavController) {
    composable(IMPORT_HUB_ROUTE) {
        ImportMovementsScreen(onBack = { navController.popBackStack() }, onYape = { navController.navigate(RECEIPTS_ROUTE) },
            onReview = navController::navigateToStatementReview, onPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) })
    }
    composable(STATEMENT_REVIEW_ROUTE, arguments = listOf(navArgument("importId") { type = NavType.StringType })) {
        val viewModel: StatementReviewViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val back: () -> Unit = { if (!navController.popBackStack()) navController.navigate(IMPORT_HUB_ROUTE) }
        LaunchedEffect(state.discarded) { if (state.discarded) back() }
        StatementReviewScreen(state, onBack = back, onCard = viewModel::setCard, onSelect = viewModel::selectLine,
            onSelectAll = viewModel::setAllSelected, onEdit = viewModel::editLine, onBatchCategory = viewModel::assignCategory,
            onConfirm = viewModel::confirm, onDiscard = viewModel::discard, onDismissMessage = viewModel::dismissMessage,
            onPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
            onViewMovements = { navController.navigate(MOVEMENTS_HISTORY_ROUTE) })
    }
}
