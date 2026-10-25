package com.kipu.app.feature.goals.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GoalListEvent {
    data object Saved : GoalListEvent
    data object Deleted : GoalListEvent
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GoalListViewModel @Inject constructor(
    private val repository: GoalRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val featureAccessCacheDao: FeatureAccessCacheDao,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator,
) : ViewModel() {
    private val _state = MutableStateFlow(GoalManagementUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<GoalListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess.collectLatest { access ->
                val userId = (access as? LocalAccess.Available)?.userId
                if (userId == null) {
                    _state.value = GoalManagementUiState(isLoading = false)
                    return@collectLatest
                }
                _state.value = GoalManagementUiState(userId = userId, isLoading = true)
                val premium = hasVerifiedPremium(userId)
                combine(repository.observeGoals(userId), repository.observeBackingAccounts(userId)) { goals, accounts ->
                    goals to accounts
                }.flatMapLatest { (goals, accounts) ->
                    if (goals.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(goals.map { goal ->
                            repository.observeEvents(userId, goal.id).map { events ->
                                GoalProgressCalculator.calculate(goal, events)
                            }
                        }) { progress ->
                            goals.mapIndexed { index, goal -> GoalListItemState(goal, progress[index]) }
                        }
                    }.map { entries -> Triple(entries, accounts, premium) }
                }.catch {
                    _state.update {
                        it.copy(isLoading = false, isSaving = false, errorMessage = "No se pudieron cargar las metas.")
                    }
                }.collect { (entries, accounts, hasPremium) ->
                    _state.value = _state.value.copy(
                        userId = userId,
                        goals = entries,
                        backingAccounts = accounts,
                        isLoading = false,
                        hasPremiumAccess = hasPremium,
                    )
                }
            }
        }
    }

    fun clearFeedback() = _state.updateError(null, clearMessage = true)

    /** Revalidate the entitlement on return without discarding goals or showing a new loader. */
    fun refreshPlanAccess() {
        val owner = _state.value.userId ?: return
        viewModelScope.launch {
            val premium = hasVerifiedPremium(owner)
            _state.update { if (it.userId == owner) it.copy(hasPremiumAccess = premium) else it }
        }
    }

    fun saveGoal(
        goalId: String?,
        name: String,
        target: Money,
        targetDate: LocalDate?,
        linkedAccountId: String?,
        replacementGoalId: String? = null,
        iconKey: String? = null,
    ) {
        val current = _state.value
        val userId = current.userId ?: return _state.updateError("Inicia sesión para guardar una meta.")
        if (current.isSaving) return
        _state.value = current.copy(isSaving = true, errorMessage = null, message = null)
        viewModelScope.launch {
            val result = runCatching {
                if (goalId == null) {
                    repository.createGoal(
                        userId = userId,
                        name = name,
                        target = target,
                        linkedAccountId = linkedAccountId,
                        targetDate = targetDate,
                        hasPremiumAccess = current.hasPremiumAccess,
                        iconKey = iconKey,
                    )
                } else {
                    val goal = current.goals.firstOrNull { it.goal.id == goalId }?.goal
                        ?: run {
                            _state.updateError("La meta ya no está disponible.")
                            return@launch
                        }
                    repository.updateGoal(
                        userId,
                        goalId,
                        UpdateGoalCommand(
                            expectedRevision = goal.revision,
                            name = name,
                            target = target,
                            targetDate = targetDate,
                            linkedAccountId = linkedAccountId,
                            iconKey = iconKey ?: goal.iconKey,
                            colorArgb = goal.colorArgb,
                            replacementGoalId = replacementGoalId,
                            replacementExpectedRevision = replacementGoalId?.let { id -> current.goals.firstOrNull { it.goal.id == id }?.goal?.revision },
                            hasPremiumAccess = current.hasPremiumAccess,
                        ),
                    )
                }
            }.getOrElse {
                _state.updateError("No se pudo guardar la meta; puedes intentarlo nuevamente.")
                return@launch
            }
            handleResult(result)
        }
    }

    fun deleteEmptyGoal(goalId: String) {
        val current = _state.value
        val userId = current.userId ?: return _state.updateError("Inicia sesión para continuar.")
        val goal = current.goals.firstOrNull { it.goal.id == goalId }?.goal
            ?: return _state.updateError("La meta ya no está disponible.")
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            val result = runCatching { repository.deleteEmptyGoal(userId, goalId, goal.revision) }.getOrElse {
                _state.updateError("No se pudo eliminar la meta; puedes intentarlo nuevamente.")
                return@launch
            }
            handleResult(result, isDelete = true)
        }
    }

    private suspend fun hasVerifiedPremium(userId: String): Boolean {
        val uuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return false
        val cache = featureAccessCacheDao.get(uuid) ?: return false
        if (!cache.effectiveTier.equals("PREMIUM", ignoreCase = true)) return false
        return entitlementEvaluator.evaluate(userId, cache) is OfflineEntitlementLeaseDecision.Allowed
    }

    private suspend fun handleResult(result: GoalCommandResult, isDelete: Boolean = false) {
        when (result) {
            is GoalCommandResult.Applied, is GoalCommandResult.Duplicate -> {
                _state.value = _state.value.copy(
                    isSaving = false,
                    message = null,
                    errorMessage = null,
                )
                _events.send(if (isDelete) GoalListEvent.Deleted else GoalListEvent.Saved)
            }
            is GoalCommandResult.Retryable -> _state.updateError("Guardamos localmente; se reintentará la sincronización (${result.code}).")
            is GoalCommandResult.Conflict -> _state.updateError("La meta cambió en otro dispositivo. Actualiza la vista antes de continuar (${result.code}).")
            is GoalCommandResult.Rejected -> _state.updateError(result.message())
        }
    }

    private fun GoalCommandResult.Rejected.message(): String = when (code) {
        "GOAL_LIMIT", "REPLACEMENT_REQUIRED" -> "Elige una meta activa para pausar y liberar un espacio del plan Free."
        "GOAL_DELETE_PENDING" -> "El borrado ya está pendiente de sincronización."
        "HISTORY_PRESERVED", "GOAL_NOT_EMPTY" -> "La meta tiene historial, reservas u operaciones pendientes de sincronizar. Puedes archivarla cuando no tenga reservas."
        "NAME_REQUIRED" -> "Escribe un nombre para la meta."
        "TARGET_MUST_BE_POSITIVE" -> "El objetivo debe ser mayor que cero."
        "CURRENCY_MISMATCH" -> "La cuenta vinculada debe usar la misma moneda."
        else -> "No se pudo guardar la meta ($code)."
    }

    private fun MutableStateFlow<GoalManagementUiState>.updateError(message: String?, clearMessage: Boolean = false) {
        update { it.copy(isSaving = false, errorMessage = message, message = if (clearMessage) null else it.message) }
    }
}
