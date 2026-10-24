package com.kipu.app.feature.budgets.presentation

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Arbol de categorias de gasto del usuario para el selector de alcance. */
interface BudgetCategoryOptionsSource {
    fun observe(ownerId: String): Flow<List<BudgetCategoryOption>>
}

class RepositoryBudgetCategoryOptionsSource @Inject constructor(
    private val categories: CategoriesRepository,
) : BudgetCategoryOptionsSource {
    override fun observe(ownerId: String): Flow<List<BudgetCategoryOption>> {
        val user = UserId(ownerId)
        return combine(categories.observeCategories(user), categories.observeCategoryPresentations(user)) { list, presentations ->
            val names = presentations.associate { it.categoryId.value to it.name }
            val usable = list.filter { it.isActive && it.categoryType != CategoryType.INCOME }
            val roots = usable.filter { it.isRoot }.sortedBy { names[it.id.value].orEmpty().lowercase() }
            buildList {
                roots.forEach { root ->
                    val rootName = names[root.id.value] ?: return@forEach
                    add(BudgetCategoryOption(root.id.value, rootName, null, null))
                    usable.filter { it.parentId?.value == root.id.value }
                        .mapNotNull { child -> names[child.id.value]?.let { child.id.value to it } }
                        .sortedBy { it.second.lowercase() }
                        .forEach { (id, name) -> add(BudgetCategoryOption(id, name, root.id.value, rootName)) }
                }
            }
        }
    }
}
