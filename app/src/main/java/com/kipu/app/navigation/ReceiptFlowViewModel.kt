package com.kipu.app.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.receipts.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.util.UUID

data class ReceiptFlowState(
    val candidates: List<ReceiptCandidate> = emptyList(),
    val selected: ReceiptCandidate? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val warningTitle: String? = null,
    val readyForMovement: Boolean = false,
    val importConsumed: Boolean = false,
    val registered: List<RegisteredYapeReceipt> = emptyList(),
    val queue: ReceiptReviewQueue? = null,
    val savedCandidate: ReceiptCandidate? = null,
    val savedMovementId: String? = null,
    val newCandidateId: String? = null,
    val addedEventId: String? = null,
    val notice: ReceiptImportNotice? = null,
    val duplicateCandidate: ReceiptCandidate? = null,
    val duplicateMovementId: String? = null,
    val pendingImportUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
)

@HiltViewModel
class ReceiptFlowViewModel @Inject constructor(
    private val store: ReceiptCandidateStore,
    private val importer: ImportYapeReceipt,
    private val confirmer: ConfirmReceiptCandidate,
    private val session: SessionCoordinator,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var owner = (session.localAccess.value as? LocalAccess.Available)?.userId
    private val _state = MutableStateFlow(ReceiptFlowState(importConsumed = savedState["importConsumed"] ?: false))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.localAccess.collectLatest { access ->
                // Session restoration may finish after Navigation restores this destination.
                if (owner == null && access is LocalAccess.Available) owner = access.userId
                if (!hasAccess()) {
                    if (owner != null) savedState.remove<String>("selectedCandidate")
                    _state.value = ReceiptFlowState(
                        importConsumed = savedState["importConsumed"] ?: false,
                        message = "Desbloquea la sesión propietaria para revisar comprobantes.",
                    )
                } else {
                    if (_state.value.selected == null) savedState.get<String>("selectedCandidate")?.let { loadCandidate(it) }
                    combine(store.observe(requireNotNull(owner)), store.observeRegistered(requireNotNull(owner))) { rows, registered ->
                        pendingReceipts(rows) to registered
                    }
                        .catch { _state.update { it.copy(message = "No se pudo cargar la bandeja.") } }
                        .collect { (rows, registered) -> if (hasAccess()) _state.update { it.copy(candidates = rows, registered = registered) } }
                }
            }
        }
    }

    private fun hasAccess() = owner != null && (session.localAccess.value as? LocalAccess.Available)?.userId == owner

    fun import(uri: String) = work {
        if (_state.value.importConsumed) return@work
        val result = importer(requireNotNull(owner), Uri.parse(uri), userConsented = true)
        if (!hasAccess()) return@work
        when (result) {
            is ReceiptImportResult.CandidateCreated -> {
                consumeImport()
                closeEditor()
                _state.update { it.copy(newCandidateId = result.candidate.id, addedEventId = result.candidate.id,
                    message = null, notice = null) }
            }
            is ReceiptImportResult.Duplicate -> {
                consumeImport()
                val duplicate = store.get(requireNotNull(owner), result.existingCandidateId)
                if (!hasAccess()) return@work
                val movement = duplicate?.takeIf { it.status == ReceiptCandidateStatus.CONFIRMED }
                    ?.let { store.registeredMovementId(requireNotNull(owner), it.id) }
                if (!hasAccess()) return@work
                _state.update { it.copy(notice = receiptImportNotice(result, duplicate?.status),
                    duplicateCandidate = duplicate, duplicateMovementId = movement) }
            }
            ReceiptImportResult.AccessDenied -> {
                consumeImport()
                message("No se pudo autorizar la lectura local. Puedes registrar el movimiento manualmente sin leer la imagen.")
            }
            ReceiptImportResult.Unreadable, ReceiptImportResult.NotYape, ReceiptImportResult.UnsupportedFormat -> {
                consumeImport()
                _state.update { it.copy(notice = receiptImportNotice(result), message = null) }
            }
            ReceiptImportResult.ConsentRequired -> message("Autoriza la lectura local antes de continuar.")
        }
    }

    fun requestImport(uri: String, name: String?, size: Long?) {
        if (!hasAccess() || _state.value.busy) return
        savedState["importConsumed"] = false
        closeEditor()
        _state.update { it.copy(pendingImportUri = uri, importConsumed = false, fileName = name,
            fileSize = size, notice = null, message = null) }
    }

    fun cancelImport() { consumeImport(); _state.update { it.copy(pendingImportUri = null) } }
    fun dismissNotice() { _state.update { it.copy(notice = null, duplicateCandidate = null, duplicateMovementId = null) } }
    fun consumeAddedEvent() { _state.update { it.copy(addedEventId = null) } }
    fun reviewDuplicate() {
        val id = _state.value.duplicateCandidate?.id ?: return
        dismissNotice()
        select(id)
    }
    fun restoreDuplicate() = work {
        val id = _state.value.duplicateCandidate?.id ?: return@work
        val restored = store.restore(requireNotNull(owner), id)
        if (!hasAccess()) return@work
        if (restored) {
            dismissNotice()
            _state.update { it.copy(newCandidateId = id, addedEventId = id) }
        } else message("No se pudo agregar el comprobante de nuevo.")
    }

    private fun consumeImport() {
        savedState["importConsumed"] = true
        _state.update { it.copy(importConsumed = true) }
    }

    fun manual() = work {
        consumeImport()
        val result = store.import(requireNotNull(owner), "manual:${UUID.randomUUID()}", YapeReceiptFields())
        if (result is ReceiptCandidateSaveResult.Saved) loadCandidate(result.candidate.id)
    }

    fun select(id: String) = work {
        _state.update { it.copy(queue = ReceiptReviewQueue.start(it.candidates, id), savedCandidate = null) }
        loadCandidate(id)
    }
    fun startReview() { _state.value.candidates.firstOrNull()?.let { select(it.id) } }
    fun reviewNext() = work {
        val next = _state.value.queue?.next(_state.value.candidates.map { it.id }.toSet())
        if (next?.currentId == null) closeEditor()
        else {
            _state.update { it.copy(queue = next, savedCandidate = null, message = null) }
            loadCandidate(requireNotNull(next.currentId))
        }
    }
    fun movementSaved() {
        val selected = _state.value.selected ?: return
        savedState.remove<String>("selectedCandidate")
        _state.update { current ->
            val remaining = current.candidates.filterNot { it.id == selected.id }
            val next = current.queue?.next(remaining.map { it.id }.toSet())
            current.copy(selected = null, readyForMovement = false, candidates = remaining,
                savedCandidate = selected.takeIf { next != null }, queue = current.queue.takeIf { next != null }, message = null)
        }
    }

    private suspend fun loadCandidate(id: String, duplicate: Boolean = false) {
        if (!hasAccess()) return
        val candidate = store.get(requireNotNull(owner), id)
        if (!hasAccess()) return
        if (candidate?.status == ReceiptCandidateStatus.PENDING) {
            savedState["selectedCandidate"] = id
            _state.update { it.copy(selected = candidate, readyForMovement = false, message = null, warningTitle = null) }
            if (duplicate) _state.update { it.copy(
                message = "Este comprobante ya está pendiente de revisión. Continúa aquí; no se creó otra entrada.",
                warningTitle = "Comprobante repetido",
            ) }
        } else {
            savedState.remove<String>("selectedCandidate")
            _state.update { it.copy(
                selected = null, readyForMovement = false,
                message = when (candidate?.status) {
                    ReceiptCandidateStatus.CONFIRMED -> candidate.registeredReceiptMessage()
                    ReceiptCandidateStatus.DISCARDED -> "Este comprobante ya fue descartado. No se registró otro movimiento ni cambió tu saldo."
                    else -> "Este comprobante ya no está disponible. Revisa la bandeja."
                },
                warningTitle = if (candidate?.status == ReceiptCandidateStatus.CONFIRMED) "Posible movimiento duplicado" else "Comprobante no disponible",
            ) }
        }
    }

    fun edit(edit: ReceiptCandidateEdit) = work {
        val id = _state.value.selected?.id ?: return@work
        val updated = store.edit(requireNotNull(owner), id, edit)
        if (!hasAccess()) return@work
        if (updated == null) message("La referencia ya está en otro comprobante o el candidato ya no está pendiente. Revisa la bandeja; tu saldo no cambió.")
        else _state.update { it.copy(selected = updated, readyForMovement = true) }
    }

    fun editCandidate(id: String, edit: ReceiptCandidateEdit) = work {
        if (store.edit(requireNotNull(owner), id, edit) == null) message("No se pudieron guardar los cambios. Revisa la referencia del comprobante.")
    }

    suspend fun confirm(candidateId: String, command: RegisterTransactionCommand): RegisterTransactionResult {
        if (!hasAccess() || command.userId != owner) {
            return RegisterTransactionResult.ValidationError("receipt", "Desbloquea tu sesión para confirmar.")
        }
        val result = confirmer(requireNotNull(owner), candidateId, command)
        if (hasAccess() && result is RegisterTransactionResult.Success) {
            val confirmed = store.get(requireNotNull(owner), candidateId)
            if (hasAccess()) _state.update { it.copy(selected = confirmed ?: it.selected, savedMovementId = result.transaction.id) }
        }
        return result
    }

    fun discard(id: String) = work { store.discard(requireNotNull(owner), id) }

    fun closeEditor() {
        savedState.remove<String>("selectedCandidate")
        _state.update { it.copy(selected = null, readyForMovement = false, queue = null, savedCandidate = null, savedMovementId = null) }
    }

    fun closeMovement() { _state.update { it.copy(readyForMovement = false) } }
    fun message(text: String) { _state.update { it.copy(message = text, warningTitle = null) } }
    fun dismissMessage() { _state.update { it.copy(message = null, warningTitle = null) } }

    private fun work(block: suspend () -> Unit) {
        if (!hasAccess() || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("No se pudo completar la acción. Puedes reintentarlo.") }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

private fun ReceiptCandidate.registeredReceiptMessage(): String = buildString {
    append("Ya registraste una operación")
    reference?.takeIf(String::isNotBlank)?.let { append(" con el N.º $it") }
    amountMinor?.let { append(" por S/ ${java.math.BigDecimal.valueOf(it, 2).toPlainString()}") }
    occurredAt?.let {
        val date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.of("America/Lima"))
        append(" el ${date.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale.forLanguageTag("es-PE")))}")
    }
    append(". Revisa tus movimientos antes de registrar otra vez.")
}
