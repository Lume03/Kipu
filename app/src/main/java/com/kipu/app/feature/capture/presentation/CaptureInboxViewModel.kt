package com.kipu.app.feature.capture.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import com.kipu.app.feature.capture.domain.CaptureDiscardResult
import com.kipu.app.feature.capture.domain.DiscardCaptureCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the "Captura automática" banner shows; derived from the verified gate, never from a UI flag. */
enum class CaptureAutomationStatus {
    CHECKING,
    ACTIVE,
    PREMIUM_REQUIRED,
    SETUP_REQUIRED,
    NO_SESSION,
}

internal fun CaptureAccessDecision.toAutomationStatus(): CaptureAutomationStatus = when (this) {
    is CaptureAccessDecision.Allowed -> CaptureAutomationStatus.ACTIVE
    CaptureAccessDecision.PremiumRequired -> CaptureAutomationStatus.PREMIUM_REQUIRED
    CaptureAccessDecision.PermissionMissing, CaptureAccessDecision.ConsentMissing -> CaptureAutomationStatus.SETUP_REQUIRED
    CaptureAccessDecision.NoSession -> CaptureAutomationStatus.NO_SESSION
}

data class CaptureInboxUiState(
    val isLoading: Boolean = true,
    val candidates: List<CaptureCandidateRecord> = emptyList(),
    val pendingAssociations: List<CaptureCandidateRecord> = emptyList(),
    val automation: CaptureAutomationStatus = CaptureAutomationStatus.CHECKING,
    val isBusy: Boolean = false,
    val message: String? = null,
    val loadFailed: Boolean = false,
)

/** HU-51: pending capture candidates of the session owner. Reading them never changes anything. */
@HiltViewModel
class CaptureInboxViewModel @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val gate: CaptureAccessGate,
    private val discardCandidate: DiscardCaptureCandidate,
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureInboxUiState())
    val state: StateFlow<CaptureInboxUiState> = _state.asStateFlow()

    init {
        val owner = gate.currentOwnerId()
        if (owner == null) {
            _state.update { it.copy(isLoading = false, automation = CaptureAutomationStatus.NO_SESSION) }
        } else {
            viewModelScope.launch {
                kotlinx.coroutines.flow.combine(candidates.observePending(owner), candidates.observePendingExpectedMatches(owner)) { pending, associations -> pending to associations }
                    .catch { _state.update { current -> current.copy(isLoading = false, loadFailed = true) } }
                    .collect { list -> _state.update { it.copy(isLoading = false, loadFailed = false, candidates = list.first, pendingAssociations = list.second) } }
            }
            refreshAutomation()
        }
    }

    /** Re-evaluates the gate, e.g. when returning from the permissions screen. */
    fun refreshAutomation() {
        viewModelScope.launch {
            val status = try {
                gate.evaluateCurrent().toAutomationStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CaptureAutomationStatus.SETUP_REQUIRED
            }
            _state.update { it.copy(automation = status) }
        }
    }

    fun discard(candidateId: String) {
        val owner = gate.currentOwnerId() ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val message = try {
                when (discardCandidate(owner, candidateId)) {
                    is CaptureDiscardResult.Discarded -> "Captura descartada. No se registró ningún movimiento."
                    CaptureDiscardResult.AlreadyRegistered -> "Esta captura ya se registró."
                    CaptureDiscardResult.NotFound -> "La captura ya no está disponible."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "No se pudo descartar. Inténtalo de nuevo."
            }
            _state.update { it.copy(isBusy = false, message = message) }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
