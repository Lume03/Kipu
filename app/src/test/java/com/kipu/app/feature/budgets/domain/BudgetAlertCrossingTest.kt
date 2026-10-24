package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertCrossingTest {
    private val today = day("2026-10-10")

    private fun evaluate(env: Env) = runBlocking { env.alerts(OWNER, "b1", today, TEST_ZONE) }

    @Test fun crossingFrom49To51EmitsFiftyOnce() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-10-01")
        env.expense("transporte", "2026-10-02", 4_900)
        assertTrue(evaluate(env).isEmpty())
        env.expense("transporte", "2026-10-03", 200)
        assertEquals(listOf(5000), evaluate(env).map { it.key.thresholdBps })
        assertTrue(evaluate(env).isEmpty())
        assertTrue(evaluate(env).isEmpty())
        assertEquals(1, env.notifier.sent.size)
    }

    @Test fun eachDefaultThresholdFiresOnceAndExceedingEmitsNothingMore() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-10-01")
        env.expense("transporte", "2026-10-02", 8_000)
        assertEquals(listOf(5000, 8000), evaluate(env).map { it.key.thresholdBps })
        env.expense("transporte", "2026-10-03", 3_000)
        assertEquals(listOf(10000), evaluate(env).map { it.key.thresholdBps })
        env.expense("transporte", "2026-10-04", 5_000)
        assertTrue(evaluate(env).isEmpty())
        assertEquals(3, env.notifier.sent.size)
    }

    @Test fun exactLimitReachesHundred() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-10-01")
        env.expense("transporte", "2026-10-02", 10_000)
        assertEquals(listOf(5000, 8000, 10000), evaluate(env).map { it.key.thresholdBps })
    }

    @Test fun newPeriodResetsAlertsForSameThreshold() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-09-01", thresholds = listOf(5000))
        env.expense("transporte", "2026-09-05", 6_000)
        runBlocking { env.alerts(OWNER, "b1", day("2026-09-20"), TEST_ZONE) }
        runBlocking { env.renew(OWNER, "b1", day("2026-10-02")) }
        env.expense("transporte", "2026-10-02", 6_000)
        assertEquals(1, evaluate(env).size)
        assertEquals(2, env.notifier.sent.size)
        assertEquals(2, env.notifier.sent.map { it.key.dedupeKey }.toSet().size)
    }

    @Test fun customThresholdsAreHonoured() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-10-01", thresholds = listOf(2500))
        env.expense("transporte", "2026-10-02", 2_600)
        assertEquals(listOf(2500), evaluate(env).map { it.key.thresholdBps })
    }

    @Test fun ledgerRejectingKeyPreventsNotification() {
        val env = Env()
        env.createBudget(limit = 10_000, today = "2026-10-01")
        env.expense("transporte", "2026-10-02", 6_000)
        val period = runBlocking { env.repo.listPeriods(OWNER, "b1").first() }
        // Otro dispositivo gano la carrera de registro entre la lectura y tryRecord.
        val racing = object : BudgetAlertLedger {
            override suspend fun emittedThresholds(ownerId: String, budgetId: String, periodId: String) = emptySet<Int>()
            override suspend fun tryRecord(key: BudgetAlertKey) = false
        }
        val alerts = EvaluateBudgetAlerts(env.repo, env.reader, racing, env.notifier)
        assertTrue(runBlocking { alerts(OWNER, "b1", today, TEST_ZONE) }.isEmpty())
        assertTrue(env.notifier.sent.isEmpty())
        assertEquals("b1:2026-10-01:2026-11-01", period.id)
    }

    @Test fun detectorIsPure() {
        val c = BudgetConsumption(10_000, 8_000)
        assertEquals(listOf(8000), BudgetAlertDetector.crossings(listOf(5000, 8000, 10000), c, setOf(5000)))
        assertEquals(emptyList<Int>(), BudgetAlertDetector.crossings(emptyList(), c, emptySet()))
    }
}
