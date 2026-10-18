package com.kipu.app.feature.goals.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val featureAccessCacheDao: com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao,
    private val entitlementEvaluator: com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator,
) : ViewModel() {
    private val goalId = savedStateHandle.get<String>(ARG_GOAL_ID).orEmpty()
    private val _state = MutableStateFlow(GoalDetailUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess
                .map { (it as? LocalAccess.Available)?.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null || goalId.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(GoalDetailUiState(userId = userId, isLoading = false))
                    } else {
                        val hasPremiumAccess = hasVerifiedPremium(userId)
                        combine(
                            repository.observeGoal(userId, goalId),
                            repository.observeEvents(userId, goalId),
                            repository.observeBackingAccounts(userId),
                            repository.observeGoals(userId),
                        ) { goal, events, accounts, goals ->
                            GoalDetailUiState(
                                userId = userId,
                                goal = goal,
                                progress = goal?.let { GoalProgressCalculator.calculate(it, events) },
                                events = events,
                                backingAccounts = accounts,
                                otherGoals = goals.filter { it.id != goalId },
                                hasPremiumAccess = hasPremiumAccess,
                                isLoading = false,
                            )
                        }
                    }
                }
                .catch { _state.value = _state.value.copy(isLoading = false, errorMessage = "No se pudo cargar el detalle.") }
                .collectLatest { fresh ->
                    _state.value = fresh.copy(
                        isSaving = _state.value.isSaving,
                        message = _state.value.message,
                        errorMessage = _state.value.errorMessage,
                    )
                }
        }
    }

    fun release(backingAccountId: String, amount: Money, replacementGoalId: String? = null) = mutate { userId, goal ->
        val replacement = replacementGoalId?.let { id -> _state.value.otherGoals.firstOrNull { it.id == id } }
        repository.releaseGoalReserve(
            userId = userId,
            goalId = goal.id,
            backingAccountId = backingAccountId,
            amount = amount,
            replacementGoalId = replacement?.id,
            replacementExpectedRevision = replacement?.revision,
            hasPremiumAccess = _state.value.hasPremiumAccess,
        )
    }

    fun reassign(destinationGoalId: String, backingAccountId: String, amount: Money) = mutate { userId, goal ->
        repository.reassignGoalReserve(userId, goal.id, destinationGoalId, backingAccountId, amount)
    }

    fun archive() = mutate { userId, goal ->
        repository.archiveGoal(userId, goal.id, goal.revision)
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(errorMessage = null, message = null)
    }

    private fun mutate(command: suspend (String, com.kipu.app.feature.goals.domain.model.Goal) -> GoalCommandResult) {
        val current = _state.value
        val userId = current.userId
        if (userId == null) {
            _state.value = current.copy(errorMessage = "Inicia sesión para continuar.")
            return
        }
        val goal = current.goal ?: return
        if (current.isSaving) return
        _state.value = current.copy(isSaving = true, errorMessage = null, message = null)
        viewModelScope.launch {
            val result = runCatching { command(userId, goal) }.getOrElse {
                _state.value = _state.value.copy(isSaving = false, errorMessage = "No se pudo guardar. La reserva local se conserva.")
                return@launch
            }
            when (result) {
                is GoalCommandResult.Applied, is GoalCommandResult.Duplicate -> _state.value = _state.value.copy(
                    isSaving = false,
                    message = "Guardado en este dispositivo; sincronización pendiente.",
                    errorMessage = null,
                )
                is GoalCommandResult.Conflict -> _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = "La meta cambió en otro dispositivo. Actualiza el detalle (${result.code}).",
                )
                is GoalCommandResult.Rejected -> _state.value = _state.value.copy(isSaving = false, errorMessage = result.message())
                is GoalCommandResult.Retryable -> _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = "Guardado localmente; se volverá a sincronizar (${result.code}).",
                )
            }
        }
    }

    private fun GoalCommandResult.Rejected.message(): String = when (code) {
        "REPLACEMENT_REQUIRED", "GOAL_LIMIT" -> "Elige una meta activa para pausar y liberar un espacio del plan Free."
        "RESERVE_UNRESOLVED" -> "Libera o reasigna toda la reserva antes de archivar."
        "RESERVE_EXCEEDED" -> "El monto excede la reserva disponible."
        "INSUFFICIENT_BACKING" -> "La cuenta no tiene saldo libre suficiente. No se reservó una parte del monto."
        "BACKING_ACCOUNT_REQUIRED" -> "Elige una cuenta real que respalde la reserva."
        "GOAL_NOT_ACTIVE" -> "Solo puedes aportar a una meta activa."
        else -> "No se pudo completar la operación ($code)."
    }

    private suspend fun hasVerifiedPremium(userId: String): Boolean {
        val uuid = runCatching { java.util.UUID.fromString(userId) }.getOrNull() ?: return false
        val cache = featureAccessCacheDao.get(uuid) ?: return false
        if (!cache.effectiveTier.equals("PREMIUM", ignoreCase = true)) return false
        return entitlementEvaluator.evaluate(userId, cache) is
            com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision.Allowed
    }

    companion object {
        const val ARG_GOAL_ID = "goalId"
    }
}
