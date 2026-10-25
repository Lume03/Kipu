package com.kipu.app.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kipu.app.feature.budgets.presentation.BudgetDetailEvent
import com.kipu.app.feature.budgets.presentation.BudgetDetailScreen
import com.kipu.app.feature.budgets.presentation.BudgetDetailViewModel
import com.kipu.app.feature.budgets.presentation.BudgetFormEvent
import com.kipu.app.feature.budgets.presentation.BudgetFormScreen
import com.kipu.app.feature.budgets.presentation.BudgetFormViewModel

const val BUDGET_CREATE_ROUTE = "budgets/new"

/** Detalle por id. Lo abre tambien el centro de avisos (aviso BUDGET_ALERT, referencia BUDGET). */
const val BUDGET_DETAIL_ROUTE = "budgets/{budgetId}"
const val BUDGET_EDIT_ROUTE = "budgets/{budgetId}/edit"

/** Budgets live in the Planning tab: there is no standalone list route any more. */
fun NavController.navigateToBudgets() = navigateToPlanning(PlanningTab.BUDGETS)

fun NavController.navigateToBudget(budgetId: String) = navigate("budgets/$budgetId")

fun NavGraphBuilder.budgetDestinations(navController: NavController) {
    composable(BUDGET_CREATE_ROUTE) {
        BudgetFormDestination(navController)
    }

    composable(
        route = BUDGET_EDIT_ROUTE,
        arguments = listOf(navArgument("budgetId") { type = NavType.StringType }),
    ) {
        BudgetFormDestination(navController)
    }

    composable(
        route = BUDGET_DETAIL_ROUTE,
        arguments = listOf(navArgument("budgetId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val viewModel: BudgetDetailViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    BudgetDetailEvent.Deleted -> navController.popBackStack()
                    BudgetDetailEvent.Finished -> Unit
                }
            }
        }
        val budgetId = backStackEntry.arguments?.getString("budgetId").orEmpty()
        BudgetDetailScreen(
            state = state,
            onNavigateBack = { navController.popBackStack() },
            onEdit = { navController.navigate("budgets/$budgetId/edit") },
            onFinish = viewModel::finish,
            onDelete = viewModel::delete,
            onRetry = viewModel::reload,
        )
    }
}

@androidx.compose.runtime.Composable
private fun BudgetFormDestination(navController: NavController) {
    val viewModel: BudgetFormViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is BudgetFormEvent.Saved) navController.popBackStack()
        }
    }
    BudgetFormScreen(
        state = state,
        onNavigateBack = { navController.popBackStack() },
        onChange = viewModel::update,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onDismissQuota = viewModel::dismissQuotaDialog,
        onSeePlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
    )
}
