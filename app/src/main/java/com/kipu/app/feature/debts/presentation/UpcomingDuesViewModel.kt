package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.feature.debts.domain.usecase.ObserveUpcomingDues
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

enum class DueOriginFilter(val label: String) { ALL("Todos"), DEBTS("Deudas"), CARDS("Tarjetas") }
enum class DueStatusFilter(val label: String) {
    ALL("Todos los estados"), PENDING("Pendientes"), UPCOMING("Próximas"), OVERDUE("Vencidas"), PAID("Pagadas"), FUTURE("Por vencer")
}

data class UpcomingDuesUiState(
    val ownerId: String? = null,
    val rows: List<UpcomingDue> = emptyList(),
    val periods: Map<DuePeriod, List<UpcomingDue>> = emptyMap(),
    val payableTotals: List<Money> = emptyList(),
    val receivableTotals: List<Money> = emptyList(),
    val overdueCount: Int = 0,
    val origin: DueOriginFilter = DueOriginFilter.ALL,
    val status: DueStatusFilter = DueStatusFilter.ALL,
    val obligationType: DebtObligationType? = null,
    val currency: Currency? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val today: java.time.LocalDate = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")),
)

private data class DueData(val ownerId: String?, val snapshot: UpcomingDuesSnapshot? = null, val loading: Boolean = false, val error: String? = null)
private data class DueFilters(val origin: DueOriginFilter, val status: DueStatusFilter, val type: DebtObligationType?, val currency: Currency?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UpcomingDuesViewModel @Inject constructor(
    session: SessionCoordinator,
    private val observeUpcomingDues: ObserveUpcomingDues,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val retries = MutableStateFlow(0)
    private val filters = combine(
        savedStateHandle.getStateFlow("dues.origin", DueOriginFilter.ALL.name),
        savedStateHandle.getStateFlow("dues.status", DueStatusFilter.ALL.name),
        savedStateHandle.getStateFlow("dues.type", "ALL"),
        savedStateHandle.getStateFlow("dues.currency", "ALL"),
    ) { origin, status, type, currency -> DueFilters(
        DueOriginFilter.entries.firstOrNull { it.name == origin } ?: DueOriginFilter.ALL,
        DueStatusFilter.entries.firstOrNull { it.name == status } ?: DueStatusFilter.ALL,
        DebtObligationType.entries.firstOrNull { it.name == type }, Currency.entries.firstOrNull { it.name == currency },
    ) }
    private val data = combine(
        session.localAccess.map { (it as? LocalAccess.Available)?.userId }.distinctUntilChanged(), retries,
    ) { owner, _ -> owner }.flatMapLatest { owner ->
        if (owner == null) flowOf(DueData(null, error = "Inicia sesión para ver tus vencimientos."))
        else observeUpcomingDues(owner).map { DueData(owner, snapshot = it) }
            .onStart { emit(DueData(owner, loading = true)) }
            .catch { emit(DueData(owner, error = "No se pudieron cargar tus vencimientos. Intenta de nuevo.")) }
    }

    val state = combine(data, filters) { data, selected ->
        val snapshot = data.snapshot
        val baseRows = snapshot?.rows.orEmpty().filter { row ->
            (selected.origin == DueOriginFilter.ALL ||
                selected.origin == DueOriginFilter.DEBTS && row.origin == DueOrigin.DEBT ||
                selected.origin == DueOriginFilter.CARDS && row.origin == DueOrigin.CARD) &&
                (selected.type == null || row.obligationType == selected.type) &&
                (selected.currency == null || row.outstanding.currency == selected.currency)
        }
        val visible = baseRows.filter { row -> when (selected.status) {
            DueStatusFilter.ALL -> true
            DueStatusFilter.PENDING -> row.status == DueStatus.PENDING
            DueStatusFilter.UPCOMING -> row.status == DueStatus.UPCOMING
            DueStatusFilter.OVERDUE -> row.status == DueStatus.OVERDUE
            DueStatusFilter.PAID -> row.status == DueStatus.PAID
            DueStatusFilter.FUTURE -> row.status in setOf(DueStatus.PENDING, DueStatus.UPCOMING)
        } }
        fun totals(type: DebtObligationType): List<Money> {
            val debtIds = baseRows.filter { it.origin == DueOrigin.DEBT && it.obligationType == type }.mapTo(hashSetOf()) { it.destinationId }
            val debtAmounts = snapshot?.debts.orEmpty().filter { it.debtId in debtIds }
                .map { Money(it.remainingPrincipalMinor.coerceAtLeast(0), Currency.fromCode(it.currencyCode)) }
            val cardAmounts = baseRows.filter { it.origin == DueOrigin.CARD && it.obligationType == type }.map { it.outstanding }
            return (debtAmounts + cardAmounts).groupBy { it.currency }.map { (currency, amounts) ->
                Money(amounts.fold(0L) { sum, money -> Math.addExact(sum, money.minorUnits) }, currency)
            }.filter { it.minorUnits > 0 }.sortedBy { it.currency.name }
        }
        UpcomingDuesUiState(
            ownerId = data.ownerId, rows = visible, periods = snapshot?.let { value -> visible.groupBy { it.period(value.today) } }.orEmpty(),
            payableTotals = totals(DebtObligationType.PAYABLE), receivableTotals = totals(DebtObligationType.RECEIVABLE),
            overdueCount = baseRows.count { it.status == DueStatus.OVERDUE }, origin = selected.origin, status = selected.status,
            obligationType = selected.type, currency = selected.currency, isLoading = data.loading, error = data.error,
            today = snapshot?.today ?: java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")),
        )
    }.catch { emit(UpcomingDuesUiState(isLoading = false, error = "No se pudieron mostrar los vencimientos. Intenta de nuevo.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingDuesUiState())

    fun selectOrigin(value: DueOriginFilter) { savedStateHandle["dues.origin"] = value.name }
    fun selectStatus(value: DueStatusFilter) { savedStateHandle["dues.status"] = value.name }
    fun selectType(value: DebtObligationType?) { savedStateHandle["dues.type"] = value?.name ?: "ALL" }
    fun selectCurrency(value: Currency?) { savedStateHandle["dues.currency"] = value?.name ?: "ALL" }
    fun retry() { retries.value++ }
}
