package com.kipu.app.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventSource
import com.kipu.app.feature.calendar.presentation.CalendarScreen
import com.kipu.app.feature.calendar.presentation.CalendarViewModel

const val CALENDAR_ROUTE = "calendar"

fun NavController.navigateToCalendar() {
    navigate(CALENDAR_ROUTE) { launchSingleTop = true }
}

/**
 * Cada evento abre su objeto fuente con rutas existentes. Un evento no disponible no navega.
 * Las recurrencias no tienen ruta propia: se gestionan desde el historial de movimientos.
 */
internal fun NavController.navigateToCalendarEventSource(event: CalendarEvent) {
    if (!event.isSourceAvailable) return
    when (val source = event.source) {
        is CalendarEventSource.Movement -> navigateToMovementEditor(source.transactionId)
        is CalendarEventSource.Card -> navigateToCardDetail(source.cardId)
        is CalendarEventSource.Debt -> navigateToDebtDetail(source.debtId)
        is CalendarEventSource.Recurrence -> navigateToMovementHistory()
    }
}

fun NavGraphBuilder.calendarDestinations(navController: NavController) {
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
