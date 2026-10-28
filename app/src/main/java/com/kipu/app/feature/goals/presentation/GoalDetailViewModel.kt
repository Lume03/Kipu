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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val featureAccessCacheDao: com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao,
    private val entitlementEvaluator: com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator,
    private val displayPreferences: com.kipu.app.feature.goals.data.GoalDisplayPreferences? = null,
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
                                isPaused = displayPreferences?.snapshot(userId)?.paused?.contains(goalId) == true,
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
                        isDeleted = _state.value.isDeleted,
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

    fun pause(paused: Boolean) {
        val current = _state.value
        val owner = current.userId ?: return
        val goal = current.goal ?: return
        if (current.isSaving) return
        viewModelScope.launch {
            runCatching { displayPreferences?.pause(owner, goal.id, paused) }
                .onSuccess { _state.value = _state.value.copy(isPaused = paused, message = if (paused) "Meta pausada. Tu reserva se conserva." else "Meta reanudada.") }
                .onFailure { _state.value = _state.value.copy(errorMessage = "No se pudo guardar la pausa. Inténtalo de nuevo.") }
        }
    }

    fun complete() {
        val current = _state.value
        val reserved = current.progress?.reserved ?: return
        if (current.goal?.status != com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE) return
        if (reserved.minorUnits <= 0L) {
            _state.value = current.copy(errorMessage = "Realiza un aporte antes de marcar la meta como lograda.")
            return
        }
        mutate { userId, goal ->
            repository.updateGoal(userId, goal.id, com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand(
                expectedRevision = goal.revision,
                name = goal.name,
                target = reserved,
                targetDate = goal.targetDate,
                linkedAccountId = goal.linkedAccountId,
                iconKey = goal.iconKey,
                colorArgb = goal.colorArgb,
                hasPremiumAccess = current.hasPremiumAccess,
            ))
        }
    }

    fun delete() = delete(null)

    fun delete(replacementGoalId: String?) = mutate(deleting = true) { userId, goal ->
        val preferences = displayPreferences
            ?: return@mutate repository.deleteEmptyGoal(userId, goal.id, goal.revision)
        val current = _state.value
        val reserves = current.events.filterNot { it.transactionVoided }.groupBy { it.backingAccountId }
            .mapValues { (_, events) -> events.fold(0L) { sum, event -> Math.addExact(sum, event.delta.minorUnits) } }
        if ((reserves[null] ?: 0L) > 0L) return@mutate GoalCommandResult.Rejected("BACKING_RECONCILIATION_REQUIRED")
        for ((backing, amount) in reserves) {
            if (backing == null || amount <= 0L) continue
            val replacement = replacementGoalId?.let { id -> repository.observeGoal(userId, id).first() }
            val result = repository.releaseGoalReserve(userId, goal.id, backing, Money(amount, goal.currency),
                replacementGoalId = replacement?.id, replacementExpectedRevision = replacement?.revision,
                hasPremiumAccess = current.hasPremiumAccess)
            if (result !is GoalCommandResult.Applied && result !is GoalCommandResult.Duplicate) return@mutate result
        }
        val fresh = repository.observeGoal(userId, goal.id).first() ?: return@mutate GoalCommandResult.Rejected("GOAL_NOT_FOUND")
        if (fresh.status != com.kipu.app.feature.goals.domain.model.GoalStatus.ARCHIVED) {
            val result = repository.archiveGoal(userId, goal.id, fresh.revision)
            if (result !is GoalCommandResult.Applied && result !is GoalCommandResult.Duplicate) return@mutate result
        }
        preferences.remove(userId, goal.id)
        GoalCommandResult.Applied(goal.id, fresh.revision)
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(errorMessage = null, message = null)
    }

    private fun mutate(deleting: Boolean = false, command: suspend (String, com.kipu.app.feature.goals.domain.model.Goal) -> GoalCommandResult) {
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
                _state.value = _state.value.copy(isSaving = false, errorMessage = "No se pudo completar la operación. Revisa el historial y vuelve a intentarlo.")
                return@launch
            }
            when (result) {
                is GoalCommandResult.Applied, is GoalCommandResult.Duplicate -> _state.value = _state.value.copy(
                    isSaving = false,
                    message = "Cambios guardados.",
                    errorMessage = null,
                    isDeleted = deleting,
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
        "HISTORY_PRESERVED", "GOAL_NOT_EMPTY" -> "Esta meta tiene historial u operaciones pendientes. Usa Mejor archivarla para conservarlos."
        "BACKING_RECONCILIATION_REQUIRED" -> "Hay una reserva antigua sin cuenta identificada. Revisa el respaldo antes de eliminar esta meta."
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
