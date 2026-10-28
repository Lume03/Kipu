package com.kipu.app.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kipu.app.feature.goals.presentation.GoalContributionEvent
import com.kipu.app.feature.goals.presentation.GoalContributionSheet
import com.kipu.app.feature.goals.presentation.GoalContributionViewModel
import com.kipu.app.feature.goals.presentation.GoalDetailScreen
import com.kipu.app.feature.goals.presentation.GoalDetailViewModel
import com.kipu.app.feature.goals.presentation.GoalFormScreen
import com.kipu.app.feature.goals.presentation.GoalListEvent
import com.kipu.app.feature.goals.presentation.GoalListViewModel

const val GOAL_CREATE_ROUTE = "goals/new"
const val GOAL_DETAIL_ROUTE = "goals/{goalId}?withdraw={withdraw}&action={action}"
const val GOAL_EDIT_ROUTE = "goals/{goalId}/edit"
const val GOAL_CONTRIBUTION_ROUTE = "goals/{goalId}/contribute"

fun NavController.navigateToGoals() = navigateToPlanning(PlanningTab.GOALS)

fun NavController.navigateToGoal(goalId: String) = navigate("goals/$goalId")

fun NavGraphBuilder.goalDestinations(navController: NavController) {
    composable(GOAL_CREATE_ROUTE) { backStackEntry ->
        val ownerEntry = remember(backStackEntry) { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }
        val viewModel: GoalListViewModel = hiltViewModel(ownerEntry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event == GoalListEvent.Saved) navController.popBackStack()
            }
        }
        GoalFormScreen(
            existingGoal = null,
            accounts = state.backingAccounts,
                accountOptions = state.accountOptions,
            isSaving = state.isSaving,
            errorMessage = state.errorMessage,
            onNavigateBack = { navController.popBackStack() },
            onSave = { _, name, target, date, accountId, _, icon -> viewModel.saveGoal(null, name, target, date, accountId, iconKey = icon) },
        )
    }

    composable(
        route = GOAL_EDIT_ROUTE,
        arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val ownerEntry = remember(backStackEntry) { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }
        val viewModel: GoalListViewModel = hiltViewModel(ownerEntry)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val goalId = backStackEntry.arguments?.getString("goalId")
        val goal = state.goals.firstOrNull { it.goal.id == goalId }?.goal
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event == GoalListEvent.Saved) navController.popBackStack()
            }
        }
        if (goal == null && state.isLoading) {
            androidx.compose.material3.CircularProgressIndicator()
        } else if (goal == null) {
            androidx.compose.material3.Text("No encontramos esta meta.")
        } else {
            GoalFormScreen(
                existingGoal = goal,
                accounts = state.backingAccounts,
                accountOptions = state.accountOptions,
                currentReserved = state.goals.firstOrNull { it.goal.id == goal.id }?.progress?.reserved,
                hasPremiumAccess = state.hasPremiumAccess,
                replacementCandidates = state.goals.map { it.goal }.filter { it.status == com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE },
                isSaving = state.isSaving,
                errorMessage = state.errorMessage,
                onNavigateBack = { navController.popBackStack() },
                onSave = { id, name, target, date, accountId, replacementId, icon -> viewModel.saveGoal(id, name, target, date, accountId, replacementId, icon) },
            )
        }
    }

    composable(
        route = GOAL_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("goalId") { type = NavType.StringType },
            navArgument("withdraw") { type = NavType.BoolType; defaultValue = false },
            navArgument("action") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val viewModel: GoalDetailViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val fundsViewModel: GoalContributionViewModel = hiltViewModel()
        LaunchedEffect(state.isDeleted) { if (state.isDeleted) navController.popBackStack() }
        val fundsState by fundsViewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(fundsViewModel) { fundsViewModel.events.collect { if (it == GoalContributionEvent.Saved) navController.popBackStack() } }
        GoalDetailScreen(
            state = state,
            onNavigateBack = { navController.popBackStack() },
            onContribute = { state.goal?.id?.let { navController.navigate("goals/$it/contribute") } },
            onRelease = viewModel::release,
            onReserve = fundsViewModel::reserve,
            fundsState = fundsState,
            onReassign = viewModel::reassign,
            onArchive = viewModel::archive,
            onComplete = viewModel::complete,
            onDelete = viewModel::delete,
            onDeleteWithReplacement = { viewModel.delete(it) },
            onPause = viewModel::pause,
            onEdit = { state.goal?.id?.let { navController.navigate("goals/$it/edit") } },
            startWithWithdrawal = backStackEntry.arguments?.getBoolean("withdraw") == true,
            initialAction = backStackEntry.arguments?.getString("action"),
            onCreateGoal = { navController.navigate(GOAL_CREATE_ROUTE) },
        )
    }

    composable(
        route = GOAL_CONTRIBUTION_ROUTE,
        arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val viewModel: GoalContributionViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) {
            // The funds screen displays the saved result before returning to the goal.
            viewModel.events.collect { }
        }
        val detailViewModel: GoalDetailViewModel = hiltViewModel()
        val withdrawalState by detailViewModel.state.collectAsStateWithLifecycle()
        GoalContributionSheet(
            state = state,
            onNavigateBack = { navController.popBackStack() },
            onReserve = viewModel::reserve,
            withdrawalState = withdrawalState,
            onRelease = detailViewModel::release,
            onReleaseAchieved = { state.goal?.id?.let { navController.navigate("goals/$it?withdraw=true") {
                popUpTo(GOAL_CONTRIBUTION_ROUTE) { inclusive = true }
            } } },
            onCreateGoal = { navController.navigate(GOAL_CREATE_ROUTE) },
        )
    }
}
