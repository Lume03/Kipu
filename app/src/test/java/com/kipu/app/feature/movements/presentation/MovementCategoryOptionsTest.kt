package com.kipu.app.feature.movements.presentation

import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.movements.domain.model.MovementType
import org.junit.Assert.*
import org.junit.Test

class MovementCategoryOptionsTest {
    private val expenses = InitialCategoryCatalog.allEntries.map {
        CategoryOption(it.id, it.name, it.iconKey, it.type, it.parentId, color = it.color)
    }
    private val income = CategoryOption("salary", "Ingreso personalizado", "work", CategoryType.INCOME)
    private val general = CategoryOption("shared", "General personalizado", "star", CategoryType.GENERAL)
    private val options = expenses + listOf(income, general)

    @Test fun `create selectors exclude the opposite type for roots and subcategories`() {
        assertEquals(expenses + general, options.forMovementType(MovementType.EXPENSE))
        assertEquals(listOf(income, general), options.forMovementType(MovementType.INCOME))
        assertTrue(options.forMovementType(MovementType.TRANSFER).isEmpty())
    }
    @Test fun `editor selector is filtered while historical selection stays intact`() {
        val historical = expenses.first()
        val state = MovementEditorUiState(movementType = MovementType.INCOME,
            availableCategories = options, initialCategoryId = historical.id,
            selectedCategoryId = historical.id, selectedCategoryName = historical.name)
        assertEquals(listOf(income, general), state.selectableCategories)
        assertEquals(historical.id, state.selectedCategoryId)
        assertTrue(state.copy(movementType = MovementType.TRANSFER).selectableCategories.isEmpty())
    }
}
