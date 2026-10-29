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
import androidx.navigation.navDeepLink
import com.kipu.app.feature.capture.presentation.CAPTURE_CANDIDATE_ID_ARG
import com.kipu.app.feature.capture.presentation.CaptureInboxScreen
import com.kipu.app.feature.capture.presentation.CaptureInboxViewModel
import com.kipu.app.feature.capture.presentation.CaptureReviewEvent
import com.kipu.app.feature.capture.presentation.CaptureReviewScreen
import com.kipu.app.feature.capture.presentation.CaptureReviewViewModel

const val CAPTURE_INBOX_ROUTE = "capture/inbox"
const val CAPTURE_CANDIDATE_ROUTE = "capture/candidate/{$CAPTURE_CANDIDATE_ID_ARG}"

/** Opened by the HU-43 review notification and the notification center. */
const val CAPTURE_CANDIDATE_DEEP_LINK = "kipu://capture/candidate/{$CAPTURE_CANDIDATE_ID_ARG}"
private const val CAPTURE_MESSAGE_KEY = "capture_inbox_message"

fun NavController.navigateToCaptureInbox() {
    navigate(CAPTURE_INBOX_ROUTE) { launchSingleTop = true }
}

fun NavController.navigateToCaptureCandidate(candidateId: String) {
    navigate("capture/candidate/${Uri.encode(candidateId)}") { launchSingleTop = true }
}

fun NavGraphBuilder.captureDestinations(navController: NavController) {
    composable(CAPTURE_INBOX_ROUTE) { entry ->
        val viewModel: CaptureInboxViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val message by entry.savedStateHandle.getStateFlow<String?>(CAPTURE_MESSAGE_KEY, null).collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                viewModel.showMessage(it)
                entry.savedStateHandle[CAPTURE_MESSAGE_KEY] = null
            }
        }
        // Returning from the permissions screen may have changed consent/permission.
        LaunchedEffect(entry) { viewModel.refreshAutomation() }
        CaptureInboxScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onOpenCandidate = navController::navigateToCaptureCandidate,
            onDiscard = viewModel::discard,
            onOpenPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
            onDismissMessage = viewModel::dismissMessage,
        )
    }
    composable(
        route = CAPTURE_CANDIDATE_ROUTE,
        arguments = listOf(navArgument(CAPTURE_CANDIDATE_ID_ARG) { type = NavType.StringType }),
        deepLinks = listOf(navDeepLink { uriPattern = CAPTURE_CANDIDATE_DEEP_LINK }),
    ) {
        val viewModel: CaptureReviewViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val leave: (String?) -> Unit = { message ->
            // The inbox shows the outcome; a cold deep link has no inbox below, so open it.
            navController.previousBackStackEntry?.savedStateHandle?.set(CAPTURE_MESSAGE_KEY, message)
            if (!navController.popBackStack()) {
                navController.navigate(CAPTURE_INBOX_ROUTE) {
                    popUpTo(CAPTURE_CANDIDATE_ROUTE) { inclusive = true }
                }
            }
        }
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is CaptureReviewEvent.Registered -> leave(event.message)
                    is CaptureReviewEvent.Discarded -> leave(event.message)
                }
            }
        }
        CaptureReviewScreen(
            state = state,
            onChooseExpected = viewModel::chooseExpected,
            onBack = { if (!navController.popBackStack()) navController.navigateToCaptureInbox() },
            onDirection = viewModel::onDirectionSelected,
            onAmount = viewModel::onAmountChanged,
            onCurrency = viewModel::onCurrencySelected,
            onDate = viewModel::onDateSelected,
            onMerchant = viewModel::onMerchantChanged,
            onCategory = viewModel::onCategorySelected,
            onInstrument = viewModel::onInstrumentSelected,
            onCreditMode = viewModel::onCreditModeSelected,
            onInstallments = viewModel::onInstallmentsChanged,
            onConfirm = viewModel::confirm,
            onConfirmDespiteSimilar = viewModel::confirmDespiteSimilar,
            onDismissSimilar = viewModel::dismissSimilar,
            onConvertToManual = viewModel::convertToManual,
            onDiscard = viewModel::discard,
            onOpenPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
        )
    }
}
