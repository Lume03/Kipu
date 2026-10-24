package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetLifecycleTest {
    private fun periods(env: Env) = runBlocking { env.repo.listPeriods(OWNER, "b1") }
    private fun budget(env: Env) = runBlocking { env.repo.getBudget(OWNER, "b1") }!!
    private fun edit(env: Env, today: String, c: (EditBudgetCommand) -> EditBudgetCommand) =
        runBlocking { env.edit(c(EditBudgetCommand(OWNER, "b1", day(today)))) }

    @Test fun editingLimitKeepsClosedSnapshotsAndAffectsCurrentAndFuture() {
        val env = Env()
        env.createBudget(limit = 50_000, today = "2026-09-10")
        runBlocking { env.renew(OWNER, "b1", day("2026-10-05")) }
        val r = edit(env, "2026-10-05") { it.copy(limitMinor = 80_000) }
        assertTrue(r is BudgetOutcome.Success)
        val ps = periods(env)
        assertEquals(50_000L, ps[0].limitMinor) // septiembre cerrado
        assertEquals(80_000L, ps[1].limitMinor) // octubre vigente
        runBlocking { env.renew(OWNER, "b1", day("2026-11-02")) }
        assertEquals(80_000L, periods(env)[2].limitMinor) // futuro
        assertEquals(50_000L, periods(env)[0].limitMinor)
    }

    @Test fun editRejectsInvalidValuesWithoutChanges() {
        val env = Env()
        env.createBudget()
        val r = edit(env, "2026-09-11") { it.copy(limitMinor = 0, thresholdsBps = listOf(5000, 5000)) }
        assertTrue(r is BudgetOutcome.Rejected)
        assertEquals(50_000L, budget(env).limitMinor)
    }

    @Test fun scopeChangeAppliesFromNextPeriodAndKeepsPastScope() {
        val env = Env()
        env.createBudget(scope = BudgetScope.Total, today = "2026-09-10")
        env.expense("transporte", "2026-09-12", 1_000)
        env.expense("transporte", "2026-10-12", 400)
        env.expense("restaurantes", "2026-10-13", 300)
        edit(env, "2026-09-15") { it.copy(scope = BudgetScope.Category("restaurantes")) }
        assertEquals(BudgetScope.Total, budget(env).scope)
        assertEquals(day("2026-10-01"), budget(env).pendingChange!!.effectiveFrom)

        runBlocking { env.renew(OWNER, "b1", day("2026-10-15")) }
        val ps = periods(env)
        assertEquals(BudgetScope.Total, ps[0].scope)
        assertEquals(BudgetScope.Category("restaurantes"), ps[1].scope)
        assertNull(budget(env).pendingChange)
        val sep = runBlocking { env.consumption.forPeriod("PEN", ps[0], TEST_ZONE) }
        val oct = runBlocking { env.consumption.forPeriod("PEN", ps[1], TEST_ZONE) }
        assertEquals(1_000L, sep.consumption.consumedMinor)
        assertEquals(300L, oct.consumption.consumedMinor)
    }

    @Test fun scheduleChangeStartsTruncatedPeriodAtBoundary() {
        val env = Env()
        env.createBudget(today = "2026-09-10") // mensual
        edit(env, "2026-09-12") { it.copy(schedule = BudgetSchedule.Recurring(BudgetPeriodType.BIWEEKLY)) }
        runBlocking { env.renew(OWNER, "b1", day("2026-10-20")) }
        val starts = periods(env).map { it.interval.start }
        assertEquals(listOf(day("2026-09-01"), day("2026-10-01"), day("2026-10-16")), starts)
        assertEquals(BudgetSchedule.Recurring(BudgetPeriodType.BIWEEKLY), budget(env).schedule)
        runBlocking { env.renew(OWNER, "b1", day("2026-10-20")) } // reintento no duplica
        assertEquals(3, periods(env).size)
    }

    @Test fun customRangeBeforeBoundaryIsRejected() {
        val env = Env()
        env.createBudget(today = "2026-09-10")
        val r = edit(env, "2026-09-12") {
            it.copy(schedule = BudgetSchedule.CustomRange(day("2026-09-20"), day("2026-10-20")))
        }
        assertEquals(BudgetOutcome.Rejected(BudgetError.RangeBeforeEffectiveDate), r)
    }

    @Test fun endedCustomRangeAppliesChangeImmediately() {
        val env = Env()
        env.createBudget(schedule = BudgetSchedule.CustomRange(day("2026-09-01"), day("2026-09-15")), today = "2026-09-02")
        val r = edit(env, "2026-10-05") { it.copy(schedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY)) }
        assertTrue(r is BudgetOutcome.Success)
        assertEquals(2, periods(env).size)
        assertEquals(day("2026-10-01"), periods(env).last().interval.start)
    }

    @Test fun finishStopsRenewalAndAlertsButKeepsHistory() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-09-10")
        env.expense("transporte", "2026-09-12", 9_000)
        runBlocking { env.finish(OWNER, "b1") }
        runBlocking { env.renew(OWNER, "b1", day("2026-11-05")) }
        assertEquals(1, periods(env).size)
        val alerts = runBlocking { env.alerts(OWNER, "b1", day("2026-09-20"), TEST_ZONE) }
        assertTrue(alerts.isEmpty())
        assertTrue(env.notifier.sent.isEmpty())
        val hist = runBlocking { env.consumption.current(OWNER, "b1", day("2026-11-05"), TEST_ZONE) } as BudgetOutcome.Success
        assertEquals(9_000L, hist.value.consumption.consumedMinor)
        assertEquals(BudgetStatus.FINISHED, budget(env).status)
    }

    @Test fun finishedBudgetCannotBeEdited() {
        val env = Env()
        env.createBudget()
        runBlocking { env.finish(OWNER, "b1") }
        assertEquals(BudgetOutcome.Rejected(BudgetError.BudgetFinished), edit(env, "2026-09-12") { it.copy(name = "x") })
    }

    @Test fun finishIsIdempotent() {
        val env = Env()
        env.createBudget()
        runBlocking {
            env.finish(OWNER, "b1")
            env.finish(OWNER, "b1")
        }
        assertEquals(2L, budget(env).revision)
    }

    @Test fun deleteRemovesBudgetAndPeriodsOnly() {
        val env = Env()
        env.createBudget()
        env.expense("transporte", "2026-09-12", 1_000)
        // cancelar la confirmacion = no invocar el caso de uso
        assertNotNull(runBlocking { env.repo.getBudget(OWNER, "b1") })
        assertTrue(runBlocking { env.delete(OWNER, "b1") } is BudgetOutcome.Success)
        assertNull(runBlocking { env.repo.getBudget(OWNER, "b1") })
        assertTrue(periods(env).isEmpty())
        assertEquals(1, env.reader.expenses.size) // los gastos no se tocan
        assertEquals(BudgetOutcome.Rejected(BudgetError.BudgetNotFound), runBlocking { env.delete(OWNER, "b1") })
    }

    @Test fun staleRevisionIsReportedAsConflict() {
        val env = Env()
        val b = env.createBudget()
        assertEquals(BudgetWriteResult.OK, runBlocking { env.repo.update(b.copy(revision = 2), 1) })
        assertEquals(BudgetWriteResult.REVISION_CONFLICT, runBlocking { env.repo.update(b.copy(revision = 2), 1) })
    }
}
