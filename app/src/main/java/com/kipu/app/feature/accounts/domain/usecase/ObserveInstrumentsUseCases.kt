package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import com.kipu.app.feature.plans.domain.FreePlanAccess
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveInstruments @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
    private val planAccess: VerifiedPlanAccess = FreePlanAccess,
) {
    fun observeAccounts(activeOnly: Boolean = false): Flow<List<Account>> =
        repository.observeAccounts(activeOnly)

    fun observeCards(activeOnly: Boolean = false): Flow<List<Card>> =
        repository.observeCards(activeOnly)

    fun observeMaxQuota(): Flow<Int> = planAccess.observePremiumVerified().map { premium ->
        if (premium) 0 else FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS
    }

    fun observeActiveComputableCount(): Flow<Int> =
        repository.observeActiveComputableCount()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFinancialDashboard @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
    private val planAccess: VerifiedPlanAccess = FreePlanAccess,
) {
    operator fun invoke(): Flow<FinancialDashboardData> {
        return combine(
            repository.observeAccounts(activeOnly = false),
            repository.observeCards(activeOnly = false),
            repository.observeActiveComputableCount(),
            planAccess.observePremiumVerified(),
        ) { accounts, cards, computableCount, premium ->
            Triple(accounts, cards, computableCount) to premium
        }.flatMapLatest { (instruments, premium) ->
            val (accounts, cards, computableCount) = instruments
            val maxQuota = if (premium) 0 else FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS
            if (accounts.isEmpty() && cards.isEmpty()) {
                flowOf(
                    FinancialDashboardData(
                        totalPen = Money(0L, Currency.PEN),
                        totalUsd = Money(0L, Currency.USD),
                        liquidAccounts = emptyList(),
                        creditCards = emptyList(),
                        activeComputableCount = computableCount,
                        maxFreeQuota = maxQuota,
                    )
                )
            } else {
                val accountBalancesFlows = accounts.map { account ->
                    repository.observeAccountBalance(account.id)
                }

                val creditCards = cards.filterIsInstance<CreditCard>()
                val creditDebtsFlows = creditCards.map { card ->
                    repository.observeCardDebt(card.id)
                }

                val debitCards = cards.filterIsInstance<DebitCard>()

                val combinedBalancesFlow = if (accountBalancesFlows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(accountBalancesFlows) { it.toList() }
                }

                val combinedDebtsFlow = if (creditDebtsFlows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(creditDebtsFlows) { it.toList() }
                }

                combine(combinedBalancesFlow, combinedDebtsFlow) { balances, debts ->
                    var totalPenMinor = 0L
                    var totalUsdMinor = 0L

                    val accountsWithBalance = accounts.mapIndexed { index, account ->
                        val balance = if (account.linkedAccountId != null) {
                            val parentIndex = accounts.indexOfFirst { it.id == account.linkedAccountId }
                            balances.getOrElse(parentIndex) { Money(0L, account.currency) }
                        } else balances.getOrElse(index) { Money(0L, account.currency) }
                        if (account.linkedAccountId == null) {
                            if (account.currency == Currency.PEN) {
                                totalPenMinor = Math.addExact(totalPenMinor, balance.minorUnits)
                            } else {
                                totalUsdMinor = Math.addExact(totalUsdMinor, balance.minorUnits)
                            }
                        }

                        val linkedCards = debitCards.filter { it.linkedAccountId == account.id }
                        AccountWithBalance(
                            account = account,
                            balance = balance,
                            linkedDebitCards = linkedCards,
                        )
                    }

                    val creditCardsWithSummary = creditCards.mapIndexed { index, card ->
                        val debt = debts.getOrElse(index) { Money(0L, card.currency) }
                        val availableMinor = Math.max(0L, card.creditLimitMinorUnits - debt.minorUnits)
                        val utilization = if (card.creditLimitMinorUnits > 0L) {
                            (debt.minorUnits.toDouble() / card.creditLimitMinorUnits.toDouble()) * 100.0
                        } else {
                            0.0
                        }
                        CreditCardWithSummary(
                            card = card,
                            debt = debt,
                            availableCredit = Money(availableMinor, card.currency),
                            utilizationPercentage = utilization,
                        )
                    }

                    // Per data-model.md:L353 and quickstart.md:L116,L219:
                    // Archived accounts are hidden from default instrument list, but their balances remain in totalPen/totalUsd.
                    // Archived credit cards remain visible in summaries if they have nonzero debt so they can be settled.
                    val displayLiquidAccounts = accountsWithBalance.filter { !it.account.isArchived }
                    val displayCreditCards = creditCardsWithSummary.filter { !it.card.isArchived || it.debt.minorUnits > 0L }

                    FinancialDashboardData(
                        totalPen = Money(totalPenMinor, Currency.PEN),
                        totalUsd = Money(totalUsdMinor, Currency.USD),
                        liquidAccounts = displayLiquidAccounts,
                        creditCards = displayCreditCards,
                        activeComputableCount = computableCount,
                        maxFreeQuota = maxQuota,
                    )
                }
            }
        }
    }
}
