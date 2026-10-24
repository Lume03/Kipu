package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetMaintenanceTest {
    @Test
    fun maintenanceRenewsBeforeEvaluatingTheCurrentPeriod() = runBlocking {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-09-10", thresholds = listOf(5000))
        env.expense("transporte", "2026-10-02", 6_000)

        val result = RunBudgetMaintenance(env.repo, env.renew, env.alerts)(OWNER, day("2026-10-02"), TEST_ZONE)

        assertEquals(1, result.renewals["b1"]?.let { (it as BudgetOutcome.Success).value.size })
        assertEquals(listOf(6000L), result.alerts.map(BudgetAlert::percentBps))
        assertEquals(1, env.notifier.sent.size)
    }
}
