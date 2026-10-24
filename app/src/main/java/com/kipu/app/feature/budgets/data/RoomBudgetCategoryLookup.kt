package com.kipu.app.feature.budgets.data

import com.kipu.app.feature.budgets.domain.BudgetCategoryLookup
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import javax.inject.Inject

class RoomBudgetCategoryLookup @Inject constructor(
    private val categoryDao: CategoryDao,
) : BudgetCategoryLookup {
    override suspend fun isAvailable(ownerId: String, categoryId: String): Boolean =
        activeCategories(ownerId).any { it.id == categoryId }

    override suspend fun idsIncludingDescendants(ownerId: String, categoryId: String): Set<String> {
        val categories = activeCategories(ownerId)
        if (categories.none { it.id == categoryId }) return emptySet()
        val childrenByParent = categories.groupBy(CategoryEntity::parentId)
        val result = linkedSetOf(categoryId)
        val queue = ArrayDeque<String>()
        queue.add(categoryId)
        while (queue.isNotEmpty()) {
            for (child in childrenByParent[queue.removeFirst()].orEmpty()) {
                if (result.add(child.id)) queue.addLast(child.id)
            }
        }
        return result
    }

    private suspend fun activeCategories(ownerId: String): List<CategoryEntity> =
        categoryDao.getCategoriesForUser(ownerId).filter { category ->
            category.isActive && (category.userId == null || category.userId == ownerId)
        }
}
