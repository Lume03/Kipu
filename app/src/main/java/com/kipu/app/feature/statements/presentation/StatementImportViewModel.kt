package com.kipu.app.feature.statements.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.statements.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Why a chosen PDF did not become a statement to review; each one is answered with its own sheet. */
enum class StatementImportNotice { NOT_STATEMENT, UNSUPPORTED_BANK, UNREADABLE, ALREADY_IMPORTED }

/** What the scan animation celebrates: how many movements were found and whether the PDF totals check out. */
data class StatementScanSummary(
    val importId: String,
    val label: String,
    val count: Int,
    val penTotal: String?,
    val usdTotal: String?,
    val verified: Boolean,
    val sample: List<Pair<String, String>>,
)

data class StatementImportUiState(
    val pending: List<StatementImport> = emptyList(),
    val busy: Boolean = false,
    val reading: Boolean = false,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val attemptsLeft: Int = StatementImportViewModel.MAX_PASSWORD_ATTEMPTS,
    val lockedOut: Boolean = false,
    val notice: StatementImportNotice? = null,
    val scan: StatementScanSummary? = null,
    val premiumLocked: Boolean = false,
    val showConsent: Boolean = false,
    val pickDocument: Boolean = false,
    val showPassword: Boolean = false,
    val password: String = "",
    val wrongPassword: Boolean = false,
    val showPremium: Boolean = false,
    val message: String? = null,
    val openImportId: String? = null,
)

/** Source URI and password deliberately never enter saved state or navigation. */
@HiltViewModel
class StatementImportViewModel @Inject constructor(
    private val importer: ImportCardStatement,
    private val store: StatementImportStore,
    private val premium: StatementImportAccess,
    private val session: SessionCoordinator,
) : ViewModel() {
    companion object { const val MAX_PASSWORD_ATTEMPTS = 3 }

    private val _state = MutableStateFlow(StatementImportUiState())
    val state: StateFlow<StatementImportUiState> = _state.asStateFlow()
    private var owner: String? = (session.localAccess.value as? LocalAccess.Available)?.userId
    private var source: Uri? = null
    private var consent = false
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            session.localAccess.collectLatest { access ->
                if (owner == null && access is LocalAccess.Available) owner = access.userId
                if (!hasAccess()) {
                    operation?.cancel()
                    source = null
                    consent = false
                    _state.value = StatementImportUiState(message = "Desbloquea tu sesión para importar estados de cuenta.")
                } else {
                    val locked = runCatching { !premium.canImport(requireNotNull(owner)) }.getOrDefault(false)
                    if (hasAccess()) _state.update { it.copy(premiumLocked = locked) }
                    store.observePending(requireNotNull(owner))
                        .catch { _state.update { it.copy(message = "No pudimos cargar las importaciones pendientes.") } }
                        .collect { rows -> if (hasAccess()) _state.update { it.copy(pending = rows) } }
                }
            }
        }
    }

    private fun hasAccess() = owner != null && (session.localAccess.value as? LocalAccess.Available)?.userId == owner

    fun requestImport() = work {
        if (!premium.canImport(requireNotNull(owner))) {
            if (hasAccess()) _state.update { it.copy(showPremium = true) }
        } else if (hasAccess()) {
            _state.update { it.copy(showConsent = !consent, pickDocument = consent, message = null) }
        }
    }

    fun acceptConsent() {
        if (!hasAccess() || !_state.value.showConsent) return
        consent = true
        _state.update { it.copy(showConsent = false, pickDocument = true) }
    }
    fun cancelConsent() { _state.update { it.copy(showConsent = false) } }
    fun pickerLaunched() { _state.update { it.copy(pickDocument = false) } }
    fun documentPicked(uri: Uri?, fileName: String? = null, fileSize: Long? = null) {
        pickerLaunched()
        if (!hasAccess() || !consent || _state.value.busy) return
        source = uri
        if (uri != null) {
            _state.update { it.copy(fileName = fileName, fileSize = fileSize, attemptsLeft = MAX_PASSWORD_ATTEMPTS,
                lockedOut = false, wrongPassword = false, notice = null, scan = null) }
            read()
        }
    }
    fun passwordChanged(value: String) {
        if (_state.value.showPassword && !_state.value.busy && hasAccess()) _state.update { it.copy(password = value) }
    }
    fun submitPassword() {
        if (!_state.value.showPassword || _state.value.busy || !hasAccess() || source == null) return
        val secret = _state.value.password.toCharArray()
        _state.update { it.copy(password = "", showPassword = false) }
        read(secret)
    }
    fun cancelPassword() {
        source = null
        _state.update { it.copy(password = "", showPassword = false, wrongPassword = false, lockedOut = false) }
    }
    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun dismissNotice() { _state.update { it.copy(notice = null, lockedOut = false) } }
    fun clearScan() { _state.update { it.copy(scan = null) } }
    fun dismissPremium() { _state.update { it.copy(showPremium = false) } }
    fun consumeNavigation() { _state.update { it.copy(openImportId = null) } }
    fun resumeImport(id: String) = work {
        if (!premium.canImport(requireNotNull(owner))) {
            if (hasAccess()) _state.update { it.copy(showPremium = true) }
        } else if (hasAccess() && _state.value.pending.any { it.id == id }) {
            _state.update { it.copy(openImportId = id) }
        }
    }

    private fun read(secret: CharArray? = null) {
        val uri = source
        if (uri == null || !hasAccess() || _state.value.busy) { secret?.fill('\u0000'); return }
        _state.update { it.copy(reading = true) }
        work {
            try {
                val result = importer(requireNotNull(owner), uri, consent, secret)
                if (!hasAccess()) return@work
                val wrong = result == StatementImportResult.WrongPassword
                val left = if (wrong) (_state.value.attemptsLeft - 1).coerceAtLeast(0) else _state.value.attemptsLeft
                val lockedOut = wrong && left == 0
                val needsPassword = (result == StatementImportResult.PasswordRequired || wrong) && !lockedOut
                if (!needsPassword) source = null
                val summary = (result as? StatementImportResult.Imported)?.let { scanSummary(requireNotNull(owner), it.importId) }
                _state.update {
                    it.copy(showPassword = needsPassword, wrongPassword = wrong && !lockedOut, attemptsLeft = left, lockedOut = lockedOut,
                        password = "", showConsent = result == StatementImportResult.ConsentRequired,
                        showPremium = result == StatementImportResult.PremiumRequired,
                        scan = summary,
                        notice = when (result) {
                            StatementImportResult.NoExtractableText -> StatementImportNotice.UNREADABLE
                            StatementImportResult.Unsupported -> StatementImportNotice.UNSUPPORTED_BANK
                            StatementImportResult.Invalid -> StatementImportNotice.NOT_STATEMENT
                            is StatementImportResult.AlreadyImported -> StatementImportNotice.ALREADY_IMPORTED.takeIf { result.completed }
                            else -> null
                        },
                        openImportId = when (result) {
                            is StatementImportResult.Imported -> result.importId
                            is StatementImportResult.AlreadyImported -> result.importId.takeUnless { result.completed }
                            else -> null
                        }, message = when (result) {
                            StatementImportResult.NoExtractableText -> "Este PDF no tiene texto legible; por ahora solo admitimos estados de cuenta digitales"
                            StatementImportResult.Unsupported -> "Por ahora solo admitimos estados de cuenta BCP Visa"
                            StatementImportResult.Invalid -> "No pudimos leer este estado de cuenta. Revisa el PDF e inténtalo otra vez."
                            is StatementImportResult.AlreadyImported -> "Ya importaste este estado de cuenta".takeIf { result.completed }
                            else -> null
                        })
                }
            } finally { secret?.fill('\u0000') }
        }
        // Also erase if the scope is cancelled before the coroutine body can start.
        operation?.invokeOnCompletion { secret?.fill('\u0000') }
    }

    /** Totals and a few real descriptions for the scan animation; never fails the import. */
    private suspend fun scanSummary(user: String, importId: String): StatementScanSummary? = runCatching {
        val snapshot = store.observe(user, importId).first() ?: return@runCatching null
        val lines = snapshot.lines.filter { it.kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE }
        fun total(currency: Currency) = lines.filter { it.currency == currency }.takeIf { it.isNotEmpty() }
            ?.let { rows -> statementCurrencyTotal(rows, currency) }
        StatementScanSummary(importId, "${snapshot.statement.issuer} •••• ${snapshot.statement.cardLastFour}", lines.size, total(Currency.PEN), total(Currency.USD),
            verified = snapshot.statement.reconciledPen != false && snapshot.statement.reconciledUsd != false,
            sample = lines.take(9).map { it.description to statementPlainAmount(it.amountMinor) })
    }.getOrNull()

    private fun work(block: suspend () -> Unit) {
        if (!hasAccess() || _state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        operation = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                source = null
                if (hasAccess()) _state.update { it.copy(password = "", showPassword = false,
                    message = "No pudimos completar la lectura. Vuelve a elegir el PDF.") }
            } finally { if (hasAccess()) _state.update { it.copy(busy = false, reading = false) } }
        }
    }
}
