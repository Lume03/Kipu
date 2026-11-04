package com.kipu.app.feature.categories.data

import android.content.Context
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.remote.*
import com.kipu.app.feature.categories.data.sync.CategoryHydration
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CategoryCatalogHydrationTest {
    @Test fun `hydration repairs generated high revision gray caches and retains remote personal presentation`() = runTest {
        val dao = FakeCategoryDao()
        val api = mockk<CategoriesApi>()
        val owner = "owner"
        val instant = "2026-09-29T00:00:00Z"
        val roots = InitialCategoryCatalog.entries.take(4)
        roots.forEach { entry -> dao.insertPresentation(CategoryPresentationEntity(entry.id, owner,
            entry.name, "category", "#757575", 7, 1)) }
        coEvery { api.fetchCategories() } returns CategoryApiResponse.Success(roots.map {
            CategoryCatalogItemDto(it.id, it.name, it.iconKey, null, null, "SYSTEM", "GENERAL", true, 7, instant, instant)
        })
        // A server customization remains authoritative even if its color is gray.
        coEvery { api.fetchCategoryPresentations() } returns CategoryApiResponse.Success(listOf(
            CategoryPresentationCatalogItemDto(roots[1].id, owner, "Mi transporte", "star", "#757575", 1, instant)
        ))
        dao.insertPresentation(CategoryPresentationEntity(roots[2].id, owner, "Mi servicio", "work", "#123456", 8, 1))
        dao.insertOutboxCommand(CategorySyncOutboxEntity("op", owner, "UPDATE_PRESENTATION", "CATEGORY",
            roots[3].id, payloadJson = "{}", payloadHash = "hash", createdAt = 1, updatedAt = 1))
        CategoryHydration(dao, FakeMerchantCatalogDao(), mockk<Context>(), api).hydrateCategories(owner)
        assertEquals(roots[0].iconKey, dao.getPresentation(owner, roots[0].id)?.icon)
        assertEquals(roots[0].color, dao.getPresentation(owner, roots[0].id)?.color)
        assertEquals("Mi transporte", dao.getPresentation(owner, roots[1].id)?.name)
        assertEquals("#757575", dao.getPresentation(owner, roots[1].id)?.color)
        assertEquals("Mi servicio", dao.getPresentation(owner, roots[2].id)?.name)
        assertEquals("#123456", dao.getPresentation(owner, roots[2].id)?.color)
        assertEquals("category", dao.getPresentation(owner, roots[3].id)?.icon)
        assertEquals("#757575", dao.getPresentation(owner, roots[3].id)?.color)
        assertTrue(dao.categories.values.all { it.categoryType == "EXPENSE" })
    }
}
