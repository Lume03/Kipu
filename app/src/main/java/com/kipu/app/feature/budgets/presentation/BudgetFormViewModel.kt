package com.kipu.app.feature.budgets.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetError
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.CreateBudget
import com.kipu.app.feature.budgets.domain.CreateBudgetCommand
import com.kipu.app.feature.budgets.domain.EditBudget
import com.kipu.app.feature.budgets.domain.EditBudgetCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado editable del formulario de alta/edicion. */
data class BudgetFormState(
    val isEdit: Boolean,
    val input: BudgetFormInput,
    val original: BudgetFormInput?,
    val categories: List<BudgetCategoryOption>,
    val fieldErrors: Map<BudgetFormField, String> = emptyMap(),
    val generalError: String? = null,
    val isSaving: Boolean = false,
    /** Si no es null, se muestra la explicacion del cupo Free (valor = tope de presupuestos activos). */
    val quotaLimitDialog: Int? = null,
) {
    /** Cambiar alcance o calendario de un presupuesto existente aplica desde el siguiente periodo. */
    val scopeOrScheduleChanged: Boolean
        get() = original != null && (
            input.scopeKind != original.scopeKind ||
                input.categoryId != original.categoryId ||
                input.periodType != original.periodType ||
                input.rangeStartText != original.rangeStartText ||
                input.rangeEndText != original.rangeEndText
            )
}

sealed interface BudgetFormUiState {
    data object Loading : BudgetFormUiState
    data class Content(val form: BudgetFormState) : BudgetFormUiState
    data object NotFound : BudgetFormUiState
    data class Error(val message: String) : BudgetFormUiState
}

sealed interface BudgetFormEvent {
    data class Saved(val budgetId: String) : BudgetFormEvent
}

@HiltViewModel
class BudgetFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: SessionCoordinator,
    private val repository: BudgetRepository,
    private val createBudget: CreateBudget,
    private val editBudget: EditBudget,
    private val categories: BudgetCategoryOptionsSource,
    private val zone: ZoneId,
    private val appearance: BudgetAppearanceStore? = null,
) : ViewModel() {
    /** null = alta; con id = edicion. */
    private val editingId: String? = savedStateHandle.get<String>(ARG_BUDGET_ID)?.takeIf { it.isNotBlank() }

    internal var todayProvider: () -> LocalDate = { LocalDate.now(zone) }
    internal var idGenerator: () -> String = { UUID.randomUUID().toString() }

    private val _state = MutableStateFlow<BudgetFormUiState>(BudgetFormUiState.Loading)
    val state: StateFlow<BudgetFormUiState> = _state.asStateFlow()
    private val _events = Channel<BudgetFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.value = BudgetFormUiState.Loading
        viewModelScope.launch {
            try {
                val owner = session.ownerIds().first()
                if (owner == null) {
                    _state.value = BudgetFormUiState.Error(SESSION_REQUIRED_MESSAGE)
                    return@launch
                }
                val options = categories.observe(owner).first()
                if (editingId == null) {
                    _state.value = BudgetFormUiState.Content(
                        BudgetFormState(isEdit = false, input = BudgetFormInput(), original = null, categories = options),
                    )
                    return@launch
                }
                val budget = repository.getBudget(owner, editingId)
                _state.value = when {
                    budget == null -> BudgetFormUiState.NotFound
                    !budget.isActive -> BudgetFormUiState.Error(BudgetTexts.error(BudgetError.BudgetFinished))
                    else -> {
                        val input = BudgetFormInput.from(budget).copy(iconId = appearance?.icon(owner, budget.id))
                        BudgetFormUiState.Content(BudgetFormState(true, input, input, options))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = BudgetFormUiState.Error("No se pudo cargar el formulario.")
            }
        }
    }

    fun update(transform: (BudgetFormInput) -> BudgetFormInput) {
        updateForm { form ->
            form.copy(input = transform(form.input), fieldErrors = emptyMap(), generalError = null)
        }
    }

    fun dismissQuotaDialog() = updateForm { it.copy(quotaLimitDialog = null) }

    fun save() {
        val form = (_state.value as? BudgetFormUiState.Content)?.form ?: return
        if (form.isSaving) return
        val parsed = when (val result = BudgetFormParser.parse(form.input)) {
            is BudgetFormParseResult.Invalid -> {
                updateForm { it.copy(fieldErrors = result.errors, generalError = null) }
                return
            }
            is BudgetFormParseResult.Valid -> result.value
        }
        updateForm { it.copy(isSaving = true, fieldErrors = emptyMap(), generalError = null) }
        viewModelScope.launch {
            val outcome = try {
                val owner = session.ownerIds().first() ?: run {
                    fail(SESSION_REQUIRED_MESSAGE)
                    return@launch
                }
                if (editingId == null) {
                    createBudget(
                        CreateBudgetCommand(
                            id = idGenerator(), ownerId = owner, name = parsed.name, scope = parsed.scope,
                            limitMinor = parsed.limitMinor, currency = parsed.currency, schedule = parsed.schedule,
                            thresholdsBps = parsed.thresholdsBps, today = todayProvider(),
                        ),
                    )
                } else {
                    editBudget(buildEdit(owner, editingId, form, parsed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail("No se pudo guardar el presupuesto. Puedes intentarlo de nuevo.")
                return@launch
            }
            when (outcome) {
                is BudgetOutcome.Success<Budget> -> {
                    appearance?.saveIcon(outcome.value.ownerId, outcome.value.id, form.input.iconId)
                    updateForm { it.copy(isSaving = false) }
                    _events.send(BudgetFormEvent.Saved(outcome.value.id))
                }
                is BudgetOutcome.Rejected -> reject(outcome.errors)
            }
        }
    }

    private fun buildEdit(owner: String, id: String, form: BudgetFormState, parsed: ParsedBudgetForm): EditBudgetCommand {
        val original = form.original ?: form.input
        val originalParsed = (BudgetFormParser.parse(original) as? BudgetFormParseResult.Valid)?.value
        val scopeChanged = originalParsed == null || originalParsed.scope != parsed.scope
        val scheduleChanged = originalParsed == null || originalParsed.schedule != parsed.schedule
        return EditBudgetCommand(
            ownerId = owner,
            budgetId = id,
            today = todayProvider(),
            name = parsed.name,
            limitMinor = parsed.limitMinor,
            thresholdsBps = parsed.thresholdsBps,
            scope = parsed.scope.takeIf { scopeChanged },
            schedule = parsed.schedule.takeIf { scheduleChanged },
        )
    }

    private fun reject(errors: List<BudgetError>) {
        val quota = errors.filterIsInstance<BudgetError.FreeQuotaExceeded>().firstOrNull()
        val byField = linkedMapOf<BudgetFormField, String>()
        val general = mutableListOf<String>()
        errors.filter { it !is BudgetError.FreeQuotaExceeded }.forEach { error ->
            val field = error.field()
            if (field == null) general += BudgetTexts.error(error) else byField.putIfAbsent(field, BudgetTexts.error(error))
        }
        updateForm {
            it.copy(
                isSaving = false,
                fieldErrors = byField,
                generalError = general.firstOrNull(),
                quotaLimitDialog = quota?.limit,
            )
        }
    }

    private fun fail(message: String) = updateForm { it.copy(isSaving = false, generalError = message) }

    private fun updateForm(transform: (BudgetFormState) -> BudgetFormState) {
        _state.update { current ->
            if (current is BudgetFormUiState.Content) BudgetFormUiState.Content(transform(current.form)) else current
        }
    }

    companion object {
        const val ARG_BUDGET_ID = "budgetId"
    }
}
