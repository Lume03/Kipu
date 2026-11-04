package com.kipu.app.feature.categories.presentation

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.categories.CategoryTab
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CategoryCatalogRegressionTest {
    private val owner = UserId.generate()
    private fun item(entry: InitialCategoryCatalog.Entry, presentation: CategoryPresentation? = null) = CategoryItem(
        Category(CategoryId(entry.id), null, entry.parentId?.let(::CategoryId), CategoryOrigin.SYSTEM, true,
            categoryType = entry.type), presentation)

    @Test fun `every catalog root and child has a resolved icon and a non gray default`() {
        assertEquals(12, InitialCategoryCatalog.entries.size)
        assertEquals(51, InitialCategoryCatalog.allEntries.size)
        InitialCategoryCatalog.allEntries.forEach { entry ->
            val category = item(entry)
            assertEquals(entry.name, category.displayName)
            assertEquals(entry.iconKey, category.icon)
            assertEquals(entry.color, category.color)
            assertNotEquals(entry.name, Icons.Default.Label, resolveCategoryIcon(category.icon))
            assertNotEquals("#757575", category.color)
            assertEquals(CategoryType.EXPENSE, entry.type)
        }
    }

    @Test fun `legacy placeholders are repaired consistently without rewriting custom presentation`() {
        InitialCategoryCatalog.allEntries.forEach { entry ->
            val old = CategoryPresentation(CategoryId(entry.id), owner, entry.name, "category", "#757575")
            assertEquals(entry.iconKey, item(entry, old).icon)
            assertEquals(entry.color, item(entry, old).color)
            val hydrated = old.copy(icon = entry.iconKey)
            assertEquals(entry.color, item(entry, hydrated).color)
            val custom = old.copy(name = "Mi categor?a", icon = "star", color = "#123456", revision = 2)
            assertEquals("star", item(entry, custom).icon)
            assertEquals("#123456", item(entry, custom).color)
            assertEquals("Mi categor?a", item(entry, custom).displayName)
            val intentionalGray = old.copy(revision = 2)
            assertEquals("#757575", item(entry, intentionalGray).color)
        }
    }

    @Test fun `catalog expenses never appear in income management and custom general remains`() {
        val roots = InitialCategoryCatalog.entries.map { item(it) }
        assertEquals(12, CategoryTab.EXPENSE.filterCategories(roots).size)
        assertTrue(CategoryTab.INCOME.filterCategories(roots).isEmpty())
        assertEquals("GENERAL", InitialCategoryCatalog.resolvedType("custom", null, "CUSTOM", "GENERAL"))
        assertEquals("INCOME", InitialCategoryCatalog.resolvedType("custom", null, "CUSTOM", "INCOME"))
        assertEquals("EXPENSE", InitialCategoryCatalog.resolvedType("child", roots.first().category.id.value, "CUSTOM", "GENERAL"))
    }
}
