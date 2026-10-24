package com.kipu.app.feature.home.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.FakeFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveNetWorthSummaryTest {
    private val user = UserId.generate()
    private val accountId = AccountId.generate()

    private fun account(currency: Currency = Currency.PEN) = Account(
        id = accountId, userId = user, alias = "BCP", type = AccountType.SAVINGS, currency = currency,
        initialBalance = Money(0L, currency), openedAt = Instant.now(),
    )

    private fun debt(type: DebtObligationType, remaining: Long, status: DebtLifecycleStatus = DebtLifecycleStatus.ACTIVE) =
        DebtSummary(
            debtId = "d-$type-$remaining", userId = user.value, obligationType = type, counterpartyName = "X",
            principalMinor = remaining, remainingPrincipalMinor = remaining, currencyCode = "PEN",
            openedOn = LocalDate.of(2026, 10, 1), dueDate = null, reminderLeadDays = null, notes = null,
            status = status, syncState = "SYNCED", revision = 1L,
        )

    private fun debtRepository(debts: List<DebtSummary>): DebtRepository =
        Proxy.newProxyInstance(DebtRepository::class.java.classLoader, arrayOf(DebtRepository::class.java)) { _, method, _ ->
            if (method.name == "observeDebts") flowOf(debts) else error("No usado en la prueba: ${method.name}")
        } as DebtRepository

    private fun useCase(
        accounts: List<Account>,
        cards: List<Card> = emptyList(),
        balance: Long = 0L,
        cardDebt: Long = 0L,
        debts: List<DebtSummary> = emptyList(),
    ) = ObserveNetWorthSummary(
        ObserveFinancialDashboard(
            object : FakeFinancialInstrumentsRepository() {
                override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> = flowOf(accounts)
                override fun observeCards(activeOnly: Boolean): Flow<List<Card>> = flowOf(cards)
                override fun observeAccountBalance(accountId: AccountId): Flow<Money> = flowOf(Money(balance, Currency.PEN))
                override fun observeCardDebt(cardId: CardId): Flow<Money> = flowOf(Money(cardDebt, Currency.PEN))
            },
        ),
        debtRepository(debts),
    )

    private fun creditCard() = CreditCard(
        id = CardId.generate(), userId = user, issuer = "BCP", network = CardNetwork.VISA, lastFourDigits = "1111",
        currency = Currency.PEN, creditLimitMinorUnits = 500_000L, billingDay = 15, dueDay = 5,
    )

    @Test
    fun `escenario S1000 mas S200 menos S300 da S900 y la linea de credito no suma`() = runTest {
        // Tarjeta con S/5000 de límite y S/0 de deuda: el crédito disponible no es activo.
        val summary = useCase(
            accounts = listOf(account()),
            cards = listOf(creditCard()),
            balance = 100_000L,
            debts = listOf(
                debt(DebtObligationType.RECEIVABLE, 20_000L),
                debt(DebtObligationType.PAYABLE, 30_000L),
            ),
        )(user.value).first() as NetWorthSummary.Available

        assertEquals(Money(90_000L, Currency.PEN), summary.byCurrency.single().netWorth)
    }

    @Test
    fun `deudas liquidadas o canceladas no cuentan`() = runTest {
        val summary = useCase(
            accounts = listOf(account()),
            balance = 100_000L,
            debts = listOf(
                debt(DebtObligationType.PAYABLE, 30_000L, DebtLifecycleStatus.SETTLED),
                debt(DebtObligationType.RECEIVABLE, 20_000L, DebtLifecycleStatus.CANCELLED),
            ),
        )(user.value).first() as NetWorthSummary.Available

        assertEquals(Money(100_000L, Currency.PEN), summary.byCurrency.single().netWorth)
    }

    @Test
    fun `la deuda vigente de tarjeta se resta`() = runTest {
        val summary = useCase(
            accounts = listOf(account()), cards = listOf(creditCard()), balance = 100_000L, cardDebt = 25_000L,
        )(user.value).first() as NetWorthSummary.Available

        assertEquals(Money(75_000L, Currency.PEN), summary.byCurrency.single().netWorth)
    }

    @Test
    fun `sin cuentas tarjetas ni deudas es sin datos`() = runTest {
        assertTrue(useCase(accounts = emptyList())(user.value).first() is NetWorthSummary.NoData)
    }

    @Test
    fun `solo cuenta en PEN no inventa una fila USD en cero`() = runTest {
        val summary = useCase(accounts = listOf(account()), balance = 0L)(user.value).first() as NetWorthSummary.Available

        assertEquals(listOf(Currency.PEN), summary.byCurrency.map { it.currency })
    }
}
