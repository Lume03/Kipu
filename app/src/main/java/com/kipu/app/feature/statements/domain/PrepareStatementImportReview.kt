package com.kipu.app.feature.statements.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantAliasEvaluation
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.ResolvePreferredCategory
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import com.kipu.app.feature.movements.domain.model.MovementType
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Review entry point: prefill only unclassified pending purchases, preserving the user's edits. */
class PrepareStatementImportReview @Inject constructor(
    private val store: StatementImportStore,
    private val categories: CategoriesRepository,
    private val preferredCategory: ResolvePreferredCategory,
    private val setPreference: SetMerchantCategoryPreference,
) {
    suspend operator fun invoke(ownerId: String, importId: String): StatementImportSnapshot? {
        val snapshot = store.observe(ownerId, importId).first() ?: return null
        if (snapshot.statement.status != StatementImportStatus.PENDING) return snapshot
        for (line in snapshot.lines) {
            if (line.status != StatementImportLineStatus.PENDING || !line.needsPurchaseCategory() || line.categoryId != null) continue
            val merchant = merchant(ownerId, line)
            val suggestion = systemCategory(ownerId, line.kind) ?: merchant?.let {
                // Only learned preferences are persisted here: a general catalog suggestion is not
                // a user correction and must not produce a new learned preference on confirmation.
                when (val resolution = preferredCategory(UserId(ownerId), it.id, MovementType.EXPENSE, null)) {
                    is MerchantPreferenceResolution.Preferred -> resolution.categoryId.value
                    else -> null
                }
            } ?: continue
            store.editLine(ownerId, line.id, StatementImportLineEdit(line.description, line.amountMinor, line.occurredDate,
                suggestion, line.kind, line.merchantId ?: merchant?.id?.value, line.sourceAccountId))
        }
        return store.observe(ownerId, importId).first()
    }

    /** UI can require category selection before confirmation; the confirm use case also enforces it. */
    suspend fun missingCategoryLineIds(ownerId: String, importId: String): List<String> =
        store.observe(ownerId, importId).first()?.lines.orEmpty().filter {
            it.selected && it.status == StatementImportLineStatus.PENDING && it.needsPurchaseCategory() &&
                effectiveCategory(ownerId, it) == null
        }.map { it.id }

    internal suspend fun effectiveCategory(ownerId: String, line: StatementImportLine): String? =
        line.categoryId?.takeIf(String::isNotBlank) ?: systemCategory(ownerId, line.kind)

    private suspend fun systemCategory(ownerId: String, kind: StatementLineKind): String? {
        // Canonical system ids from the existing category catalog; never create replacement categories.
        val id = when (kind) {
            StatementLineKind.INSURANCE -> "00000000-0000-0000-0000-000000000903"
            StatementLineKind.INTEREST, StatementLineKind.FEE -> "00000000-0000-0000-0000-000000000901"
            else -> return null
        }
        val all = categories.observeCategories(UserId(ownerId)).first().associateBy { it.id.value }
        val category = all[id] ?: return null
        val parent = category.parentId?.let { all[it.value] }
        return id.takeIf {
            category.isSystem && category.ownerId == null && category.isActive && !category.isPlanLocked &&
                category.categoryType != CategoryType.INCOME &&
                (category.parentId == null || parent?.let { it.isActive && !it.isPlanLocked } == true)
        }
    }

    internal suspend fun learn(ownerId: String, line: StatementImportLine) {
        if (!line.needsPurchaseCategory()) return
        val categoryId = line.categoryId?.takeIf(String::isNotBlank) ?: return
        // The model stores explicit category edits, while automatic system fallbacks are not corrections.
        if (categoryId == systemCategory(ownerId, line.kind)) return
        val merchant = merchant(ownerId, line) ?: return
        val existing = categories.observeMerchantCategoryPreferences(UserId(ownerId)).first().singleOrNull {
            it.ownerId == UserId(ownerId) && it.merchantId == merchant.id && it.deletedAt == null
        }
        if (existing?.categoryId?.value != categoryId) {
            setPreference(UserId(ownerId), merchant.id, CategoryId(categoryId)).getOrThrow()
        }
    }

    private suspend fun merchant(ownerId: String, line: StatementImportLine): MerchantCatalogEntry? {
        val catalog = categories.observeMerchantCatalog().first().filter { it.isActive }
        line.merchantId?.let { id -> return catalog.singleOrNull { it.id == MerchantId(id) } }
        val alias = MerchantAliasRules.evaluate(UserId(ownerId), line.description,
            categories.observeMerchantAliasRules(UserId(ownerId)).first(), catalog.map { it.id }.toSet())
        return when (alias) {
            is MerchantAliasEvaluation.Match -> catalog.singleOrNull { it.id == alias.merchantId }
            is MerchantAliasEvaluation.NeedsReview -> null
            MerchantAliasEvaluation.NoMatch -> catalog.singleOrNull {
                MerchantAliasRules.normalize(it.name) == MerchantAliasRules.normalize(line.description)
            }
        }
    }
}

internal fun StatementImportLine.needsPurchaseCategory(): Boolean = kind in setOf(
    StatementLineKind.PURCHASE, StatementLineKind.INSTALLMENT_PURCHASE, StatementLineKind.INTEREST,
    StatementLineKind.INSURANCE, StatementLineKind.FEE,
)
