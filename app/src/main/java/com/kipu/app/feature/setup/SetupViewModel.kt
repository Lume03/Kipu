package com.kipu.app.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.auth.domain.model.OtpException
import com.kipu.app.feature.auth.domain.model.OtpFailure
import com.kipu.app.feature.auth.presentation.SessionEntryCurtain
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import com.kipu.app.feature.plans.domain.model.CommercialOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Saving the initial configuration for an authenticated owner. */
data class SetupSaveState(
    val loading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

enum class OtpNotice { RESENT }

data class SetupOtpState(
    val code: String = "",
    val verifying: Boolean = false,
    val resending: Boolean = false,
    val failure: OtpFailure? = null,
    val attemptsLeft: Int? = null,
    val resendSeconds: Int = 0,
    val lockedSeconds: Int = 0,
    val notice: OtpNotice? = null,
    /** The code was accepted; the screen celebrates briefly before moving on. */
    val verified: Boolean = false,
) {
    val busy: Boolean get() = verifying || resending || verified
    val canSubmit: Boolean get() = code.length == OTP_DIGITS && !busy && lockedSeconds == 0
}

const val OTP_DIGITS = 6
private const val OTP_RESEND_SECONDS = 60
private const val OTP_MAX_ATTEMPTS = 5

sealed interface SetupEvent {
    data class Verified(val userId: String) : SetupEvent
    data class Completed(val userId: String) : SetupEvent
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val store: SetupDraftStore,
    private val auth: AuthRepository,
    private val curtain: SessionEntryCurtain,
    private val finalizer: SetupFinalizer,
    private val sessions: com.kipu.app.core.session.SessionCoordinator,
    private val onboarding: com.kipu.app.feature.auth.data.PlanOnboardingStore,
) : ViewModel() {

    val draft: StateFlow<SetupDraft> = store.draft

    private val _save = MutableStateFlow(SetupSaveState())
    val save: StateFlow<SetupSaveState> = _save.asStateFlow()

    private val _otp = MutableStateFlow(SetupOtpState())
    val otp: StateFlow<SetupOtpState> = _otp.asStateFlow()

    private val eventChannel = Channel<SetupEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var timerJob: Job? = null
    private val appliedSteps = mutableSetOf<SetupStep>()
    private var attempts = 0

    /** Category entries the owner can keep or hide, in the order they are shown. */
    val categoryCatalog: List<InitialCategoryCatalog.Entry> = InitialCategoryCatalog.entries

    // ---- step 1 · first account
    fun chooseKind(kind: SetupAccountKind) = store.update { d ->
        val preset = when (kind) {
            SetupAccountKind.BANK -> AccountPreset.BCP
            SetupAccountKind.WALLET -> AccountPreset.YAPE
            SetupAccountKind.CASH -> AccountPreset.CASH
        }
        val alias = d.account.alias.takeIf { d.account.kind == kind && it.isNotBlank() } ?: preset.defaultName
        d.copy(account = d.account.copy(kind = kind, preset = preset, alias = if (kind == SetupAccountKind.CASH) "Efectivo" else alias))
    }

    fun choosePreset(preset: AccountPreset) = store.update { d ->
        val previous = d.account.preset
        val aliasFollowsPreset = d.account.alias.isBlank() || d.account.alias == previous?.defaultName
        d.copy(account = d.account.copy(preset = preset, alias = if (aliasFollowsPreset) preset.defaultName else d.account.alias))
    }

    fun chooseSubtype(subtype: SetupBankSubtype) = store.update { it.copy(account = it.account.copy(bankSubtype = subtype)) }
    fun onAlias(value: String) = store.update { it.copy(account = it.account.copy(alias = value.take(80))) }
    fun onBalance(value: String) = store.update {
        it.copy(account = it.account.copy(balanceText = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(14)))
    }
    fun chooseCurrency(currency: Currency) = store.update { it.copy(account = it.account.copy(currency = currency)) }

    // ---- step 2 · categories
    /** Returns false when the change would leave no category active. */
    fun toggleCategory(id: String): Boolean {
        val hidden = store.draft.value.hiddenCategoryIds
        val total = categoryCatalog.size
        val willHide = id !in hidden
        if (willHide && hidden.size + 1 >= total) return false
        store.update { it.copy(hiddenCategoryIds = if (willHide) hidden + id else hidden - id) }
        return true
    }

    fun setAllCategories(active: Boolean) = store.update {
        it.copy(hiddenCategoryIds = if (active) emptySet() else categoryCatalog.drop(1).map { c -> c.id }.toSet())
    }

    // ---- step 3 · plan
    fun choosePlan(option: CommercialOption) = store.update { it.copy(plan = option) }

    init { sessions.currentOwner?.verifiedUserId?.let(store::bindOwner) }

    // ---- summary: the owner is already authenticated
    fun confirmAll() {
        if (_save.value.loading) return
        val userId = sessions.currentOwner?.verifiedUserId
        if (userId == null || !store.draft.value.account.isComplete) {
            _save.value = SetupSaveState(error = "Completa tu cuenta para continuar.")
            return
        }
        _save.value = SetupSaveState(loading = true)
        viewModelScope.launch {
            finalizer.apply(userId, store.draft.value, appliedSteps).fold(
                onSuccess = {
                    onboarding.setupCompleted(userId)
                    curtain.start(store.draft.value.email, registering = true)
                    curtain.authenticated()
                    store.clear()
                    _save.value = SetupSaveState()
                    eventChannel.send(SetupEvent.Completed(userId))
                },
                onFailure = { error ->
                    _save.value = SetupSaveState(error = error.message ?: "No pudimos guardar tu configuración.")
                },
            )
        }
    }

    fun beginConfirmation() {
        if (_otp.value != SetupOtpState()) return
        _otp.value = SetupOtpState(resendSeconds = if (store.codeSent) OTP_RESEND_SECONDS else 0)
        startTimer()
    }

    // ---- confirmation code
    fun onCodeChanged(raw: String) {
        val state = _otp.value
        if (state.busy) return
        val digits = raw.filter(Char::isDigit).take(OTP_DIGITS)
        _otp.update { it.copy(code = digits, failure = if (it.failure == OtpFailure.INVALID) null else it.failure, notice = null) }
        if (digits.length == OTP_DIGITS) verify()
    }

    fun verify() {
        val state = _otp.value
        val email = store.draft.value.email
        if (!state.canSubmit || email.isBlank()) return
        _otp.update { it.copy(verifying = true, failure = null) }
        viewModelScope.launch {
            auth.verifyOtp(email, state.code).fold(
                onSuccess = { result ->
                    val userId = (result as? AuthResult.Success)?.userId
                    if (userId == null) _otp.update { it.copy(verifying = false, failure = OtpFailure.UNKNOWN) }
                    else {
                        timerJob?.cancel()
                        store.bindOwner(userId)
                        _otp.update { it.copy(verifying = false, verified = true) }
                        eventChannel.send(SetupEvent.Verified(userId))
                    }
                },
                onFailure = { error -> onOtpFailure(error) },
            )
        }
    }

    private fun onOtpFailure(error: Throwable) {
        val failure = error as? OtpException
        attempts += if (failure?.failure == OtpFailure.INVALID) 1 else 0
        when (failure?.failure) {
            OtpFailure.INVALID -> _otp.update {
                it.copy(
                    verifying = false, code = "", failure = OtpFailure.INVALID,
                    attemptsLeft = failure.attemptsLeft ?: (OTP_MAX_ATTEMPTS - attempts).coerceAtLeast(0),
                )
            }
            OtpFailure.LOCKED -> {
                _otp.update { it.copy(verifying = false, code = "", failure = OtpFailure.LOCKED, lockedSeconds = failure.retryAfterSeconds ?: 300) }
                startTimer()
            }
            OtpFailure.EXPIRED -> _otp.update { it.copy(verifying = false, code = "", failure = OtpFailure.EXPIRED, resendSeconds = 0) }
            OtpFailure.OFFLINE -> _otp.update { it.copy(verifying = false, failure = OtpFailure.OFFLINE) }
            else -> _otp.update { it.copy(verifying = false, failure = OtpFailure.UNKNOWN) }
        }
    }

    fun resend() {
        val state = _otp.value
        val email = store.draft.value.email
        if (state.resendSeconds > 0 || state.busy || email.isBlank()) return
        _otp.update { it.copy(resending = true) }
        viewModelScope.launch {
            auth.resendOtp(email).fold(
                onSuccess = {
                    attempts = 0
                    _otp.update { it.copy(code = "", failure = null, attemptsLeft = null, resendSeconds = OTP_RESEND_SECONDS, lockedSeconds = 0, notice = OtpNotice.RESENT) }
                    startTimer()
                },
                onFailure = { error ->
                    val failure = error as? OtpException
                    if (failure?.failure == OtpFailure.LOCKED) {
                        _otp.update { it.copy(resendSeconds = failure.retryAfterSeconds ?: OTP_RESEND_SECONDS) }
                        startTimer()
                    } else {
                        _otp.update { it.copy(failure = failure?.failure ?: OtpFailure.UNKNOWN) }
                    }
                },
            )
            _otp.update { it.copy(resending = false) }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_otp.value.resendSeconds > 0 || _otp.value.lockedSeconds > 0) {
                delay(1_000)
                _otp.update {
                    val locked = (it.lockedSeconds - 1).coerceAtLeast(0)
                    it.copy(
                        resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0),
                        lockedSeconds = locked,
                        failure = if (it.failure == OtpFailure.LOCKED && locked == 0) null else it.failure,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
