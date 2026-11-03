package com.kipu.app.feature.analytics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.analytics.data.AnalyticsRepository
import com.kipu.app.feature.analytics.domain.*
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.feature.home.domain.ObserveNetWorthSummary
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

sealed interface AnalyticsRequest {
    data object CurrentMonth : AnalyticsRequest
    data class Report(val period: AnalyticsPeriod, val filters: AnalyticsFilters = AnalyticsFilters()) : AnalyticsRequest
    data class Compare(val previous: AnalyticsPeriod, val current: AnalyticsPeriod, val filters: AnalyticsFilters = AnalyticsFilters()) : AnalyticsRequest
}
sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState
    data object Locked : AnalyticsUiState
    data object NoOwner : AnalyticsUiState
    data class Content(val result: PeriodResult, val netWorth: NetWorthSummary) : AnalyticsUiState
    data class Comparison(val result: AnalyticsComparison, val netWorth: NetWorthSummary) : AnalyticsUiState
    /** The UI can show current statistics and navigate to the existing basic movement history. */
    data class PremiumRequired(val current: PeriodResult, val netWorth: NetWorthSummary, val basicHistoryAvailable: Boolean = true) : AnalyticsUiState
    data class Error(val message: String) : AnalyticsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val session: SessionCoordinator,
    private val plan: VerifiedPlanAccess,
    private val repository: AnalyticsRepository,
    private val netWorth: ObserveNetWorthSummary,
) : ViewModel() {
    private val request = MutableStateFlow<AnalyticsRequest>(AnalyticsRequest.CurrentMonth)
    private val retry = MutableStateFlow(0)
    val state: StateFlow<AnalyticsUiState> = combine(session.localAccess, request, retry) { access, selection, _ -> access to selection }
        .flatMapLatest { (access, selection) ->
            when (access) {
                LocalAccess.NoOwner -> flowOf(AnalyticsUiState.NoOwner)
                is LocalAccess.Protected -> flowOf(AnalyticsUiState.Locked)
                is LocalAccess.Available -> plan.observePremiumVerified().flatMapLatest { premium ->
                    load(access.userId, selection, premium)
                }.onStart { emit(AnalyticsUiState.Loading) }
                    .catch { emit(AnalyticsUiState.Error("No se pudieron cargar las estadísticas.")) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState.Loading)

    /** The request the UI is showing, so the selectors reflect it after rotation or tab switches. */
    val selection: StateFlow<AnalyticsRequest> = request.asStateFlow()

    /** Labels for filters and the category breakdown; an unavailable source just leaves the list empty. */
    val filterOptions: StateFlow<AnalyticsFilterOptions> = session.localAccess
        .flatMapLatest { access ->
            if (access is LocalAccess.Available) repository.observeFilterOptions(access.userId).catch { emit(AnalyticsFilterOptions()) }
            else flowOf(AnalyticsFilterOptions())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsFilterOptions())

    fun showCurrentMonth() { request.value = AnalyticsRequest.CurrentMonth }
    fun showReport(period: AnalyticsPeriod, filters: AnalyticsFilters = AnalyticsFilters()) { request.value = AnalyticsRequest.Report(period, filters) }
    fun compare(previous: AnalyticsPeriod, current: AnalyticsPeriod, filters: AnalyticsFilters = AnalyticsFilters()) { request.value = AnalyticsRequest.Compare(previous, current, filters) }
    fun retry() { retry.value++ }

    private fun load(owner: String, selection: AnalyticsRequest, premium: Boolean): Flow<AnalyticsUiState> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val basic = AnalyticsPeriod.currentMonth(today, zone)
        val requiresPremium = selection !is AnalyticsRequest.CurrentMonth
        if (requiresPremium && !premium) return combine(repository.observe(owner, basic), netWorth(owner)) { rows, worth ->
            AnalyticsUiState.PremiumRequired(AnalyticsCalculator.calculate(basic, today, AnalyticsFilters(), rows), worth)
        }
        fun report(period: AnalyticsPeriod, filters: AnalyticsFilters): Flow<PeriodResult> {
            require(period.zone == zone && !period.endInclusive.isAfter(today)) { "Periodo inválido" }
            return repository.observe(owner, period).map { AnalyticsCalculator.calculate(period, today, filters, it) }
        }
        return when (selection) {
            AnalyticsRequest.CurrentMonth -> combine(report(basic, AnalyticsFilters()), netWorth(owner)) { result, worth -> AnalyticsUiState.Content(result, worth) }
            is AnalyticsRequest.Report -> combine(report(selection.period, selection.filters), netWorth(owner)) { result, worth -> AnalyticsUiState.Content(result, worth) }
            is AnalyticsRequest.Compare -> combine(report(selection.previous, selection.filters), report(selection.current, selection.filters), netWorth(owner)) { previous, current, worth ->
                AnalyticsUiState.Comparison(AnalyticsCalculator.compare(previous, current), worth)
            }
        }
    }
}
