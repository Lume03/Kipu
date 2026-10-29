package com.kipu.app.feature.capture.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.categories.domain.usecase.SaveMerchantAliasRule
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LearnFromCaptureCorrection @Inject constructor(
    private val categories: CategoriesRepository,
    private val saveAlias: SaveMerchantAliasRule,
    private val setPreference: SetMerchantCategoryPreference,
    private val gate: CaptureAccessGate,
) : CaptureCandidateResolvedListener {
    override val followupFlag = CaptureFollowup.LEARN
    override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) {
        if (candidate.status != CaptureCandidateStatus.CONFIRMED || gate.currentOwnerId() != candidate.ownerId) return
        val fields = candidate.userCorrectedFields
        if (CaptureCorrectedField.MERCHANT.name !in fields && CaptureCorrectedField.CATEGORY.name !in fields) return
        val owner = UserId(candidate.ownerId)
        check(categories.observePremiumVerified(owner).first()) { "Capture learning awaits verified Premium" }
        val merchantId = candidate.merchantId?.let(::MerchantId) ?: return
        if (CaptureCorrectedField.MERCHANT.name in fields && !candidate.merchantSourceText.isNullOrBlank()) {
            val merchant = categories.observeMerchantCatalog().first().firstOrNull { it.id == merchantId && it.isActive }
            if (merchant != null) {
                val normalized = MerchantAliasRules.normalize(candidate.merchantSourceText)
                val existing = categories.observeMerchantAliasRules(owner).first().firstOrNull {
                    it.ownerId == owner && it.normalizedPattern == normalized && it.deletedAt == null
                }
                if (existing?.merchantId != merchantId) {
                    saveAlias(owner, SourceMerchantText(candidate.merchantSourceText), merchant, merchantId, existing).getOrThrow()
                }
            }
        }
        if (CaptureCorrectedField.CATEGORY.name in fields) {
            candidate.categoryId?.let { categoryId ->
                val existing = categories.observeMerchantCategoryPreferences(owner).first()
                    .firstOrNull { it.ownerId == owner && it.merchantId == merchantId }
                if (existing?.categoryId?.value != categoryId) setPreference(owner, merchantId, CategoryId(categoryId)).getOrThrow()
            }
        }
    }
}
