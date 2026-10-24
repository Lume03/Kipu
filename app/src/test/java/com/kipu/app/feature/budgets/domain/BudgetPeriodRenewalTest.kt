package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPeriodRenewalTest {
    private fun periods(env: Env, id: String = "b1") = runBlocking { env.repo.listPeriods(OWNER, id) }
    private fun success(o: BudgetOutcome<List<BudgetPeriod>>) = (o as BudgetOutcome.Success).value

    @Test fun septemberToOctoberKeepsClosedLimitSnapshot() = runBlocking {
        val env = Env()
        env.createBudget(limit = 50_000, today = "2026-09-10")
        success(env.renew(OWNER, "b1", day("2026-10-01")))
        val ps = periods(env)
        assertEquals(2, ps.size)
        assertEquals(day("2026-09-01"), ps[0].interval.start)
        assertEquals(day("2026-10-01"), ps[1].interval.start)
        assertEquals(50_000L, ps[0].limitMinor)
        assertEquals(50_000L, ps[1].limitMinor)
    }

    @Test fun retryingNTimesYieldsSinglePeriod() = runBlocking {
        val env = Env()
        env.createBudget(today = "2026-09-10")
        val first = success(env.renew(OWNER, "b1", day("2026-10-01")))
        assertEquals(1, first.size)
        repeat(5) { assertTrue(success(env.renew(OWNER, "b1", day("2026-10-01"))).isEmpty()) }
        assertEquals(2, periods(env).size)
        assertEquals(periods(env).map { it.id }.toSet().size, periods(env).size)
    }

    @Test fun repositoryInsertIsIdempotentEvenIfCalledWithSamePeriods() = runBlocking {
        val env = Env()
        env.createBudget(today = "2026-09-10")
        val created = success(env.renew(OWNER, "b1", day("2026-10-05")))
        env.repo.applyRenewal(OWNER, null, 1, created)
        env.repo.applyRenewal(OWNER, null, 1, created)
        assertEquals(2, periods(env).size)
    }

    @Test fun lateOpenCatchesUpEveryMissingMonth() = runBlocking {
        val env = Env()
        env.createBudget(today = "2026-07-15")
        val created = success(env.renew(OWNER, "b1", day("2026-10-24")))
        assertEquals(3, created.size)
        assertEquals(4, periods(env).size)
    }

    @Test fun dailyWeeklyAndBiweeklyRenew() = runBlocking {
        val env = Env(premium = true)
        env.createBudget("d", schedule = BudgetSchedule.Recurring(BudgetPeriodType.DAILY), today = "2026-10-20")
        env.createBudget("w", schedule = BudgetSchedule.Recurring(BudgetPeriodType.WEEKLY), today = "2026-10-20")
        env.createBudget("q", schedule = BudgetSchedule.Recurring(BudgetPeriodType.BIWEEKLY), today = "2026-10-10")
        env.renew.renewAll(OWNER, day("2026-10-24"))
        assertEquals(5, periods(env, "d").size) // 20..24
        assertEquals(1, periods(env, "w").size) // semana 19-25
        assertEquals(listOf(day("2026-10-01"), day("2026-10-16")), periods(env, "q").map { it.interval.start })
    }

    @Test fun customRangeNeverRenews() = runBlocking {
        val env = Env()
        env.createBudget(schedule = BudgetSchedule.CustomRange(day("2026-10-01"), day("2026-10-15")), today = "2026-10-05")
        assertTrue(success(env.renew(OWNER, "b1", day("2026-12-01"))).isEmpty())
        val ps = periods(env)
        assertEquals(1, ps.size)
        assertEquals(day("2026-10-16"), ps[0].interval.endExclusive)
    }

    @Test fun finishedBudgetDoesNotRenew() = runBlocking {
        val env = Env()
        env.createBudget(today = "2026-09-10")
        env.finish(OWNER, "b1")
        assertTrue(success(env.renew(OWNER, "b1", day("2026-12-01"))).isEmpty())
        assertEquals(1, periods(env).size)
    }

    @Test fun missingBudgetIsRejected() = runBlocking {
        val r = Env().renew(OWNER, "nope", day("2026-10-01"))
        assertEquals(BudgetOutcome.Rejected(BudgetError.BudgetNotFound), r)
    }
}
