package com.kipu.app.feature.budgets

import com.kipu.app.feature.budgets.presentation.BudgetCategoryOption
import com.kipu.app.feature.budgets.presentation.BudgetIdea
import com.kipu.app.feature.budgets.presentation.BudgetScopeKind
import com.kipu.app.feature.goals.presentation.GoalIdea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetIdeasTest {
    private fun option(id: String, name: String, parentId: String? = null, icon: String = "category") =
        BudgetCategoryOption(id = id, name = name, parentId = parentId, parentName = null, iconId = icon)

    @Test fun ideaPrefillsNameLimitAndTheMatchingRootCategory() {
        val categories = listOf(
            option("sub", "Alimentación fuera de casa", parentId = "root"),
            option("root", "Alimentación", icon = "restaurant"),
            option("fun", "Ocio y entretenimiento"),
        )
        val input = BudgetIdea.FOOD.toInput(categories)
        assertEquals("Alimentación", input.name)
        assertEquals("500", input.limitText)
        assertEquals(BudgetScopeKind.CATEGORY, input.scopeKind)
        assertEquals("root", input.categoryId)
        assertEquals("restaurant", input.iconId)
        assertEquals("fun", BudgetIdea.LEISURE.toInput(categories).categoryId)
    }

    @Test fun matchingIgnoresAccentsAndCase() {
        val input = BudgetIdea.TRANSPORT.toInput(listOf(option("t", "TRANSPORTE público")))
        assertEquals("t", input.categoryId)
    }

    @Test fun withoutAMatchingCategoryTheBudgetStartsAsTotalAndNothingIsInvented() {
        val input = BudgetIdea.TRANSPORT.toInput(listOf(option("food", "Alimentación")))
        assertEquals(BudgetScopeKind.TOTAL, input.scopeKind)
        assertNull(input.categoryId)
        assertEquals("250", input.limitText)
    }

    @Test fun ideasAreResolvedByKeyAndUnknownKeysAreIgnored() {
        assertEquals(BudgetIdea.LEISURE, BudgetIdea.fromKey("leisure"))
        assertNull(BudgetIdea.fromKey("nope"))
        assertEquals(GoalIdea.EMERGENCY, GoalIdea.fromKey("emergency"))
        assertEquals("", GoalIdea.OTHER.goalName)
        assertNull(GoalIdea.fromKey(null))
        assertEquals(listOf("shield", "flight", "laptop", "savings"), GoalIdea.entries.map { it.iconKey })
    }
}
