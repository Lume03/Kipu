package com.kipu.app.feature.budgets.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetAppearanceStoreTest {
    @Test fun selectedIconSurvivesReopeningAndIsIsolatedByOwnerAndBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = UUID.randomUUID().toString()
        val id = UUID.randomUUID().toString()
        val store = BudgetAppearanceStore(context)
        try {
            store.saveIcon(owner, id, "directions_car")
            val reopened = BudgetAppearanceStore(context)
            assertEquals("directions_car", reopened.icon(owner, id))
            assertNull(reopened.icon("other-owner", id))
            assertNull(reopened.icon(owner, "other-budget"))
        } finally { store.saveIcon(owner, id, null) }
    }
}
