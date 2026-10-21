package com.kipu.app.feature.goals.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.usecase.GoalTransferIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface GoalContributionEvent {
    data object Saved : GoalContributionEvent
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GoalContributionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    private val goalId = savedStateHandle.get<String>(GoalDetailViewModel.ARG_GOAL_ID).orEmpty()
    private val _state = MutableStateFlow(GoalContributionUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<GoalContributionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess
                .map { (it as? LocalAccess.Available)?.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null || goalId.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(GoalContributionUiState(userId = userId, isLoading = false))
                    } else {
                        combine(repository.observeGoal(userId, goalId), repository.observeBackingAccounts(userId)) { goal, accounts ->
                            GoalContributionUiState(userId, goal, accounts, isLoading = false)
                        }
                    }
                }
                .catch { _state.value = _state.value.copy(isLoading = false, errorMessage = "No se pudieron cargar las cuentas.") }
                .collectLatest { fresh ->
                    _state.value = fresh.copy(isSaving = _state.value.isSaving, errorMessage = _state.value.errorMessage)
                }
        }
    }

    fun reserve(
        amount: Money,
        backingAccountId: String,
        confirmTransfer: Boolean,
        sourceAccountId: String?,
    ) {
        val current = _state.value
        val userId = current.userId
        val goal = current.goal
        if (userId == null || goal == null || current.isSaving) return
        val destination = current.accounts.firstOrNull { it.id == backingAccountId }
        if (destination == null || destination.currency != amount.currency) {
            _state.value = current.copy(errorMessage = "Elige una cuenta compatible con la moneda de la meta.")
            return
        }
        val transfer = if (confirmTransfer) {
            val source = current.accounts.firstOrNull { it.id == sourceAccountId }
            if (source == null || source.currency != amount.currency) {
                _state.value = current.copy(errorMessage = "Elige una cuenta origen de la misma moneda.")
                return
            }
            if (source.available.minorUnits < amount.minorUnits) {
                _state.value = current.copy(errorMessage = "La cuenta origen no tiene saldo libre suficiente.")
                return
            }
            GoalTransferIntent(source.id, destination.id, amount.currency)
        } else {
            null
        }
        if (transfer == null && destination.available.minorUnits < amount.minorUnits) {
            _state.value = current.copy(errorMessage = "Saldo libre insuficiente. El monto completo fue rechazado.")
            return
        }
        _state.value = current.copy(isSaving = true, errorMessage = null, message = null)
        viewModelScope.launch {
            val result = runCatching {
                repository.reserveGoalFunds(userId, goal.id, amount, destination.id, transfer)
            }.getOrElse {
                _state.value = _state.value.copy(isSaving = false, errorMessage = "No se pudo guardar. Intenta nuevamente.")
                return@launch
            }
            when (result) {
                is GoalCommandResult.Applied, is GoalCommandResult.Duplicate -> {
                    _state.value = _state.value.copy(isSaving = false, message = "Reserva guardada.", errorMessage = null)
                    _events.send(GoalContributionEvent.Saved)
                }
                is GoalCommandResult.Conflict -> _state.value = _state.value.copy(
                    isSaving = false, errorMessage = "La meta o la cuenta cambió. Revisa los saldos antes de reintentar.",
                )
                is GoalCommandResult.Rejected -> _state.value = _state.value.copy(isSaving = false, errorMessage = result.message())
                is GoalCommandResult.Retryable -> _state.value = _state.value.copy(
                    isSaving = false, message = "Reserva guardada. Puedes seguir usando tus metas.", errorMessage = null,
                )
            }
        }
    }

    private fun GoalCommandResult.Rejected.message(): String = when (code) {
        "INSUFFICIENT_BACKING" -> "Saldo libre insuficiente. Se rechazó el importe completo."
        "INSUFFICIENT_TRANSFER_SOURCE" -> "La cuenta origen no tiene saldo libre suficiente."
        "OWNER_OR_ACCOUNT_INVALID" -> "La cuenta ya no está disponible. Actualiza e inténtalo de nuevo."
        "GOAL_NOT_ACTIVE" -> "Solo puedes aportar a una meta activa."
        else -> "No se pudo registrar la reserva ($code)."
    }

    companion object { const val ARG_GOAL_ID = GoalDetailViewModel.ARG_GOAL_ID }
}
