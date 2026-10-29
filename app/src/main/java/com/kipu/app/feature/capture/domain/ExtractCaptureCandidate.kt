package com.kipu.app.feature.capture.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantAliasEvaluation
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.MerchantCategoryPreferenceRules
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.CaptureCandidate
import com.kipu.app.feature.categories.domain.model.CaptureConfidence
import com.kipu.app.feature.categories.domain.model.CaptureProvenance
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.categories.domain.usecase.EvaluateMerchantAlias
import com.kipu.app.feature.movements.domain.model.MovementType
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface CaptureExtractionResult {
    data class Created(val candidate: CaptureCandidateRecord) : CaptureExtractionResult
    data class Ignored(val outcome: CaptureSourceOutcome) : CaptureExtractionResult
    data object AccessDenied : CaptureExtractionResult
}

class ExtractCaptureCandidate @Inject constructor(
    private val catalog: CaptureSourceCatalog,
    private val gate: CaptureAccessGate,
    private val events: CaptureSourceEventStore,
    private val candidates: CaptureCandidateStore,
    private val instruments: CaptureInstrumentResolver,
    private val categories: CategoriesRepository,
    private val evaluateAlias: EvaluateMerchantAlias,
    private val clock: Clock,
    private val postProcessors: Set<@JvmSuppressWildcards CaptureCandidatePostProcessor>,
    private val transactions: com.kipu.app.core.database.DatabaseTransactionRunner,
    private val followups: CaptureResolutionFollowups,
) {
    suspend operator fun invoke(ownerId: String, signal: CaptureSignal): CaptureExtractionResult {
        if (!catalog.supportsPackage(signal.sourcePackage) ||
            catalog.provider(signal.sourcePackage, signal.title, signal.text) != signal.provider
        ) return CaptureExtractionResult.Ignored(CaptureSourceOutcome.IGNORED_UNSUPPORTED)
        val access = gate.evaluate(ownerId) as? CaptureAccessDecision.Allowed ?: return CaptureExtractionResult.AccessDenied
        val now = clock.millis()
        val fingerprint = fingerprint(signal)
        candidates.findBySourceFingerprint(ownerId, fingerprint)?.let {
            resume(it)
            return CaptureExtractionResult.Ignored(CaptureSourceOutcome.DUPLICATE)
        }
        if (events.find(ownerId, fingerprint) != null) return CaptureExtractionResult.Ignored(CaptureSourceOutcome.DUPLICATE)
        val parsed = NotificationCaptureParsers.forProvider(signal.provider).parse(signal)
        if (parsed.direction == null) {
            events.recordIfAbsent(CaptureSourceEvent(ownerId, fingerprint, signal.sourcePackage, now, CaptureSourceOutcome.IGNORED_UNPARSEABLE))
            return CaptureExtractionResult.Ignored(CaptureSourceOutcome.IGNORED_UNPARSEABLE)
        }
        val instrument = instruments.resolve(ownerId, signal.provider, parsed)
        val reasons = mutableListOf<CaptureReason>()
        if (parsed.amountMinor == null) reasons += CaptureReason.AMOUNT_MISSING
        if (parsed.currency == null) reasons += CaptureReason.CURRENCY_MISSING
        if (!parsed.occurredAtReliable) reasons += CaptureReason.DATE_INFERRED
        if (instrument.ambiguous) reasons += CaptureReason.INSTRUMENT_AMBIGUOUS
        if (instrument.accountId == null && instrument.cardId == null) reasons += CaptureReason.INSTRUMENT_UNRESOLVED
        if (instrument.kind == CaptureInstrumentKind.CREDIT_CARD) reasons += CaptureReason.CREDIT_CARD_REQUIRES_REVIEW

        val userId = UserId(ownerId)
        val authorized = CaptureCandidate.authorize(
            userId, SourceMerchantText(parsed.merchantSourceText ?: signal.text),
            CaptureProvenance(signal.sourcePackage, fingerprint, now),
            CaptureConfidence(0.0), access.entitlement, consentGranted = true, nowEpochMillis = now,
        ).getOrNull() ?: return CaptureExtractionResult.AccessDenied
        var merchantId: String? = null
        var categoryId: String? = null
        parsed.merchantSourceText?.let { source ->
            val merchants = categories.observeMerchantCatalog().first().filter { it.isActive }
            val alias = evaluateAlias(authorized, categories.observeMerchantAliasRules(userId).first(), merchants.map { it.id }.toSet())
            val merchant = when (alias) {
                is MerchantAliasEvaluation.Match -> merchants.singleOrNull { it.id == alias.merchantId }
                is MerchantAliasEvaluation.NeedsReview -> {
                    reasons += CaptureReason.MERCHANT_AMBIGUOUS
                    null
                }
                MerchantAliasEvaluation.NoMatch -> merchants.singleOrNull {
                    MerchantAliasRules.normalize(it.name) == MerchantAliasRules.normalize(source)
                }
            }
            merchantId = merchant?.id?.value
            if (merchant != null) {
                val preference = MerchantCategoryPreferenceRules.resolve(
                    userId, merchant.id, categories.observeMerchantCategoryPreferences(userId).first(),
                    categories.observeCategories(userId).first(),
                    if (parsed.direction == CaptureDirection.INCOME) MovementType.INCOME else MovementType.EXPENSE,
                    generalSuggestion = null,
                )
                // Only an explicit valid preference is a safe category for later automatic registration.
                categoryId = (preference as? MerchantPreferenceResolution.Preferred)?.categoryId?.value
            }
        }
        if (merchantId == null) reasons += CaptureReason.MERCHANT_UNRESOLVED
        if (categoryId == null) reasons += CaptureReason.CATEGORY_UNRESOLVED
        // Recheck after suspended lookups: a revoked source or switched owner cannot persist a candidate.
        if (gate.evaluate(ownerId) !is CaptureAccessDecision.Allowed) return CaptureExtractionResult.AccessDenied
        val record = CaptureCandidateRecord(
            id = UUID.randomUUID().toString(), ownerId = ownerId, sourcePackage = signal.sourcePackage,
            provider = signal.provider, sourceEventFingerprint = fingerprint, status = CaptureCandidateStatus.PENDING,
            direction = parsed.direction, amountMinor = parsed.amountMinor, currency = parsed.currency,
            occurredAt = parsed.occurredAt ?: signal.postTimeMillis, occurredAtInferred = !parsed.occurredAtReliable,
            merchantSourceText = parsed.merchantSourceText, merchantId = merchantId, categoryId = categoryId,
            cardLast4 = parsed.cardLast4, instrumentKind = instrument.kind, accountId = instrument.accountId,
            cardId = instrument.cardId, instrumentAmbiguous = instrument.ambiguous,
            confidence = CaptureConfidenceLevel.LOW, confidenceReasons = reasons.distinct(),
            creditMode = if (instrument.kind == CaptureInstrumentKind.CREDIT_CARD) CreditMode.DIRECT else null,
            installmentCount = null, resolvedTransactionId = null, resolutionKey = null,
            evidenceSnippet = maskEvidence(signal.text), evidenceExpiresAt = now + EVIDENCE_RETENTION_MILLIS,
            userCorrectedFields = emptyList(), createdAt = now, updatedAt = now, resolvedAt = null,
            followupsPending = CaptureFollowup.POST_PROCESS or CaptureFollowup.PUBLISH_REVIEW,
        )
        val inserted = transactions {
            if (events.find(ownerId, fingerprint) != null) false
            else {
                check(candidates.insertIfAbsent(record)) { "Capture fingerprint collision" }
                check(events.recordIfAbsent(CaptureSourceEvent(ownerId, fingerprint, signal.sourcePackage, now, CaptureSourceOutcome.CANDIDATE_CREATED)))
                true
            }
        }
        if (!inserted) {
            candidates.findBySourceFingerprint(ownerId, fingerprint)?.let { resume(it) }
            return CaptureExtractionResult.Ignored(CaptureSourceOutcome.DUPLICATE)
        }
        resume(record)
        return CaptureExtractionResult.Created(candidates.get(ownerId, record.id) ?: record)
    }

    /** Also used by the durable worker and duplicate callbacks after a process restart. */
    suspend fun resume(record: CaptureCandidateRecord) {
        if (record.followupsPending and (CaptureFollowup.POST_PROCESS or CaptureFollowup.PUBLISH_REVIEW) != 0) {
            // Access failure is retryable, not completion of post-processing.
            check(gate.evaluate(record.ownerId) is CaptureAccessDecision.Allowed)
            for (processor in postProcessors.sortedBy { it.order }) {
                processor.process(candidates.get(record.ownerId, record.id) ?: record)
            }
            candidates.completeFollowup(record.ownerId, record.id, CaptureFollowup.POST_PROCESS)
        }
        followups.process(record.ownerId, record.id) { gate.currentOwnerId() == record.ownerId }
    }

    companion object {
        const val EVIDENCE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
        fun maskEvidence(text: String): String = text
            .replace(Regex("\\d(?:[ -]?\\d){4,}"), "••••")
            .take(280)

        fun fingerprint(signal: CaptureSignal): String {
            val digest = MessageDigest.getInstance("SHA-256")
            // Length-prefix fields so separators inside notification keys cannot collide.
            listOf(signal.sourcePackage, signal.notificationKey, signal.postTimeMillis.toString(),
                MerchantAliasRules.normalize(signal.title), MerchantAliasRules.normalize(signal.text)).forEach { field ->
                val bytes = field.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
