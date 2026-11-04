package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CategoryItem(
    val category: Category,
    val presentation: CategoryPresentation?,
    val subcategories: List<CategoryItem> = emptyList(),
) {
    private val systemDefault get() = if (category.isSystem)
        com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog.find(category.id.value) else null
    // Only the exact unedited v1 placeholders are repaired; custom names/icons/colors survive.
    private val usesLegacyDefault get() = systemDefault?.let { entry ->
        presentation == null || (presentation.revision == 1L && presentation.name == entry.name &&
            presentation.color.equals("#757575", ignoreCase = true) &&
            (presentation.icon == "category" || presentation.icon == entry.iconKey))
    } ?: false
    val displayName: String get() = presentation?.name ?: systemDefault?.name ?: "Sin nombre"
    val icon: String get() = if (usesLegacyDefault) systemDefault!!.iconKey else presentation?.icon ?: "category"
    val color: String get() = if (usesLegacyDefault) systemDefault!!.color else presentation?.color ?: "#757575"
}

class ObserveCategories @Inject constructor(
    private val repository: CategoriesRepository,
) {
    fun observePremiumVerified(userId: UserId): Flow<Boolean> = repository.observePremiumVerified(userId)

    operator fun invoke(userId: UserId): Flow<List<CategoryItem>> {
        return combine(
            repository.observeCategories(userId),
            repository.observeCategoryPresentations(userId),
        ) { categories, presentations ->
            val presentationMap = presentations.associateBy { it.categoryId }

            val subcategoriesByParent = categories
                .filter { it.parentId != null }
                .groupBy { it.parentId!! }

            categories
                .filter { it.isRoot }
                .map { root ->
                    val subs = (subcategoriesByParent[root.id] ?: emptyList()).map { sub ->
                        CategoryItem(
                            category = sub,
                            presentation = presentationMap[sub.id],
                            subcategories = emptyList(),
                        )
                    }
                    CategoryItem(
                        category = root,
                        presentation = presentationMap[root.id],
                        subcategories = subs,
                    )
                }
        }
    }
}

class ObserveSelectedFreeCategoryRoots @Inject constructor(
    private val repository: CategoriesRepository,
) {
    operator fun invoke(userId: UserId): Flow<Set<com.kipu.app.feature.categories.domain.model.CategoryId>> =
        repository.observeSelectedFreeCategoryRoots(userId)
}

class SaveSelectedFreeCategoryRoots @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        userId: UserId,
        categoryIds: Set<com.kipu.app.feature.categories.domain.model.CategoryId>,
    ): Result<Unit> = repository.saveSelectedFreeCategoryRoots(userId, categoryIds)
}
