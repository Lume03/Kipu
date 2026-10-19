package com.kipu.app.feature.recurrences.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.recurrences.domain.RecurrenceDraft
import com.kipu.app.feature.recurrences.domain.RecurrenceLifecycleUseCase
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.recurrences.domain.RecurrenceResumePreview
import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurrencePresentationState(
    val recurrences: List<RecurrenceRule> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedRule: RecurrenceRule? = null,
    val resumePreview: RecurrenceResumePreview? = null,
)

@HiltViewModel
class RecurrencePresentationViewModel @Inject constructor(
    private val repository: RecurrenceRepository,
    private val lifecycle: RecurrenceLifecycleUseCase,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(RecurrencePresentationState())
    val state = _state.asStateFlow()
    private var activeUserId: String? = null

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess.collectLatest { access ->
                val userId = (access as? LocalAccess.Available)?.userId
                activeUserId = userId
                _state.value = RecurrencePresentationState(isLoading = userId != null)
                if (userId != null) repository.observeAll(userId).collectLatest { rules ->
                    if (activeUserId == userId) _state.update { it.copy(recurrences = rules, isLoading = false) }
                }
            }
        }
    }

    fun create(draft: RecurrenceDraft) {
        val userId = activeUserId ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, errorMessage = null, savedRule = null) }
        viewModelScope.launch {
            try {
                when (val result = repository.create(userId, draft)) {
                    is RecurrenceCommandResult.Pending -> _state.update { it.copy(isSaving = false, savedRule = result.recurrence) }
                    is RecurrenceCommandResult.Synced -> _state.update { it.copy(isSaving = false, savedRule = result.recurrence) }
                    is RecurrenceCommandResult.Conflict -> _state.update { it.copy(isSaving = false, errorMessage = "Conflicto de revisión; vuelve a cargar la lista.") }
                    is RecurrenceCommandResult.Rejected -> _state.update { it.copy(isSaving = false, errorMessage = result.message) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(isSaving = false, errorMessage = error.message ?: "No se pudo guardar la recurrencia") } }
        }
    }

    fun pause(rule: RecurrenceRule) = runCommand("No se pudo pausar la recurrencia") { lifecycle.pause(rule) }

    fun requestResume(rule: RecurrenceRule) {
        try {
            val preview = lifecycle.previewResume(rule)
            _state.update { it.copy(resumePreview = preview, errorMessage = null) }
        } catch (error: Exception) {
            _state.update { it.copy(errorMessage = error.message ?: "No se pudo calcular la próxima fecha") }
        }
    }

    fun confirmResume() {
        val preview = _state.value.resumePreview ?: return
        val rule = _state.value.recurrences.firstOrNull { it.id == preview.recurrenceId } ?: return
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = lifecycle.resume(rule, preview, confirmed = true)
                _state.update { it.copy(isSaving = false, resumePreview = null, errorMessage = result.toError()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(isSaving = false, errorMessage = error.message ?: "No se pudo reanudar") } }
        }
    }

    fun cancel(rule: RecurrenceRule) = runCommand("No se pudo cancelar la recurrencia") { lifecycle.cancel(rule, confirmed = true) }

    fun dismissConfirmation() { _state.update { it.copy(resumePreview = null) } }

    fun clearSavedRule() { _state.update { it.copy(savedRule = null) } }

    private fun runCommand(fallback: String, command: suspend () -> RecurrenceCommandResult?) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = command()
                _state.update { it.copy(isSaving = false, errorMessage = result.toError(fallback)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(isSaving = false, errorMessage = error.message ?: fallback) } }
        }
    }

    private fun RecurrenceCommandResult?.toError(fallback: String = "La solicitud no se aplicó") = when (this) {
        is RecurrenceCommandResult.Conflict -> "Conflicto de revisión: se esperaba ${expectedRevision}, versión actual ${currentRevision ?: "desconocida"}."
        is RecurrenceCommandResult.Rejected -> message
        null -> fallback
        is RecurrenceCommandResult.Pending, is RecurrenceCommandResult.Synced -> null
    }
}
