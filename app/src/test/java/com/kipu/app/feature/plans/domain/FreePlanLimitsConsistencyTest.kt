package com.kipu.app.feature.plans.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.FakeFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.domain.usecase.CreateLiquidAccount
import com.kipu.app.feature.accounts.domain.usecase.QuotaExceededException
import com.kipu.app.feature.budgets.domain.BudgetError
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.Env
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.presentation.DebtListUiState
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.presentation.categories.CategoriesUiState
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.goals.data.OfflineFirstGoalRepository
import com.kipu.app.feature.goals.data.local.GoalLocalDataSource
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.presentation.GoalManagementUiState
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreePlanLimitsConsistencyTest {
    private val limits = FreePlanLimits()

    @Test
    fun displayedQuotasMatchEveryEnforcementAndPresentationDefault() {
        assertEquals(limits.instruments, FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS)
        val dashboard = FinancialDashboardData(
            Money(0, Currency.PEN), Money(0, Currency.USD), emptyList(), emptyList(), 0,
        )
        assertEquals(limits.instruments, dashboard.maxFreeQuota)
        assertEquals(limits.customCategories, CategoryRules.MAX_FREE_ACTIVE_CUSTOM_ROOTS)
        assertEquals(limits.customCategories, CategoryRules.MAX_FREE_ACTIVE_CUSTOM_EXPENSE_ROOTS)
        assertEquals(limits.customCategories, CategoryRules.MAX_FREE_ACTIVE_CUSTOM_INCOME_ROOTS)
        assertEquals(limits.customCategories, CategoriesUiState().maxCustomRoots)
        assertEquals(limits.debts, DebtLocalDataSource.FREE_ACTIVE_DEBT_LIMIT)
        assertEquals(limits.debts, DebtListUiState.FREE_ACTIVE_DEBT_LIMIT)
        assertEquals(limits.goals, GoalManagementUiState.FREE_GOAL_LIMIT)

        val expected = mapOf(
            QuotaGroup.INSTRUMENTS to limits.instruments,
            QuotaGroup.CUSTOM_CATEGORIES to limits.customCategories,
            QuotaGroup.DEBTS to limits.debts,
            QuotaGroup.GOALS to limits.goals,
            QuotaGroup.BUDGETS to limits.budgets,
        )
        expected.forEach { (group, limit) ->
            assertEquals(limit, PlanQuotaPolicy().evaluate(group, emptyList(), emptyList()).limit)
        }
    }

    @Test
    fun accountsAcceptExactlyTheAdvertisedComputableQuota() = runTest {
        val repository = FakeFinancialInstrumentsRepository()
        val create = CreateLiquidAccount(repository)
        val owner = UserId.generate()
        repeat(limits.instruments) {
            val account = Account(
                id = AccountId.generate(), userId = owner, alias = "Cuenta $it",
                type = AccountType.SAVINGS, currency = Currency.PEN, preset = AccountPreset.BCP,
                initialBalance = Money(0, Currency.PEN), openedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
            assertTrue(create(account).isSuccess)
        }
        assertTrue(create(repository.createdAccounts.first().copy(id = AccountId.generate())).exceptionOrNull() is QuotaExceededException)
        assertEquals(limits.instruments, repository.createdAccounts.size)
    }

    @Test
    fun categoriesEnforceTheAdvertisedQuotaIndependentlyForEachType() {
        val owner = UserId.generate()
        listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.GENERAL).forEach { type ->
            val categories = List(limits.customCategories) {
                Category(CategoryId.generate(), owner, null, CategoryOrigin.CUSTOM, true, categoryType = type)
            }
            assertTrue(CategoryRules.canActivateCustomRoot(type, categories.dropLast(1), isPremium = false))
            assertFalse(CategoryRules.canActivateCustomRoot(type, categories, isPremium = false))
            assertTrue(CategoryRules.canActivateCustomRoot(type, categories, isPremium = true))
        }
    }

    @Test
    fun goalsEnforceTheAdvertisedQuotaBeforeStagingWrites() = runTest {
        val local = mockk<GoalLocalDataSource>()
        val repository = OfflineFirstGoalRepository(local, mockk<GoalsSyncScheduler>(relaxed = true))
        val owner = UserId.generate().toString()
        coEvery { local.countOperationalGoals(owner) } returns limits.goals - 1
        coEvery { local.stageCommand(owner, any(), any(), any(), any()) } coAnswers {
            GoalCommandResult.Applied(secondArg<GoalCommandEnvelope>().goalId, 1)
        }
        assertTrue(repository.createGoal(owner, "Meta", Money(10_000, Currency.PEN), null, null, false, null) is GoalCommandResult.Applied)

        coEvery { local.countOperationalGoals(owner) } returns limits.goals
        assertEquals(
            GoalCommandResult.Rejected("GOAL_LIMIT"),
            repository.createGoal(owner, "Meta adicional", Money(10_000, Currency.PEN), null, null, false, null),
        )
        coVerify(exactly = 1) { local.stageCommand(owner, any(), any(), any(), any()) }
    }

    @Test
    fun budgetsAcceptExactlyTheAdvertisedActiveQuota() {
        val environment = Env()
        repeat(limits.budgets) { environment.createBudget(id = "budget-$it") }
        assertEquals(
            BudgetOutcome.Rejected(BudgetError.FreeQuotaExceeded(limits.budgets)),
            kotlinx.coroutines.runBlocking {
                environment.create(com.kipu.app.feature.budgets.domain.CreateBudgetCommand(
                    "extra", com.kipu.app.feature.budgets.domain.OWNER, "Adicional",
                    com.kipu.app.feature.budgets.domain.BudgetScope.Total, 10_000, "PEN",
                    com.kipu.app.feature.budgets.domain.BudgetSchedule.Recurring(com.kipu.app.feature.budgets.domain.BudgetPeriodType.MONTHLY),
                    today = java.time.LocalDate.parse("2026-10-01"),
                ))
            },
        )
    }
}
