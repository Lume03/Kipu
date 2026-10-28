package com.kipu.app.feature.goals.presentation

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalDisplaySectionsTest {
    private fun item(id: String, status: GoalStatus = GoalStatus.ACTIVE): GoalListItemState {
        val goal = Goal(id, "owner", id, Money(10000, Currency.PEN), status = status, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        return GoalListItemState(goal, GoalProgressCalculator.calculate(goal, emptyList()))
    }
    @Test fun `completed and paused goals leave active section and removed goals stay hidden`() {
        val sections = GoalManagementUiState(goals = listOf(item("active"), item("paused"), item("done", GoalStatus.COMPLETED),
            item("legacy", GoalStatus.ARCHIVED), item("removed")), pausedGoalIds = setOf("paused"), removedGoalIds = setOf("removed")).displaySections()
        assertEquals(listOf("active"), sections.active.map { it.goal.id })
        assertEquals(listOf("paused", "legacy"), sections.paused.map { it.goal.id })
        assertEquals(listOf("done"), sections.completed.map { it.goal.id })
    }
}
