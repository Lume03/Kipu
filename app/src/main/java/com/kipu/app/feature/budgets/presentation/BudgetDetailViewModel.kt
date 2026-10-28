package com.kipu.app.feature.budgets.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.DeleteBudget
import com.kipu.app.feature.budgets.domain.FinishBudget
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import com.kipu.app.feature.budgets.domain.RenewBudgetPeriods
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Detalle: periodo vigente, historial con limite snapshot y acciones finalizar/eliminar.
 * Las cifras vienen de [GetBudgetConsumption]; este ViewModel no calcula nada financiero.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: SessionCoordinator,
    private val repository: BudgetRepository,
    private val renew: RenewBudgetPeriods,
    private val getConsumption: GetBudgetConsumption,
    private val finishBudget: FinishBudget,
    private val deleteBudget: DeleteBudget,
    private val categories: BudgetCategoryOptionsSource,
    private val zone: ZoneId,
    private val appearance: BudgetAppearanceStore? = null,
) : ViewModel() {
    private val budgetId: String = savedStateHandle.get<String>(ARG_BUDGET_ID).orEmpty()

    internal var todayProvider: () -> LocalDate = { LocalDate.now(zone) }

    private data class Action(val isWorking: Boolean = false, val error: String? = null)

    private val reload = MutableStateFlow(0)
    private val action = MutableStateFlow(Action())
    private val _events = Channel<BudgetDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val base: StateFlow<BudgetDetailUiState> = combine(session.ownerIds(), reload) { owner, _ -> owner }
        .flatMapLatest { owner ->
            if (owner == null || budgetId.isBlank()) {
                flowOf<BudgetDetailUiState>(
                    if (owner == null) BudgetDetailUiState.Error(SESSION_REQUIRED_MESSAGE) else BudgetDetailUiState.NotFound,
                )
            } else {
                combine(
                    repository.observeBudgets(owner),
                    repository.observePeriods(owner, budgetId),
                    categories.observe(owner),
                    appearance?.changes ?: flowOf(0),
                ) { budgets, periods, options, _ -> Triple(budgets.firstOrNull { it.id == budgetId }, periods, options) }
                    .map<_, BudgetDetailUiState> { (budget, periods, options) ->
                        if (budget == null) BudgetDetailUiState.NotFound else build(owner, budget, periods, options)
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(BudgetDetailUiState.Error("No se pudo cargar el presupuesto."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BudgetDetailUiState.Loading)

    val state: StateFlow<BudgetDetailUiState> = combine(base, action) { content, act ->
        if (content is BudgetDetailUiState.Content) {
            BudgetDetailUiState.Content(content.content.copy(isWorking = act.isWorking, actionError = act.error))
        } else {
            content
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BudgetDetailUiState.Loading)

    fun reload() {
        reload.update { it + 1 }
    }

    fun clearActionError() = action.update { it.copy(error = null) }

    fun finish() = runAction { owner ->
        when (val outcome = finishBudget(owner, budgetId)) {
            is BudgetOutcome.Success -> {
                _events.send(BudgetDetailEvent.Finished)
                null
            }
            is BudgetOutcome.Rejected -> outcome.errors.firstOrNull()?.let(BudgetTexts::error)
        }
    }

    fun delete() = runAction { owner ->
        when (val outcome = deleteBudget(owner, budgetId)) {
            is BudgetOutcome.Success -> {
                appearance?.saveIcon(owner, budgetId, null)
                _events.send(BudgetDetailEvent.Deleted)
                null
            }
            is BudgetOutcome.Rejected -> outcome.errors.firstOrNull()?.let(BudgetTexts::error)
        }
    }

    private fun runAction(block: suspend (owner: String) -> String?) {
        if (action.value.isWorking) return
        action.value = Action(isWorking = true)
        viewModelScope.launch {
            val error = try {
                val owner = session.ownerIds().first()
                if (owner == null) {
                    fail(SESSION_REQUIRED_MESSAGE)
                    return@launch
                }
                block(owner)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "No se pudo completar la acción. Puedes intentarlo de nuevo."
            }
            action.value = Action(isWorking = false, error = error)
        }
    }

    private fun fail(message: String) {
        action.value = Action(isWorking = false, error = message)
    }

    private suspend fun build(
        owner: String,
        budget: Budget,
        periods: List<BudgetPeriod>,
        options: List<BudgetCategoryOption>,
    ): BudgetDetailUiState {
        val today = todayProvider()
        if (budget.isActive) renew(owner, budget.id, today)
        val names = options.associate { it.id to it.label }
        val rows = periods.sortedByDescending { it.interval.start }.map { period ->
            val result = getConsumption.forPeriod(budget.currency, period, zone)
            BudgetPeriodRow(
                periodId = period.id,
                interval = period.interval,
                scopeLabel = BudgetTexts.scopeLabel(period.scope) { names[it] },
                consumption = result.consumption,
                isCurrent = today in period.interval,
            )
        }
        // Vigente; si el rango ya termino, el ultimo periodo conocido (como GetBudgetConsumption.current).
        val current = rows.firstOrNull { it.isCurrent } ?: rows.firstOrNull()
        val pending = budget.pendingChange?.let { change ->
            val parts = buildList {
                change.scope?.let { add("alcance «${BudgetTexts.scopeLabel(it) { id -> names[id] }}»") }
                change.schedule?.let { add("calendario «${BudgetTexts.schedule(it)}»") }
            }.joinToString(" y ")
            "Cambio de $parts: aplica desde el ${BudgetTexts.date(change.effectiveFrom)}."
        }
        return BudgetDetailUiState.Content(
            BudgetDetailContent(
                budget = budget,
                scopeLabel = BudgetTexts.scopeLabel(budget.scope) { names[it] },
                current = current,
                history = rows.filter { it.periodId != current?.periodId },
                pendingChangeLabel = pending,
                iconId = budgetIconId(budget.scope.categoryIdOrNull(), appearance?.icon(owner, budget.id), options),
                isParentCategory = budget.scope.categoryIdOrNull()?.let { id -> options.firstOrNull { it.id == id }?.parentId == null && options.any { it.id == id } } == true,
            ),
        )
    }

    companion object {
        const val ARG_BUDGET_ID = "budgetId"
    }
}
