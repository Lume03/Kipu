package com.kipu.app.feature.statements.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantAliasEvaluation
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.MerchantTextMatcher
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.categories.domain.usecase.SaveMerchantAliasRule
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
    private val saveAlias: SaveMerchantAliasRule,
) {
    suspend operator fun invoke(ownerId: String, importId: String): StatementImportSnapshot? {
        val snapshot = store.observe(ownerId, importId).first() ?: return null
        if (snapshot.statement.status != StatementImportStatus.PENDING) return snapshot
        val catalog = categories.observeMerchantCatalog().first().filter { it.isActive }
        val aliases = categories.observeMerchantAliasRules(UserId(ownerId)).first()
        for (line in snapshot.lines) {
            if (line.status != StatementImportLineStatus.PENDING || !line.needsPurchaseCategory()) continue
            val alias = MerchantAliasRules.evaluate(UserId(ownerId), line.originalDescription, aliases, catalog.map { it.id }.toSet())
            val merchant = line.merchantId?.let { id -> catalog.singleOrNull { it.id.value == id } }
                ?: if (!line.merchantReviewed && alias is MerchantAliasEvaluation.Match) catalog.singleOrNull { it.id == alias.merchantId } else null
            if (merchant == null && !line.merchantReviewed && line.suggestedMerchantId == null && alias == MerchantAliasEvaluation.NoMatch) {
                MerchantTextMatcher.suggest(line.originalDescription, catalog)?.let {
                    store.setMerchantSuggestion(ownerId, line.id, it.id.value)
                }
            }
            val category = line.categoryId ?: systemCategory(ownerId, line.kind)
                ?: merchant?.let { categoryForMerchant(ownerId, it, line.kind) }
            if (category != line.categoryId || merchant?.id?.value != line.merchantId) {
                store.editLine(ownerId, line.id, StatementImportLineEdit(line.description, line.amountMinor, line.occurredDate,
                    category, line.kind, merchant?.id?.value, line.sourceAccountId))
            }
        }
        return store.observe(ownerId, importId).first()
    }

    /** UI can require category selection before confirmation; the confirm use case also enforces it. */
    suspend fun missingCategoryLineIds(ownerId: String, importId: String): List<String> =
        store.observe(ownerId, importId).first()?.lines.orEmpty().filter {
            it.selected && it.status == StatementImportLineStatus.PENDING && it.needsPurchaseCategory() &&
                effectiveCategory(ownerId, it) == null
        }.map { it.id }

    internal suspend fun effectiveCategory(ownerId: String, line: StatementImportLine): String? {
        val id = line.categoryId?.takeIf(String::isNotBlank) ?: systemCategory(ownerId, line.kind) ?: return null
        if (line.kind != StatementLineKind.ACCOUNT_DEBIT && line.kind != StatementLineKind.ACCOUNT_CREDIT) return id
        val all = categories.observeCategories(UserId(ownerId)).first().associateBy { it.id.value }
        val category = all[id] ?: return null
        val expected = if (line.kind == StatementLineKind.ACCOUNT_CREDIT) CategoryType.INCOME else CategoryType.EXPENSE
        return id.takeIf { category.isActive && !category.isPlanLocked &&
            (category.ownerId == null || category.ownerId == UserId(ownerId)) &&
            (category.categoryType == CategoryType.GENERAL || category.categoryType == expected) &&
            (category.parentId == null || all[category.parentId.value]?.let { it.isActive && !it.isPlanLocked } == true) }
    }

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

    suspend fun chooseMerchant(ownerId: String, lineId: String, merchantId: String?, importId: String): Boolean {
        val line = store.observe(ownerId, importId).first()?.lines?.singleOrNull { it.id == lineId } ?: return false
        val merchant = merchantId?.let { id -> categories.observeMerchantCatalog().first().singleOrNull { it.id.value == id && it.isActive } ?: return false }
        // A merchant without a usable preference or default keeps the category the user already has.
        val category = merchant?.let { categoryForMerchant(ownerId, it, line.kind) } ?: line.categoryId
        return store.chooseMerchant(ownerId, lineId, merchantId, category)
    }

    internal suspend fun categoryForMerchant(ownerId: String, merchant: MerchantCatalogEntry, kind: StatementLineKind): String? =
        when (val resolution = preferredCategory(UserId(ownerId), merchant.id,
            if (kind == StatementLineKind.ACCOUNT_CREDIT) MovementType.INCOME else MovementType.EXPENSE, merchant.defaultCategoryId)) {
            is MerchantPreferenceResolution.Preferred -> resolution.categoryId.value
            is MerchantPreferenceResolution.Suggested -> resolution.categoryId.value
            else -> null
        }

    /** Learn only durable registrations, considering the whole import to avoid order-dependent rules. */
    internal suspend fun learn(ownerId: String, lines: List<StatementImportLine>, scope: List<StatementImportLine>): List<StatementLearningWarning> {
        if (!categories.observePremiumVerified(UserId(ownerId)).first()) return emptyList()
        val warnings = mutableListOf<StatementLearningWarning>()
        val aliasConflicts = scope.filter { it.merchantId != null }.groupBy { MerchantAliasRules.normalize(it.originalDescription) }
            .filterValues { group -> group.map { it.merchantId }.distinct().size > 1 }
        val categoryConflicts = scope.filter { it.merchantId != null && it.categoryId != null }.groupBy { it.merchantId!! }
            .filterValues { group -> group.map { it.categoryId }.distinct().size > 1 }
        val catalog = categories.observeMerchantCatalog().first().filter { it.isActive }.associateBy { it.id.value }
        val aliases = categories.observeMerchantAliasRules(UserId(ownerId)).first()
        val preferences = categories.observeMerchantCategoryPreferences(UserId(ownerId)).first()
        val learnedAliases = mutableSetOf<String>()
        val learnedCategories = mutableSetOf<String>()
        for (line in lines) {
            if (!line.needsPurchaseCategory()) continue
            val merchant = catalog[line.merchantId] ?: continue
            val aliasKey = MerchantAliasRules.normalize(line.originalDescription)
            if (line.merchantReviewed && line.rememberMerchantAlias && aliases.none {
                it.ownerId == UserId(ownerId) && it.deletedAt == null && it.normalizedPattern == aliasKey
            } && learnedAliases.add(aliasKey)) {
                val conflict = aliasConflicts[aliasKey]
                if (conflict != null) warnings += StatementLearningWarning(StatementLearningWarningKind.ALIAS_CONFLICT, aliasKey, conflict.map { it.id })
                else try {
                    saveAlias(UserId(ownerId), SourceMerchantText(line.originalDescription), merchant, merchant.id).getOrThrow()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { warnings += StatementLearningWarning(StatementLearningWarningKind.ALIAS_SAVE_FAILED, aliasKey, listOf(line.id)) }
            }
            val category = line.categoryId ?: continue
            if (line.rememberMerchantCategory && category != systemCategory(ownerId, line.kind) && preferences.none {
                it.ownerId == UserId(ownerId) && it.merchantId == merchant.id && it.deletedAt == null && it.categoryId.value == category
            } && learnedCategories.add(merchant.id.value)) {
                val conflict = categoryConflicts[merchant.id.value]
                if (conflict != null) warnings += StatementLearningWarning(StatementLearningWarningKind.CATEGORY_CONFLICT, merchant.id.value, conflict.map { it.id })
                else try {
                    setPreference(UserId(ownerId), merchant.id, CategoryId(category)).getOrThrow()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { warnings += StatementLearningWarning(StatementLearningWarningKind.CATEGORY_SAVE_FAILED, merchant.id.value, listOf(line.id)) }
            }
        }
        return warnings
    }
}

internal fun StatementImportLine.needsPurchaseCategory(): Boolean = kind in setOf(
    StatementLineKind.PURCHASE, StatementLineKind.INSTALLMENT_PURCHASE, StatementLineKind.INTEREST,
    StatementLineKind.INSURANCE, StatementLineKind.FEE, StatementLineKind.ACCOUNT_DEBIT, StatementLineKind.ACCOUNT_CREDIT,
)
