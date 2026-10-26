package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.BudgetConsumption
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetVisualStateTest {
    @Test fun categoryAndSubcategoryIconsOverrideTheSavedCustomIcon() {
        val categories = listOf(
            BudgetCategoryOption("food", "Alimentación", null, null, "restaurant"),
            BudgetCategoryOption("fast", "Comida rápida", "food", "Alimentación", "fastfood"),
        )
        assertEquals("restaurant", budgetIconId("food", "music_note", categories))
        assertEquals("fastfood", budgetIconId("fast", "music_note", categories))
        assertEquals("music_note", budgetIconId(null, "music_note", categories))
        assertEquals(null, budgetIconId("unavailable", "music_note", categories))
    }
    @Test fun warningLabelsFollowTheConfiguredThresholds() {
        val thresholds = listOf(7000, 9000, 10000)
        assertEquals("Dentro del límite", BudgetConsumption(10000, 6900).statusLabel(thresholds))
        assertEquals("Atención", BudgetConsumption(10000, 8500).statusLabel(thresholds))
        assertEquals("Casi al límite", BudgetConsumption(10000, 9000).statusLabel(thresholds))
        assertEquals("Límite alcanzado", BudgetConsumption(10000, 10000).statusLabel(thresholds))
        assertEquals("Excedido", BudgetConsumption(10000, 10001).statusLabel(thresholds))
    }
}
