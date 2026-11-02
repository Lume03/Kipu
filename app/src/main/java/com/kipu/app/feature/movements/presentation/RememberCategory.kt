package com.kipu.app.feature.movements.presentation

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.MerchantCategoryPreferenceRules
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.movements.domain.model.MovementType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class RememberOption(val label: String, val checked: Boolean = true, val merchantId: String, val categoryId: String)

internal fun rememberCategoryOption(
    owner: UserId, premium: Boolean, merchantId: String?, merchantName: String,
    categoryId: String?, categoryName: String?, type: MovementType,
    preferences: List<MerchantCategoryPreference>, categories: List<Category>, previous: RememberOption?,
): RememberOption? {
    if (!premium || merchantId == null || categoryId == null || categoryName.isNullOrBlank() || merchantName.isBlank()) return null
    val merchant = MerchantId(merchantId)
    val category = CategoryId(categoryId)
    if (preferences.any { it.ownerId == owner && it.merchantId == merchant && it.deletedAt == null && it.categoryId == category }) return null
    val eligible = MerchantCategoryPreferenceRules.resolve(owner, merchant, emptyList(), categories, type, category)
    if (eligible !is MerchantPreferenceResolution.Suggested || eligible.categoryId != category) return null
    val checked = previous?.takeIf { it.merchantId == merchantId && it.categoryId == categoryId }?.checked ?: true
    return RememberOption("Recordar $categoryName para $merchantName", checked, merchantId, categoryId)
}

internal data class RememberCategoryInput(
    val merchantId: String?, val merchantName: String, val categoryId: String?, val categoryName: String?,
    val type: MovementType, val changed: Boolean = true,
)

internal fun observeRememberCategory(
    owner: UserId,
    repository: com.kipu.app.feature.categories.domain.CategoriesRepository,
    inputs: kotlinx.coroutines.flow.Flow<RememberCategoryInput>,
): kotlinx.coroutines.flow.Flow<Pair<RememberCategoryInput, RememberOption?>> =
    kotlinx.coroutines.flow.combine(
        inputs,
        repository.observePremiumVerified(owner).catch { emit(false) },
        repository.observeMerchantCategoryPreferences(owner).map { it as List<MerchantCategoryPreference>? }.catch { emit(null) },
        repository.observeCategories(owner).catch { emit(emptyList()) },
    ) { input, premium, preferences, categories ->
        input to if (!input.changed || preferences == null) null else rememberCategoryOption(
            owner, premium, input.merchantId, input.merchantName, input.categoryId, input.categoryName,
            input.type, preferences, categories, null,
        )
    }

internal suspend fun learnRememberedCategory(
    owner: UserId, option: RememberOption?,
    repository: com.kipu.app.feature.categories.domain.CategoriesRepository?,
    setter: com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference?,
) {
    if (option?.checked != true || repository == null || setter == null) return
    try {
        if (repository.observePremiumVerified(owner).first()) {
            setter(owner, MerchantId(option.merchantId), CategoryId(option.categoryId)).getOrThrow()
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { java.util.logging.Logger.getLogger("RememberCategory").warning("Could not save merchant category preference") }
}

internal suspend fun resolveRememberCategory(
    owner: UserId, repository: com.kipu.app.feature.categories.domain.CategoriesRepository?,
    input: RememberCategoryInput, previous: RememberOption?,
): RememberOption? {
    if (repository == null || !input.changed) return null
    return try {
        rememberCategoryOption(owner, repository.observePremiumVerified(owner).first(), input.merchantId,
            input.merchantName, input.categoryId, input.categoryName, input.type,
            repository.observeMerchantCategoryPreferences(owner).first(), repository.observeCategories(owner).first(), previous)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
