package com.kipu.app.feature.accounts.domain

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
import com.kipu.app.feature.accounts.domain.usecase.CreateLiquidAccount
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.accounts.domain.usecase.QuotaExceededException
import com.kipu.app.feature.accounts.domain.usecase.ReactivateInstrument
import com.kipu.app.feature.accounts.domain.usecase.RegisterCreditCard
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MutableVerifiedPlanAccess(premium: Boolean) : VerifiedPlanAccess {
    val premium = MutableStateFlow(premium)
    override suspend fun isPremiumVerified(ownerId: String?): Boolean = premium.value
    override fun observePremiumVerified() = premium
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanAwareInstrumentQuotaTest {
    private val owner = UserId.generate()
    private fun account() = Account(
        id = AccountId.generate(), userId = owner, alias = "Bank", type = AccountType.SAVINGS,
        currency = Currency.PEN, preset = AccountPreset.BCP, initialBalance = Money(0, Currency.PEN),
        openedAt = Instant.EPOCH,
    )
    private fun card() = CreditCard(
        id = CardId.generate(), userId = owner, issuer = "BCP", network = CardNetwork.VISA,
        lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 100_000,
        billingDay = 15, dueDay = 5,
    )

    @Test
    fun `verified premium can create and reactivate above four while free is rejected`() = runTest {
        for (premium in listOf(false, true)) {
            val repo = FakeFinancialInstrumentsRepository().apply { activeComputableCount = 4 }
            val access = MutableVerifiedPlanAccess(premium)
            val create = CreateLiquidAccount(repo, access)(account())
            val register = RegisterCreditCard(repo, access)(card())
            val reactivate = ReactivateInstrument(repo, access)("archived", false, true)
            for (result in listOf(create, register, reactivate)) {
                assertEquals(premium, result.isSuccess)
                if (!premium) assertTrue(result.exceptionOrNull() is QuotaExceededException)
            }
            assertEquals(if (premium) 1 else 0, repo.createdAccounts.size)
            assertEquals(if (premium) listOf("archived") else emptyList<String>(), repo.reactivatedInstruments)
        }
    }

    @Test
    fun `downgrade restores quota for subsequent commands`() = runTest {
        val repo = FakeFinancialInstrumentsRepository().apply { activeComputableCount = 4 }
        val access = MutableVerifiedPlanAccess(true)
        val create = CreateLiquidAccount(repo, access)
        assertTrue(create(account()).isSuccess)
        access.premium.value = false
        assertTrue(create(account()).exceptionOrNull() is QuotaExceededException)
        assertTrue(RegisterCreditCard(repo, access)(card()).exceptionOrNull() is QuotaExceededException)
        assertTrue(ReactivateInstrument(repo, access)("archived", true, true).exceptionOrNull() is QuotaExceededException)
    }

    @Test
    fun `dashboard and instrument quota update when verified plan changes without instrument writes`() = runTest {
        for (hasAccounts in listOf(false, true)) {
            val repo = FakeFinancialInstrumentsRepository().apply {
                activeComputableCount = 4
                if (hasAccounts) createdAccounts += account()
            }
            val access = MutableVerifiedPlanAccess(false)
            val quotas = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ObserveFinancialDashboard(repo, access)().collect { quotas += it.maxFreeQuota }
            }
            testScheduler.runCurrent()
            assertEquals(4, quotas.last())
            access.premium.value = true
            testScheduler.runCurrent()
            assertEquals(0, quotas.last())
            assertEquals(0, ObserveInstruments(repo, access).observeMaxQuota().first())
            access.premium.value = false
            testScheduler.runCurrent()
            assertEquals(4, quotas.last())
            assertEquals(4, ObserveInstruments(repo, access).observeMaxQuota().first())
        }
    }
}
