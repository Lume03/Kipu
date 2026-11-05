package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.QuotaGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanQuotaPolicyTest {
    private val policy = PlanQuotaPolicy()

    @Test
    fun goalDowngradeRequiresTwoSelectionsAndPremiumRenewalRestoresAllWithoutDeletingResources() {
        val goals = setOf("goal-a", "goal-b", "goal-c", "goal-d")
        val premium = policy.evaluate(QuotaGroup.GOALS, goals, emptySet(), premiumVerified = true)
        assertEquals(goals, premium.usableResourceIds)
        val downgraded = policy.evaluate(QuotaGroup.GOALS, goals, emptySet(), premiumVerified = false)
        assertTrue(downgraded.needsSelection)
        assertEquals(goals, downgraded.activeResourceIds)
        assertTrue(downgraded.usableResourceIds.isEmpty())
        val selected = policy.evaluate(QuotaGroup.GOALS, goals, setOf("goal-a", "goal-c"))
        assertEquals(2, selected.limit)
        assertFalse(selected.needsSelection)
        assertEquals(setOf("goal-a", "goal-c"), selected.usableResourceIds)
        assertEquals(setOf("goal-b", "goal-d"), selected.planLockedResourceIds)
        val renewed = policy.evaluate(QuotaGroup.GOALS, goals, selected.selectedResourceIds, premiumVerified = true)
        assertEquals(goals, renewed.activeResourceIds)
        assertEquals(goals, renewed.usableResourceIds)
        assertTrue(renewed.planLockedResourceIds.isEmpty())
        assertFalse(renewed.needsSelection)
    }

    @Test
    fun overLimitRequiresExplicitSelectionAndPreservesEveryResource() {
        val active = (1..9).map { "category-$it" }
        val state = policy.evaluate(QuotaGroup.CUSTOM_CATEGORIES, active, emptyList())

        assertTrue(state.needsSelection)
        assertTrue(state.usableResourceIds.isEmpty())
        assertEquals(active.toSet(), state.planLockedResourceIds)
        assertEquals(active.toSet(), state.activeResourceIds)
    }

    @Test
    fun selectedResourcesAreUsableAndOtherResourcesArePlanLocked() {
        val active = (1..9).map { "category-$it" }
        val selected = active.take(5)
        val state = policy.evaluate(QuotaGroup.CUSTOM_CATEGORIES, active, selected)

        assertFalse(state.needsSelection)
        assertEquals(selected.toSet(), state.usableResourceIds)
        assertEquals(active.drop(5).toSet(), state.planLockedResourceIds)
    }

    @Test
    fun ignoresStaleSelectionsAndNeverChangesFunctionalArchiveState() {
        val active = setOf("open-1", "open-2", "open-3", "open-4", "open-5")
        val state = policy.evaluate(
            QuotaGroup.INSTRUMENTS,
            active,
            selectedResourceIds = setOf("open-1", "deleted-archived", "open-2", "open-3", "open-4"),
        )

        assertEquals(setOf("open-1", "open-2", "open-3", "open-4"), state.usableResourceIds)
        assertEquals(setOf("open-5"), state.planLockedResourceIds)
        assertEquals(active, state.activeResourceIds)
    }

    @Test
    fun verifiedPremiumClearsOnlyPlanLocks() {
        val active = setOf("instrument-1", "instrument-2", "instrument-3", "instrument-4", "instrument-5")
        val state = policy.evaluate(QuotaGroup.INSTRUMENTS, active, emptyList(), premiumVerified = true)

        assertEquals(active, state.usableResourceIds)
        assertTrue(state.planLockedResourceIds.isEmpty())
    }

    @Test
    fun eachGroupUsesItsOwnLimit() {
        val active = (1..3).map { "debt-$it" }
        val state = policy.evaluate(QuotaGroup.DEBTS, active, active.take(2))

        assertEquals(2, state.limit)
        assertEquals(setOf("debt-1", "debt-2"), state.usableResourceIds)
        assertEquals(setOf("debt-3"), state.planLockedResourceIds)
    }
}
