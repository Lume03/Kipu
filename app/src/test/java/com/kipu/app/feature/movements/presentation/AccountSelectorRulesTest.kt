package com.kipu.app.feature.movements.presentation

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
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSelectorRulesTest {
    private val user = UserId.generate()

    private fun account(
        alias: String,
        type: AccountType,
        preset: AccountPreset?,
        linkedAccountId: AccountId? = null,
    ) = Account(
        id = AccountId.generate(),
        userId = user,
        alias = alias,
        type = type,
        currency = Currency.PEN,
        preset = preset,
        initialBalance = Money(0, Currency.PEN),
        openedAt = Instant.parse("2026-10-01T00:00:00Z"),
        linkedAccountId = linkedAccountId,
    )

    @Test
    fun incomeAndTransferSourceNeverOfferCreditCards() {
        assertFalse(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.INCOME_DESTINATION, callerAllows = true))
        assertFalse(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.TRANSFER_SOURCE, callerAllows = true))
    }

    @Test
    fun expenseTransferDestinationAndFilterOfferCardsWhenCallerHasThem() {
        assertTrue(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.EXPENSE_SOURCE, callerAllows = true))
        assertTrue(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.TRANSFER_DESTINATION, callerAllows = true))
        assertTrue(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.FILTER, callerAllows = true))
        assertFalse(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.GENERIC, callerAllows = false))
    }

    @Test
    fun copyFollowsTheFlow() {
        assertEquals("Seleccionar cuenta destino", AccountSelectorDefaults.title(AccountSelectorPurpose.INCOME_DESTINATION))
        assertEquals("Elige dónde recibir el ingreso", AccountSelectorDefaults.subtitle(AccountSelectorPurpose.INCOME_DESTINATION))
        assertEquals("Elige desde dónde sale el dinero", AccountSelectorDefaults.subtitle(AccountSelectorPurpose.TRANSFER_SOURCE))
        assertEquals("Elige a dónde llega el dinero", AccountSelectorDefaults.subtitle(AccountSelectorPurpose.TRANSFER_DESTINATION))
    }

    @Test
    fun affiliatedWalletSaysItSharesTheLinkedAccountBalance() {
        val bcp = account("Cuenta BCP", AccountType.SAVINGS, AccountPreset.BCP)
        val yape = account("Yape", AccountType.DIGITAL_WALLET, AccountPreset.YAPE, bcp.id)

        assertEquals("Yape · Comparte saldo con Cuenta BCP", formatAccountSubtitle(yape, bcp))
        assertEquals("Yape · Saldo compartido · PEN", formatAccountSubtitle(yape, linkedAccount = null))
    }

    @Test
    fun ownBalanceAccountsKeepInstitutionTypeAndCurrency() {
        assertEquals("BCP · Ahorros PEN", formatAccountSubtitle(account("Sueldo", AccountType.SAVINGS, AccountPreset.BCP)))
        assertEquals("Plin · Billetera digital PEN", formatAccountSubtitle(account("Plin", AccountType.DIGITAL_WALLET, AccountPreset.PLIN)))
        assertEquals("Billetera física · PEN", formatAccountSubtitle(account("Efectivo", AccountType.CASH, AccountPreset.CASH)))
    }

    @Test
    fun cardAsTransferDestinationIsACardPayment() {
        val card = CreditCard(
            id = CardId.generate(), userId = user, issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "4242", currency = Currency.PEN, creditLimitMinorUnits = 100_000,
            billingDay = 15, dueDay = 5,
        )
        assertEquals("Crédito · •••• 4242", formatCardSubtitle(card))
        assertEquals("Pago de tarjeta · •••• 4242", formatCardSubtitle(card, asPaymentTarget = true))
    }
}
