package com.kipu.app.feature.budgets.presentation

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetCategoryLookup
import com.kipu.app.feature.budgets.domain.BudgetConsumptionReader
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetPlanAccess
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus
import com.kipu.app.feature.budgets.domain.BudgetValidator
import com.kipu.app.feature.budgets.domain.BudgetWriteResult
import com.kipu.app.feature.budgets.domain.CreateBudget
import com.kipu.app.feature.budgets.domain.DeleteBudget
import com.kipu.app.feature.budgets.domain.EditBudget
import com.kipu.app.feature.budgets.domain.FinishBudget
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import com.kipu.app.feature.budgets.domain.RenewBudgetPeriods
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

const val TEST_OWNER = "owner-1"
val TEST_TODAY: LocalDate = LocalDate.of(2026, 10, 14)
val TEST_ZONE: ZoneId = ZoneId.of("America/Lima")

fun fakeSession(userId: String? = TEST_OWNER): SessionCoordinator {
    val session = mockk<SessionCoordinator>()
    every { session.localAccess } returns MutableStateFlow(
        if (userId == null) LocalAccess.NoOwner else LocalAccess.Available(userId, RemoteSession.Absent),
    )
    return session
}

class FakeBudgetRepository : BudgetRepository {
    val budgets = MutableStateFlow<List<Budget>>(emptyList())
    val periods = MutableStateFlow<List<BudgetPeriod>>(emptyList())
    var failObserve: Boolean = false
    var deleteCalls = 0

    override fun observeBudgets(ownerId: String): Flow<List<Budget>> =
        budgets.map { if (failObserve) error("boom") else it.filter { b -> b.ownerId == ownerId } }

    override fun observePeriods(ownerId: String, budgetId: String): Flow<List<BudgetPeriod>> =
        periods.map { list -> list.filter { it.budgetId == budgetId } }

    override suspend fun getBudget(ownerId: String, budgetId: String): Budget? =
        budgets.value.firstOrNull { it.id == budgetId && it.ownerId == ownerId }

    override suspend fun listBudgets(ownerId: String): List<Budget> = budgets.value.filter { it.ownerId == ownerId }

    override suspend fun listPeriods(ownerId: String, budgetId: String): List<BudgetPeriod> =
        periods.value.filter { it.budgetId == budgetId }.sortedBy { it.interval.start }

    override suspend fun create(budget: Budget, firstPeriod: BudgetPeriod): BudgetWriteResult {
        if (budgets.value.any { it.id == budget.id }) return BudgetWriteResult.ALREADY_EXISTS
        budgets.value = budgets.value + budget
        periods.value = periods.value + firstPeriod
        return BudgetWriteResult.OK
    }

    override suspend fun update(budget: Budget, expectedRevision: Long): BudgetWriteResult {
        val stored = budgets.value.firstOrNull { it.id == budget.id } ?: return BudgetWriteResult.NOT_FOUND
        if (stored.revision != expectedRevision) return BudgetWriteResult.REVISION_CONFLICT
        budgets.value = budgets.value.map { if (it.id == budget.id) budget else it }
        return BudgetWriteResult.OK
    }

    override suspend fun applyRenewal(
        ownerId: String,
        budget: Budget?,
        expectedRevision: Long,
        newPeriods: List<BudgetPeriod>,
    ): BudgetWriteResult {
        if (budget != null) {
            val result = update(budget, expectedRevision)
            if (result != BudgetWriteResult.OK) return result
        }
        periods.value = periods.value + newPeriods.filter { n -> periods.value.none { it.id == n.id } }
        return BudgetWriteResult.OK
    }

    override suspend fun updatePeriodLimit(ownerId: String, periodId: String, limitMinor: Long): BudgetWriteResult {
        periods.value = periods.value.map { if (it.id == periodId) it.copy(limitMinor = limitMinor) else it }
        return BudgetWriteResult.OK
    }

    override suspend fun delete(ownerId: String, budgetId: String): BudgetWriteResult {
        deleteCalls++
        if (budgets.value.none { it.id == budgetId }) return BudgetWriteResult.NOT_FOUND
        budgets.value = budgets.value.filterNot { it.id == budgetId }
        periods.value = periods.value.filterNot { it.budgetId == budgetId }
        return BudgetWriteResult.OK
    }
}

class FakeCategories(private val available: Set<String> = setOf("cat-food", "cat-food-out")) : BudgetCategoryLookup {
    override suspend fun isAvailable(ownerId: String, categoryId: String): Boolean = categoryId in available
    override suspend fun idsIncludingDescendants(ownerId: String, categoryId: String): Set<String> = setOf(categoryId)
}

class FakeCategoryOptions(
    val options: MutableStateFlow<List<BudgetCategoryOption>> = MutableStateFlow(
        listOf(
            BudgetCategoryOption("cat-food", "Comida", null, null),
            BudgetCategoryOption("cat-food-out", "Restaurantes", "cat-food", "Comida"),
        ),
    ),
) : BudgetCategoryOptionsSource {
    override fun observe(ownerId: String): Flow<List<BudgetCategoryOption>> = options
}

class FakePlanAccess(var premium: Boolean = false) : BudgetPlanAccess {
    override suspend fun isPremiumVerified(ownerId: String): Boolean = premium
}

class FakeReader(var consumed: Long = 12_000L) : BudgetConsumptionReader {
    override suspend fun consumedMinor(
        ownerId: String,
        scope: BudgetScope,
        currency: String,
        interval: BudgetInterval,
        zone: ZoneId,
    ): Long = consumed
}

fun sampleBudget(
    id: String = "b1",
    name: String = "Comida del mes",
    limit: Long = 50_000L,
    scope: BudgetScope = BudgetScope.Total,
    schedule: BudgetSchedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
    currency: String = "PEN",
    status: BudgetStatus = BudgetStatus.ACTIVE,
) = Budget(
    id = id, ownerId = TEST_OWNER, name = name, scope = scope, limitMinor = limit, currency = currency,
    schedule = schedule, status = status, createdOn = TEST_TODAY,
)

fun samplePeriod(
    budget: Budget,
    start: LocalDate = LocalDate.of(2026, 10, 1),
    end: LocalDate = LocalDate.of(2026, 11, 1),
) = BudgetPeriod(
    id = BudgetPeriod.idFor(budget.id, BudgetInterval(start, end)),
    budgetId = budget.id, ownerId = budget.ownerId, interval = BudgetInterval(start, end),
    limitMinor = budget.limitMinor, scope = budget.scope,
)

/** Casos de uso reales del dominio sobre fakes de puertos. */
class BudgetTestKit(
    val repository: FakeBudgetRepository = FakeBudgetRepository(),
    val reader: FakeReader = FakeReader(),
    val plan: FakePlanAccess = FakePlanAccess(),
    val categories: FakeCategories = FakeCategories(),
) {
    val validator = BudgetValidator(categories)
    val renew = RenewBudgetPeriods(repository)
    val getConsumption = GetBudgetConsumption(repository, reader)
    val create = CreateBudget(repository, validator, plan)
    val edit = EditBudget(repository, validator, renew)
    val finish = FinishBudget(repository)
    val delete = DeleteBudget(repository)

    fun seed(budget: Budget, vararg extraPeriods: BudgetPeriod) {
        repository.budgets.value = repository.budgets.value + budget
        repository.periods.value = repository.periods.value + samplePeriod(budget) + extraPeriods
    }
}
