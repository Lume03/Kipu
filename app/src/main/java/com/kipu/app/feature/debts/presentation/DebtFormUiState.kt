package com.kipu.app.feature.debts.presentation

import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.DebtAmountParser
import java.time.LocalDate

data class DebtAccountOption(
    val id: String,
    val name: String,
    val currencyCode: String,
    val balanceMinor: Long? = null,
    val effectiveAccountId: String = id,
    val sharedWith: String? = null,
)

internal fun debtAccountOptions(entities: List<AccountEntity>, balances: Map<String, Long>): List<DebtAccountOption> {
    val eligible = entities.filterNot { it.type in setOf("GOALS_VIRTUAL", "CREDIT_LIABILITY") }
    val byId = eligible.associateBy { it.id }
    return eligible.mapNotNull { account ->
        val parent = account.linkedAccountId?.let { byId[it] ?: return@mapNotNull null }
        if (parent != null && (parent.currency != account.currency || parent.linkedAccountId != null)) return@mapNotNull null
        val effectiveId = parent?.id ?: account.id
        DebtAccountOption(account.id, account.alias, account.currency, balances[effectiveId], effectiveId, parent?.alias)
    }
}

data class DebtFormUiState(
    val counterpartyName: String = "",
    val principalAmount: String = "",
    val currencyCode: String = "PEN",
    val openingMode: DebtOpeningMode = DebtOpeningMode.NEW_CASH_FLOW,
    val selectedAccountId: String? = null,
    val availableAccounts: List<DebtAccountOption> = emptyList(),
    val openedOn: LocalDate = LocalDate.now(),
    val dueDate: LocalDate? = null,
    val dueDateInput: String = "",
    val reminderLeadDays: Int? = null,
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

internal fun DebtFormUiState.isValidForSave(): Boolean {
    val amountIsPositive = DebtAmountParser.toMinorUnits(principalAmount) != null
    val accountIsValid = openingMode == DebtOpeningMode.HISTORICAL || availableAccounts.any { account ->
        account.id == selectedAccountId && account.currencyCode == currencyCode
    }
    val dueDateIsValid = dueDateInput.isBlank() || dueDate != null
    return counterpartyName.trim().isNotEmpty() && amountIsPositive && accountIsValid && dueDateIsValid && !isSaving
}
