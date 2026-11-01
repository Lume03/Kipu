package com.kipu.app.feature.setup

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.plans.domain.model.CommercialOption
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The kind of first account the owner sets up; each one opens its own presets. */
enum class SetupAccountKind { BANK, WALLET, CASH }

/** Bank account flavours of the existing account form; only savings maps to its own [AccountType]. */
enum class SetupBankSubtype(val label: String, val type: AccountType) {
    SAVINGS("Ahorros", AccountType.SAVINGS),
    PAYROLL("Sueldo", AccountType.BANK),
    CHECKING("Corriente", AccountType.BANK),
}

data class SetupAccountDraft(
    val kind: SetupAccountKind? = null,
    val preset: AccountPreset? = null,
    val bankSubtype: SetupBankSubtype = SetupBankSubtype.SAVINGS,
    val alias: String = "",
    val balanceText: String = "",
    val currency: Currency = Currency.PEN,
) {
    val accountType: AccountType?
        get() = when (kind) {
            SetupAccountKind.BANK -> bankSubtype.type
            SetupAccountKind.WALLET -> AccountType.DIGITAL_WALLET
            SetupAccountKind.CASH -> AccountType.CASH
            null -> null
        }

    /** Minor units of the typed balance, or null when the text is not a valid non-negative amount. */
    val balanceMinorUnits: Long?
        get() {
            val text = balanceText.trim().replace(",", "")
            if (text.isEmpty()) return 0L
            val value = text.toBigDecimalOrNull() ?: return null
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) return null
            return value.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toLong()
        }

    val balanceError: String?
        get() = if (balanceMinorUnits == null) "Ingresa un monto válido, por ejemplo 150.00" else null

    val aliasError: String?
        get() = if (alias.isNotBlank() && alias.trim().length > 80) "Máximo 80 caracteres" else null

    val isComplete: Boolean
        get() {
            val needsPreset = kind != SetupAccountKind.CASH
            return kind != null && (!needsPreset || preset != null) && alias.isNotBlank() &&
                aliasError == null && balanceError == null
        }
}

data class SetupDraft(
    val email: String = "",
    val account: SetupAccountDraft = SetupAccountDraft(),
    val hiddenCategoryIds: Set<String> = emptySet(),
    val plan: CommercialOption = CommercialOption.ANNUAL,
)

/** In-memory choices for the active owner; credentials are never kept here. */
@Singleton
class SetupDraftStore @Inject constructor() {
    private val _draft = MutableStateFlow(SetupDraft())
    val draft: StateFlow<SetupDraft> = _draft.asStateFlow()
    private var ownerId: String? = null

    var codeSent: Boolean = true
        private set

    fun begin(email: String, codeSent: Boolean = true) {
        this.codeSent = codeSent
        ownerId = null
        _draft.value = SetupDraft(email = email.trim())
    }

    fun bindOwner(owner: String) {
        if (ownerId == owner) return
        _draft.value = SetupDraft(email = if (ownerId == null) _draft.value.email else "")
        ownerId = owner
    }

    fun update(transform: (SetupDraft) -> SetupDraft) = _draft.update(transform)
    fun clear() { ownerId = null; _draft.value = SetupDraft() }
}
