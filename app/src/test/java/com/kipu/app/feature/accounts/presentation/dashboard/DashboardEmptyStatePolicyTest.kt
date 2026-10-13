package com.kipu.app.feature.accounts.presentation.dashboard

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.presentation.DashboardUiState
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardEmptyStatePolicyTest {
    @Test
    fun emptyDashboardIsFirstUseOnlyWhenLoadingHasFinished() {
        assertTrue(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = false),
            InstrumentsUiState(isLoading = false),
        ))
        assertFalse(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = true),
            InstrumentsUiState(isLoading = false),
        ))
        assertFalse(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = false),
            InstrumentsUiState(isLoading = true),
        ))
    }

    @Test
    fun archivedAccountsAndCardsDoNotGetMistakenForFirstUse() {
        assertFalse(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = false),
            InstrumentsUiState(isLoading = false, archivedAccounts = listOf(account())),
        ))
        assertFalse(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = false),
            InstrumentsUiState(isLoading = false, archivedCards = listOf(card())),
        ))
    }

    @Test
    fun accountOrCreditCardDataKeepsDashboardOutOfFirstUseState() {
        val emptyInstruments = InstrumentsUiState(isLoading = false)
        assertFalse(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = false, dashboardData = dashboardDataWithAccount()),
            emptyInstruments,
        ))
        assertFalse(shouldShowEmptyDashboard(
            DashboardUiState(isLoading = false, dashboardData = dashboardDataWithCard()),
            emptyInstruments,
        ))
    }

    private fun account() = Account(
        id = AccountId("f1000000-0000-4000-8000-000000000001"),
        userId = UserId("f1000000-0000-4000-8000-000000000002"),
        alias = "Efectivo archivado",
        type = AccountType.CASH,
        currency = Currency.PEN,
        preset = AccountPreset.CASH,
        initialBalance = Money(0L, Currency.PEN),
        openedAt = Instant.parse("2025-01-01T00:00:00Z"),
        isArchived = true,
    )

    private fun card() = CreditCard(
        id = CardId("f1000000-0000-4000-8000-000000000003"),
        userId = UserId("f1000000-0000-4000-8000-000000000002"),
        issuer = "Banco",
        network = CardNetwork.VISA,
        lastFourDigits = "1234",
        currency = Currency.PEN,
        creditLimitMinorUnits = 100_000L,
        billingDay = 10,
        dueDay = 20,
        isArchived = true,
    )

    private fun dashboardDataWithAccount() = com.kipu.app.feature.accounts.domain.model.FinancialDashboardData(
        totalPen = Money(0L, Currency.PEN),
        totalUsd = Money(0L, Currency.USD),
        liquidAccounts = listOf(
            com.kipu.app.feature.accounts.domain.model.AccountWithBalance(
                account = account().copy(isArchived = false),
                balance = Money(0L, Currency.PEN),
            ),
        ),
        creditCards = emptyList(),
        activeComputableCount = 1,
    )

    private fun dashboardDataWithCard() = com.kipu.app.feature.accounts.domain.model.FinancialDashboardData(
        totalPen = Money(0L, Currency.PEN),
        totalUsd = Money(0L, Currency.USD),
        liquidAccounts = emptyList(),
        creditCards = listOf(
            com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary(
                card = card().copy(isArchived = false),
                debt = Money(0L, Currency.PEN),
                availableCredit = Money(100_000L, Currency.PEN),
                utilizationPercentage = 0.0,
            ),
        ),
        activeComputableCount = 1,
    )
}
