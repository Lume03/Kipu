package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDeleteResult
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.VoidTransaction
import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import com.kipu.app.feature.movements.domain.model.MovementMutationResult
import kotlinx.coroutines.flow.update

data class DebtDetailUiState(
    val debt: DebtSummary? = null,
    val hasFinancialHistory: Boolean = false,
    val activities: List<DebtSettlementActivity> = emptyList(),
    val closure: DebtClosureSummary = DebtClosureSummary(),
    val installments: List<DebtScheduleItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedPayment: DebtSettlementActivity? = null,
    val isVoidingPayment: Boolean = false,
    val voidError: String? = null,
    val errorMessage: String? = null,
)

data class DebtClosureSummary(
    val paidMinor: Long = 0,
    val forgivenMinor: Long = 0,
    val adjustedMinor: Long = 0,
    val closedAt: Long? = null,
)

internal fun summarizeClosure(activities: List<DebtSettlementActivity>): DebtClosureSummary {
    val effective = activities.filterNot { it.isVoided }
    return DebtClosureSummary(
        paidMinor = effective.filter { it.eventType == "PAYMENT" }.sumOf { it.principalMinor },
        forgivenMinor = effective.filter { it.eventType == "FORGIVENESS" }.sumOf { it.principalMinor },
        adjustedMinor = effective.filter { it.eventType == "ADJUSTMENT" }.sumOf { it.principalDeltaMinor ?: 0L },
        closedAt = effective.maxOfOrNull { it.occurredAt },
    )
}

sealed interface DebtDetailUiEvent {
    data object Deleted : DebtDetailUiEvent
}

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DebtRepository,
    debtDao: DebtDao,
    private val sessionCoordinator: SessionCoordinator,
    private val maintenanceRepository: MovementMaintenanceRepository,
    private val voidTransaction: VoidTransaction,
) : ViewModel() {
    val debtId: String = savedStateHandle.get<String>("debtId").orEmpty()
    private val _state = MutableStateFlow(DebtDetailUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<DebtDetailUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        if (userId == null || debtId.isBlank()) {
            _state.value = DebtDetailUiState(isLoading = false, errorMessage = "No se encontró esta deuda.")
        } else {
            viewModelScope.launch {
                combine(
                    repository.observeDebt(userId, debtId),
                    debtDao.observeHasFinancialHistory(userId, debtId),
                    debtDao.observeSettlementActivities(userId, debtId),
                    repository.observeInstallments(userId, debtId),
                ) { debt, hasHistory, activities, installments ->
                    val mappedActivities = activities.map { row ->
                        DebtSettlementActivity(
                            eventId = row.eventId, principalMinor = row.principalMinor, interestMinor = row.interestMinor,
                            occurredAt = row.occurredAt, isVoided = row.isVoided, eventType = row.eventType,
                            principalDeltaMinor = row.principalDeltaMinor, transactionId = row.transactionId, syncStatus = row.syncStatus,
                        )
                    }
                    DebtDetailUiState(
                        debt = debt,
                        hasFinancialHistory = hasHistory,
                        activities = mappedActivities,
                        closure = summarizeClosure(mappedActivities),
                        installments = installments,
                        isLoading = false,
                    )
                }
                    .catch { _state.value = DebtDetailUiState(isLoading = false, errorMessage = "No se pudo cargar el detalle.") }
                    .collect { loaded -> _state.update { loaded.copy(
                        selectedPayment = it.selectedPayment, isVoidingPayment = it.isVoidingPayment,
                        voidError = it.voidError, errorMessage = it.errorMessage,
                    ) } }
            }
        }
    }

    fun selectPaymentToVoid(activity: DebtSettlementActivity) {
        if (_state.value.isVoidingPayment || activity.isVoided || activity.eventType != "PAYMENT" || activity.transactionId == null) return
        _state.update { it.copy(selectedPayment = activity, voidError = null) }
    }

    fun dismissPaymentVoid() {
        if (!_state.value.isVoidingPayment) _state.update { it.copy(selectedPayment = null, voidError = null) }
    }

    fun confirmPaymentVoid() {
        if (_state.value.isVoidingPayment) return
        val payment = _state.value.selectedPayment ?: return
        val transactionId = payment.transactionId ?: return
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        _state.update { it.copy(isVoidingPayment = true, voidError = null) }
        viewModelScope.launch {
            try {
                val head = maintenanceRepository.getRevisionHead(userId, transactionId)
                    ?: error("No se encontró el pago. Actualiza el historial.")
                val result = voidTransaction(userId, MovementRevisionCommand.Void(
                    idempotencyKey = UUID.randomUUID().toString(), transactionId = transactionId,
                    expectedRevision = head.commandBaseRevision, reason = "Anular pago desde el detalle de deuda",
                ))
                if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId != userId) return@launch
                when (result) {
                    is MovementMutationResult.Success -> _state.update { it.copy(selectedPayment = null, isVoidingPayment = false) }
                    is MovementMutationResult.Conflict -> _state.update { it.copy(isVoidingPayment = false,
                        voidError = "El pago cambió en otro dispositivo. Revisa el historial y reintenta.") }
                    is MovementMutationResult.Rejected -> _state.update { it.copy(isVoidingPayment = false,
                        voidError = "No se pudo anular el pago (${result.code}).") }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId == userId) {
                    _state.update { it.copy(isVoidingPayment = false, voidError = error.message ?: "No se pudo anular el pago.") }
                }
            }
        }
    }

    fun delete() {
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        viewModelScope.launch {
            val operationId = UUID.randomUUID().toString()
            val identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256("delete:$userId:$debtId"))
            when (val result = repository.deleteDebtIfUnreferenced(userId, debtId, identity)) {
                is DebtDeleteResult.Deleted -> _events.send(DebtDetailUiEvent.Deleted)
                is DebtDeleteResult.Rejected -> _state.value = _state.value.copy(
                    errorMessage = if (result.code == "HISTORY_PRESERVED") {
                        "El saldo pendiente y los movimientos se conservan como parte del historial."
                    } else "No se pudo eliminar la deuda (${result.code}).",
                )
                is DebtDeleteResult.Conflict -> _state.value = _state.value.copy(
                    errorMessage = "La deuda cambió en otro dispositivo. Actualiza antes de eliminarla.",
                )
            }
        }
    }
}
