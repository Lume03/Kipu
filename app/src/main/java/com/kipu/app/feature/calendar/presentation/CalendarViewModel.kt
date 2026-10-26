package com.kipu.app.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.calendar.domain.CalendarMonth
import com.kipu.app.feature.calendar.domain.ObserveCalendarMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal const val CALENDAR_LOAD_ERROR_MESSAGE = "Tus eventos siguen guardados. Puedes cambiar de mes o reintentar."

/** Estados explícitos de la pantalla de calendario. */
sealed interface CalendarContent {
    data object Loading : CalendarContent
    data object Empty : CalendarContent
    data class Loaded(val month: CalendarMonth) : CalendarContent
    data class Error(val message: String) : CalendarContent
}

data class CalendarUiState(
    val month: YearMonth,
    val today: LocalDate,
    val content: CalendarContent = CalendarContent.Loading,
)

/** Solo presenta y navega entre meses; no hay límite hacia el pasado (Free incluido). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeCalendarMonth: ObserveCalendarMonth,
    sessionCoordinator: SessionCoordinator,
    clock: Clock,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(clock.withZone(zone))
    private val selectedMonth = MutableStateFlow(YearMonth.from(today))
    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<CalendarUiState> = combine(
        sessionCoordinator.localAccess,
        selectedMonth,
        retryTick,
    ) { access, month, _ -> access to month }
        .flatMapLatest { (access, month) ->
            contentFor(access, month).map { CalendarUiState(month, today, it) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(selectedMonth.value, today),
        )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun goToToday() {
        selectedMonth.value = YearMonth.from(today)
    }

    fun retry() {
        retryTick.value += 1
    }

    private fun contentFor(access: LocalAccess, month: YearMonth): Flow<CalendarContent> = when (access) {
        is LocalAccess.Available -> observeCalendarMonth(access.userId, month, zone)
            .map<CalendarMonth, CalendarContent> { if (it.isEmpty) CalendarContent.Empty else CalendarContent.Loaded(it) }
            .onStart { emit(CalendarContent.Loading) }
            .catch { emit(CalendarContent.Error(CALENDAR_LOAD_ERROR_MESSAGE)) }
        is LocalAccess.Protected -> flowOf(CalendarContent.Loading)
        LocalAccess.NoOwner -> flowOf(CalendarContent.Error("Inicia sesión para ver tu calendario."))
    }
}
