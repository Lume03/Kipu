package com.kipu.app.navigation

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventSource
import com.kipu.app.feature.calendar.presentation.CalendarScreen
import com.kipu.app.feature.calendar.presentation.CalendarViewModel
import com.kipu.app.feature.recurrences.presentation.RecurrenceLifecycleScreen
import com.kipu.app.feature.recurrences.presentation.RecurrencePresentationViewModel

const val CALENDAR_ROUTE = "calendar"

fun NavController.navigateToCalendar() {
    navigate(CALENDAR_ROUTE) { launchSingleTop = true }
}

/**
 * Cada evento abre su objeto fuente con rutas existentes. Un evento no disponible no navega.
 */
internal fun NavController.navigateToCalendarEventSource(event: CalendarEvent) {
    if (!event.isSourceAvailable) return
    when (val source = event.source) {
        is CalendarEventSource.Movement -> navigateToMovementEditor(source.transactionId)
        is CalendarEventSource.Card -> navigateToCardDetail(source.cardId)
        is CalendarEventSource.Debt -> navigateToDebtDetail(source.debtId)
        is CalendarEventSource.Recurrence -> navigate("calendar/recurrence/${Uri.encode(source.recurrenceId)}")
    }
}

fun NavGraphBuilder.calendarDestinations(navController: NavController) {
    calendarRecurrenceDestination(navController)
    composable(CALENDAR_ROUTE) {
        val viewModel: CalendarViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        CalendarScreen(
            state = state,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onToday = viewModel::goToToday,
            onRetry = viewModel::retry,
            onEventClick = navController::navigateToCalendarEventSource,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

fun NavGraphBuilder.calendarRecurrenceDestination(
    navController: NavController,
    viewModelProvider: @androidx.compose.runtime.Composable () -> RecurrencePresentationViewModel = { hiltViewModel() },
) {
    composable(
        route = "calendar/recurrence/{recurrenceId}",
        arguments = listOf(navArgument("recurrenceId") { type = NavType.StringType }),
    ) { entry ->
        val recurrenceId = entry.arguments?.getString("recurrenceId").orEmpty()
        val viewModel = viewModelProvider()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val rule = state.recurrences.firstOrNull { it.id == recurrenceId }
        if (!state.isLoading && rule == null) {
            Column {
                Text("Objeto no disponible: el registro de origen ya no existe.")
                TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
            }
        } else {
            RecurrenceLifecycleScreen(
                recurrences = listOfNotNull(rule),
                expectedPayments = state.expectedPayments,
                isLoading = state.isLoading,
                errorMessage = state.errorMessage,
                onPause = viewModel::pause,
                onRequestResume = viewModel::requestResume,
                onCancel = viewModel::cancel,
                onConfirmResume = viewModel::confirmResume,
                onDismissConfirmation = viewModel::dismissConfirmation,
                resumeDate = state.resumePreview?.firstFutureDueDate,
                pendingOperation = state.isSaving,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
