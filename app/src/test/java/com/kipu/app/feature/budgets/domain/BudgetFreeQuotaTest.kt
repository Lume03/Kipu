package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetFreeQuotaTest {
    private fun tryCreate(env: Env, id: String, name: String = "P $id", limit: Long = 10_000) = runBlocking {
        env.create(
            CreateBudgetCommand(
                id, OWNER, name, BudgetScope.Total, limit, "PEN",
                BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY), today = day("2026-10-01"),
            ),
        )
    }

    @Test fun thirdActiveBudgetIsRejectedAndExistingOnesKept() {
        val env = Env()
        assertTrue(tryCreate(env, "a") is BudgetOutcome.Success)
        assertTrue(tryCreate(env, "b") is BudgetOutcome.Success)
        assertEquals(BudgetOutcome.Rejected(BudgetError.FreeQuotaExceeded(2)), tryCreate(env, "c"))
        assertEquals(setOf("a", "b"), runBlocking { env.repo.listBudgets(OWNER) }.map { it.id }.toSet())
    }

    @Test fun finishingFreesASlot() {
        val env = Env()
        tryCreate(env, "a")
        tryCreate(env, "b")
        runBlocking { env.finish(OWNER, "a") }
        assertTrue(tryCreate(env, "c") is BudgetOutcome.Success)
        assertEquals(3, runBlocking { env.repo.listBudgets(OWNER) }.size) // el finalizado se conserva
    }

    @Test fun premiumIsNotLimitedToTwo() {
        val env = Env(premium = true)
        listOf("a", "b", "c", "d").forEach { assertTrue(tryCreate(env, it) is BudgetOutcome.Success) }
    }

    @Test fun duplicateIdIsRejected() {
        val env = Env(premium = true)
        tryCreate(env, "a")
        assertEquals(BudgetOutcome.Rejected(BudgetError.AlreadyExists), tryCreate(env, "a"))
    }

    @Test fun invalidInputIsRejectedBeforeQuota() {
        val env = Env()
        val r = tryCreate(env, "x", name = "", limit = 0) as BudgetOutcome.Rejected
        assertEquals(2, r.errors.size)
    }
}
