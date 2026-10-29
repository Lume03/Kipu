package com.kipu.app.feature.accounts.presentation

import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal fun FinancialDashboardData.hasLocalData(): Boolean =
    hasStoredInstruments || liquidAccounts.isNotEmpty() || creditCards.isNotEmpty() || activeComputableCount > 0

/** Room is observed immediately; only a genuinely empty first-device snapshot waits for hydration. */
internal fun dashboardEntryStates(
    local: Flow<FinancialDashboardData>,
    masked: Flow<Boolean>,
    hydrate: suspend () -> Unit,
): Flow<DashboardUiState> = channelFlow {
    val settled = MutableStateFlow(false)
    val syncError = MutableStateFlow<String?>(null)
    launch {
        try {
            hydrate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            syncError.value = error.message ?: "No se pudo sincronizar tu dinero. Intenta nuevamente."
        } finally {
            settled.value = true
        }
    }
    combine(local, masked, settled, syncError) { data, hide, finished, error ->
        val available = data.hasLocalData()
        DashboardUiState(
            isLoading = !available && !finished,
            dashboardData = data.takeIf { available || (finished && error == null) },
            isMasked = hide,
            errorMessage = error,
        )
    }.collect { send(it) }
}
