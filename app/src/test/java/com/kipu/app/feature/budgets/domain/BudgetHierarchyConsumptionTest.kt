package com.kipu.app.feature.budgets.domain

import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetHierarchyConsumptionTest {
    private fun consumed(env: Env, scope: BudgetScope, id: String): Long {
        val budget = env.createBudget(id = id, scope = scope, limit = 100_000, today = "2026-10-03")
        return runBlocking {
            (env.consumption.current(OWNER, budget.id, day("2026-10-10"), TEST_ZONE) as BudgetOutcome.Success)
                .value.consumption.consumedMinor
        }
    }

    @Test fun fiftyInRestaurantsCountsOncePerScope() {
        val env = Env(premium = true)
        env.expense("restaurantes", "2026-10-05", 5_000)
        assertEquals(5_000L, consumed(env, BudgetScope.Category("restaurantes"), "r"))
        assertEquals(5_000L, consumed(env, BudgetScope.Category("alimentacion"), "a"))
        assertEquals(5_000L, consumed(env, BudgetScope.Total, "t"))
    }

    @Test fun parentIncludesSubcategoriesButSiblingsStayApart() {
        val env = Env(premium = true)
        env.expense("restaurantes", "2026-10-05", 5_000)
        env.expense("supermercado", "2026-10-06", 3_000)
        env.expense("alimentacion", "2026-10-07", 1_000)
        env.expense("transporte", "2026-10-08", 700)
        assertEquals(5_000L, consumed(env, BudgetScope.Category("restaurantes"), "r"))
        assertEquals(9_000L, consumed(env, BudgetScope.Category("alimentacion"), "a"))
        assertEquals(9_700L, consumed(env, BudgetScope.Total, "t"))
    }

    @Test fun readerPassesFullCategorySetAndNoFilterForTotal() = runBlocking {
        val seen = mutableListOf<ExpenseConsumptionQuery>()
        val fake = object : ExpenseConsumptionRepository {
            override suspend fun queryConsumption(userId: String, query: ExpenseConsumptionQuery): ExpenseConsumptionResult {
                seen += query
                return ExpenseConsumptionResult(-500L, 1L)
            }
        }
        val reader = ExpenseBudgetConsumptionReader(fake, FakeCategoryLookup())
        val interval = BudgetInterval(day("2026-10-01"), day("2026-11-01"))

        val parent = reader.consumedMinor(OWNER, BudgetScope.Category("alimentacion"), "PEN", interval, TEST_ZONE)
        reader.consumedMinor(OWNER, BudgetScope.Total, "PEN", interval, TEST_ZONE)

        assertEquals(setOf("alimentacion", "restaurantes", "supermercado"), seen[0].categoryIds)
        assertEquals(emptySet<String>(), seen[1].categoryIds)
        assertEquals("PEN", seen[0].currency)
        assertEquals(interval.fromInclusiveMillis(TEST_ZONE), seen[0].fromInclusive)
        assertEquals(interval.toExclusiveMillis(TEST_ZONE), seen[0].toExclusive)
        assertEquals(TEST_ZONE, seen[0].zoneId)
        assertEquals(0L, parent) // neto negativo se muestra 0
    }
}
