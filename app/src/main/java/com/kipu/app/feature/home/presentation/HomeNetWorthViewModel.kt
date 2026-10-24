package com.kipu.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.home.domain.CurrencyNetWorth
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.feature.home.domain.ObserveNetWorthSummary
import dagger.hilt.android.lifecycle.HiltViewModel
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

/** Estados explícitos del resumen patrimonial de Inicio. */
sealed interface NetWorthUiState {
    data object Loading : NetWorthUiState

    /** Sin cuentas, tarjetas ni deudas: patrimonio desconocido, no cero. */
    data object Empty : NetWorthUiState

    /** Un patrimonio por moneda; sin conversión. */
    data class Content(val byCurrency: List<CurrencyNetWorth>) : NetWorthUiState

    data class Error(val message: String) : NetWorthUiState
}

/** Solo presenta: el cálculo autoritativo vive en [ObserveNetWorthSummary]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeNetWorthViewModel @Inject constructor(
    private val observeNetWorthSummary: ObserveNetWorthSummary,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<NetWorthUiState> = combine(sessionCoordinator.localAccess, retryTick) { access, _ -> access }
        .flatMapLatest { access -> stateFlowFor(access) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetWorthUiState.Loading)

    fun retry() {
        retryTick.value += 1
    }

    private fun stateFlowFor(access: LocalAccess): Flow<NetWorthUiState> = when (access) {
        is LocalAccess.Available -> observeNetWorthSummary(access.userId)
            .map { summary ->
                when (summary) {
                    NetWorthSummary.NoData -> NetWorthUiState.Empty
                    is NetWorthSummary.Available -> NetWorthUiState.Content(summary.byCurrency)
                }
            }
            .onStart<NetWorthUiState> { emit(NetWorthUiState.Loading) }
            .catch { emit(NetWorthUiState.Error("No se pudo calcular tu patrimonio.")) }
        is LocalAccess.Protected -> flowOf(NetWorthUiState.Loading)
        LocalAccess.NoOwner -> flowOf(NetWorthUiState.Error("Inicia sesión para ver tu patrimonio."))
    }
}
