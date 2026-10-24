package com.kipu.app.feature.budgets.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetPlanAccess
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import com.kipu.app.feature.budgets.domain.RenewBudgetPeriods
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lista de presupuestos. No calcula nada financiero: renovacion, consumo, restante y porcentaje
 * salen de los casos de uso del dominio.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetListViewModel @Inject constructor(
    private val session: SessionCoordinator,
    private val repository: BudgetRepository,
    private val renew: RenewBudgetPeriods,
    private val getConsumption: GetBudgetConsumption,
    private val categories: BudgetCategoryOptionsSource,
    private val planAccess: BudgetPlanAccess,
    private val zone: ZoneId,
) : ViewModel() {
    internal var todayProvider: () -> LocalDate = { LocalDate.now(zone) }

    private val reload = MutableStateFlow(0)
    private val _state = MutableStateFlow<BudgetListUiState>(BudgetListUiState.Loading)
    val state: StateFlow<BudgetListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(session.ownerIds(), reload) { owner, _ -> owner }
                .flatMapLatest { owner ->
                    if (owner == null) {
                        flowOf<BudgetListUiState>(BudgetListUiState.Error(SESSION_REQUIRED_MESSAGE))
                    } else {
                        combine(repository.observeBudgets(owner), categories.observe(owner)) { budgets, options ->
                            budgets to options
                        }.map<_, BudgetListUiState> { (budgets, options) -> build(owner, budgets, options) }
                            .catch { error ->
                                if (error is CancellationException) throw error
                                emit(BudgetListUiState.Error("No se pudieron cargar los presupuestos."))
                            }
                    }
                }
                .collect { _state.value = it }
        }
    }

    /** Reintenta tras un error o refresca el consumo al volver a la pantalla. */
    fun reload() {
        reload.update { it + 1 }
    }

    private suspend fun build(
        owner: String,
        budgets: List<Budget>,
        options: List<BudgetCategoryOption>,
    ): BudgetListUiState {
        val premium = planAccess.isPremiumVerified(owner)
        val quota = BudgetQuotaState(
            activeCount = budgets.count { it.isActive },
            limit = FreePlanLimits().budgets,
            isPremium = premium,
        )
        if (budgets.isEmpty()) return BudgetListUiState.Empty(quota)

        val today = todayProvider()
        val names = options.associate { it.id to it.label }
        val items = budgets.sortedWith(compareBy({ !it.isActive }, { it.name.lowercase() })).map { budget ->
            if (budget.isActive) renew(owner, budget.id, today)
            val current = (getConsumption.current(owner, budget.id, today, zone) as? BudgetOutcome.Success)?.value
            BudgetListItem(
                id = budget.id,
                name = budget.name,
                scopeLabel = BudgetTexts.scopeLabel(current?.period?.scope ?: budget.scope) { names[it] },
                currency = budget.currency,
                status = budget.status,
                periodType = budget.schedule.type,
                interval = current?.period?.interval,
                consumption = current?.consumption,
                hasPendingChange = budget.pendingChange != null,
            )
        }
        return BudgetListUiState.Content(items, quota)
    }
}
