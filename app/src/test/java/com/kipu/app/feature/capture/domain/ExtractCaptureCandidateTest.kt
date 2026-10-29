package com.kipu.app.feature.capture.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.domain.usecase.EvaluateMerchantAlias
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import io.mockk.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExtractCaptureCandidateTest {
    private val owner = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-28T17:00:00Z").toEpochMilli()
    private var access: CaptureAccessDecision = CaptureAccessDecision.Allowed(EffectiveEntitlement(true, null))
    private val gate = object : CaptureAccessGate {
        override fun currentOwnerId() = owner
        override suspend fun evaluateCurrent() = access
        override suspend fun evaluate(ownerId: String) = if (ownerId == owner) access else CaptureAccessDecision.NoSession
    }
    private val seen = mutableSetOf<Pair<String, String>>()
    private val terminalEvents = mutableMapOf<Pair<String, String>, CaptureSourceEvent>()
    private val saved = mutableMapOf<Pair<String, String>, CaptureCandidateRecord>()
    private val events = mockk<CaptureSourceEventStore>(relaxed = true) {
        coEvery { recordIfAbsent(any()) } answers {
            val event = firstArg<CaptureSourceEvent>()
            terminalEvents.putIfAbsent(event.ownerId to event.eventFingerprint, event)
            seen.add(event.ownerId to event.eventFingerprint)
        }
        coEvery { find(any(), any()) } answers { terminalEvents[firstArg<String>() to secondArg<String>()] }
    }
    private val candidates = mockk<CaptureCandidateStore>(relaxed = true) {
        coEvery { findBySourceFingerprint(any(), any()) } answers { saved.values.firstOrNull { it.ownerId == firstArg<String>() && it.sourceEventFingerprint == secondArg<String>() } }
        coEvery { completeFollowup(any(), any(), any()) } answers {
            val pair = firstArg<String>() to secondArg<String>()
            saved[pair]?.let { saved[pair] = it.copy(followupsPending = it.followupsPending and thirdArg<Int>().inv()) }
        }
        coEvery { insertIfAbsent(any()) } answers {
            val record = firstArg<CaptureCandidateRecord>()
            if (saved.values.any { it.ownerId == record.ownerId && it.sourceEventFingerprint == record.sourceEventFingerprint }) false
            else { saved[record.ownerId to record.id] = record; true }
        }
        coEvery { get(any(), any()) } answers { saved[firstArg<String>() to secondArg<String>()] }
    }
    private var instrument = CaptureInstrumentResolution()
    private val resolver = CaptureInstrumentResolver { _, _, _ -> instrument }
    private val categories = mockk<CategoriesRepository> {
        every { observeMerchantCatalog() } returns flowOf(emptyList())
        every { observeMerchantAliasRules(UserId(owner)) } returns flowOf(emptyList())
        every { observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(emptyList())
        every { observeCategories(UserId(owner)) } returns flowOf(emptyList())
    }
    private val catalog = CaptureSourceCatalog(true)
    private fun pipeline(processors: Set<CaptureCandidatePostProcessor> = emptySet()) = ExtractCaptureCandidate(
        catalog, gate, events, candidates, resolver, categories, EvaluateMerchantAlias(),
        Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC), processors, TestCaptureTransactions, CaptureResolutionFollowups(candidates, emptySet(), emptySet()),
    )
    private fun signal(text: String = "Yapeaste S/ 25.50 a Juan P.", key: String = "key", source: String = CaptureSourceCatalog.DEBUG_SHELL) =
        CaptureSignal(CaptureProvider.YAPE, source, key, now - 1000, "Yape", text)

    @Test fun `repeat delivery produces one candidate and duplicate outcome`() = runTest {
        val extract = pipeline()
        assertTrue(extract(owner, signal()) is CaptureExtractionResult.Created)
        assertEquals(CaptureExtractionResult.Ignored(CaptureSourceOutcome.DUPLICATE), extract(owner, signal()))
        assertEquals(1, saved.size)
        coVerify(exactly = 1) { candidates.insertIfAbsent(any()) }
        coVerify(exactly = 1) { events.recordIfAbsent(match { it.outcome == CaptureSourceOutcome.CANDIDATE_CREATED }) }
    }

    @Test fun `ambiguous instrument remains unresolved with reason and low initial confidence`() = runTest {
        instrument = CaptureInstrumentResolution(ambiguous = true)
        val result = pipeline()(owner, signal()) as CaptureExtractionResult.Created
        assertTrue(result.candidate.instrumentAmbiguous)
        assertNull(result.candidate.cardId)
        assertNull(result.candidate.accountId)
        assertEquals(CaptureConfidenceLevel.LOW, result.candidate.confidence)
        assertTrue(CaptureReason.INSTRUMENT_AMBIGUOUS in result.candidate.confidenceReasons)
    }

    @Test fun `post time is explicitly inferred and evidence is masked and expires after seven days`() = runTest {
        val source = signal("Yapeaste S/ 25.50 a Juan P. referencia 1234 5678 9012 3456 " + "x".repeat(400))
        val candidate = (pipeline()(owner, source) as CaptureExtractionResult.Created).candidate
        assertEquals(source.postTimeMillis, candidate.occurredAt)
        assertTrue(candidate.occurredAtInferred)
        assertTrue(CaptureReason.DATE_INFERRED in candidate.confidenceReasons)
        assertEquals(now + ExtractCaptureCandidate.EVIDENCE_RETENTION_MILLIS, candidate.evidenceExpiresAt)
        assertTrue(candidate.evidenceSnippet!!.length <= 280)
        assertFalse(candidate.evidenceSnippet.contains("1234"))
    }

    @Test fun `known direction without amount creates low review candidate and unknown direction is ignored`() = runTest {
        val candidate = (pipeline()(owner, signal("Yapeaste a Juan")) as CaptureExtractionResult.Created).candidate
        assertNull(candidate.amountMinor)
        assertTrue(CaptureReason.AMOUNT_MISSING in candidate.confidenceReasons)
        assertEquals(CaptureExtractionResult.Ignored(CaptureSourceOutcome.IGNORED_UNPARSEABLE), pipeline()(owner, signal("Promoción", "other")))
        assertEquals(1, saved.size)
    }

    @Test fun `unsupported source denied gate and foreign owner never persist`() = runTest {
        val extract = pipeline()
        assertEquals(CaptureExtractionResult.Ignored(CaptureSourceOutcome.IGNORED_UNSUPPORTED), extract(owner, signal(source = "com.fake.yape")))
        access = CaptureAccessDecision.ConsentMissing
        assertEquals(CaptureExtractionResult.AccessDenied, extract(owner, signal()))
        assertEquals(CaptureExtractionResult.AccessDenied, extract(UUID.randomUUID().toString(), signal()))
        assertTrue(seen.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test fun `revocation during lookup prevents persistence`() = runTest {
        val revokingResolver = CaptureInstrumentResolver { _, _, _ ->
            access = CaptureAccessDecision.ConsentMissing
            CaptureInstrumentResolution()
        }
        val extract = ExtractCaptureCandidate(catalog, gate, events, candidates, revokingResolver, categories,
            EvaluateMerchantAlias(), Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC), emptySet(), TestCaptureTransactions, CaptureResolutionFollowups(candidates, emptySet(), emptySet()))
        assertEquals(CaptureExtractionResult.AccessDenied, extract(owner, signal()))
        assertTrue(saved.isEmpty())
    }

    @Test fun `authorized alias and explicit category preference prefill only current owner`() = runTest {
        val merchant = MerchantCatalogEntry(MerchantId("merchant"), "Comercio", "comercio")
        val category = Category(CategoryId("category"), UserId(owner), null, CategoryOrigin.CUSTOM, true)
        every { categories.observeMerchantCatalog() } returns flowOf(listOf(merchant))
        every { categories.observeMerchantAliasRules(UserId(owner)) } returns flowOf(listOf(
            MerchantAliasRule(MerchantAliasRuleId("alias"), UserId(owner), "juan p.", merchant.id),
        ))
        every { categories.observeCategories(UserId(owner)) } returns flowOf(listOf(category))
        every { categories.observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(listOf(
            MerchantCategoryPreference(UserId(owner), merchant.id, category.id),
        ))
        val candidate = (pipeline()(owner, signal()) as CaptureExtractionResult.Created).candidate
        assertEquals("merchant", candidate.merchantId)
        assertEquals("category", candidate.categoryId)
        assertEquals("Juan P.", candidate.merchantSourceText)
    }

    @Test fun `post processors run by order and see latest persisted record`() = runTest {
        val calls = mutableListOf<Int>()
        fun processor(priority: Int) = object : CaptureCandidatePostProcessor {
            override val order = priority
            override suspend fun process(candidate: CaptureCandidateRecord) { calls += priority }
        }
        pipeline(setOf(processor(100), processor(0)))(owner, signal())
        assertEquals(listOf(0, 100), calls)
    }

    @Test fun `ingress does not read extras until supported source and owner pass the gate`() = runTest {
        val ingress = NotificationCaptureIngress(catalog, gate, pipeline())
        var reads = 0
        ingress.receive("com.fake.yape", owner) { reads++; signal() }
        access = CaptureAccessDecision.PremiumRequired
        ingress.receive(CaptureSourceCatalog.DEBUG_SHELL, owner) { reads++; signal() }
        access = CaptureAccessDecision.PermissionMissing
        ingress.receive(CaptureSourceCatalog.DEBUG_SHELL, owner) { reads++; signal() }
        assertEquals(0, reads)
        access = CaptureAccessDecision.Allowed(EffectiveEntitlement(true, null))
        ingress.receive(CaptureSourceCatalog.DEBUG_SHELL, owner) { reads++; signal() }
        assertEquals(1, reads)
        assertEquals(1, saved.size)
    }

    @Test fun `non completed operations never reach scoring or registration even with all fields resolvable`() = runTest {
        val merchant = MerchantCatalogEntry(MerchantId("merchant"), "PLAZA VEA", "plaza vea")
        every { categories.observeMerchantCatalog() } returns flowOf(listOf(merchant))
        every { categories.observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(listOf(
            MerchantCategoryPreference(UserId(owner), merchant.id, CategoryId("category")),
        ))
        every { categories.observeCategories(UserId(owner)) } returns flowOf(listOf(
            Category(CategoryId("category"), UserId(owner), null, CategoryOrigin.CUSTOM, true),
        ))
        instrument = CaptureInstrumentResolution(CaptureInstrumentKind.DEBIT_ACCOUNT, "bank", "card")
        val processor = mockk<CaptureCandidatePostProcessor>(relaxed = true)
        val states = listOf("rechazada", "DENEGADA", "fallida", "anulada", "CANCELADA", "pendiente", "revertida", "no procesada", "NO FUE PROCESADA")
        for ((i, state) in states.withIndex()) {
            val signal = CaptureSignal(CaptureProvider.BCP, "com.bcp.bank.bcp", "negative-$i", now, "BCP",
                "Compra $state de S/ 120.00 en PLAZA VEA con tu tarjeta debito terminada en 1234 el 28/10/2026 10:35")
            assertEquals(CaptureExtractionResult.Ignored(CaptureSourceOutcome.IGNORED_UNPARSEABLE), pipeline(setOf(processor))(owner, signal))
        }
        assertTrue(saved.isEmpty())
        coVerify(exactly = 0) { processor.process(any()) }
    }

    @Test fun `failure before persistence leaves fingerprint available for a retry`() = runTest {
        val source = signal()
        val failingResolver = CaptureInstrumentResolver { _, _, _ -> error("transient lookup") }
        val failing = ExtractCaptureCandidate(catalog, gate, events, candidates, failingResolver, categories,
            EvaluateMerchantAlias(), Clock.systemUTC(), emptySet(), TestCaptureTransactions, CaptureResolutionFollowups(candidates, emptySet(), emptySet()))
        try { failing(owner, source); fail("expected lookup failure") } catch (_: IllegalStateException) { }
        assertTrue(seen.isEmpty())
        assertTrue(pipeline()(owner, source) is CaptureExtractionResult.Created)
        assertEquals(1, saved.size)
    }

    @Test fun `cancellation during lookup does not consume the fingerprint`() = runTest {
        val cancellingResolver = CaptureInstrumentResolver { _, _, _ -> throw kotlinx.coroutines.CancellationException("cancelled callback") }
        val interrupted = ExtractCaptureCandidate(catalog, gate, events, candidates, cancellingResolver, categories,
            EvaluateMerchantAlias(), Clock.systemUTC(), emptySet(), TestCaptureTransactions, CaptureResolutionFollowups(candidates, emptySet(), emptySet()))
        try { interrupted(owner, signal()); fail("expected cancellation") } catch (_: kotlinx.coroutines.CancellationException) { }
        assertTrue(terminalEvents.isEmpty())
        assertTrue(pipeline()(owner, signal()) is CaptureExtractionResult.Created)
    }

    @Test fun `duplicate after post processing failure resumes the existing durable candidate`() = runTest {
        var attempts = 0
        val processor = object : CaptureCandidatePostProcessor {
            override val order = 0
            override suspend fun process(candidate: CaptureCandidateRecord) {
                attempts++
                if (attempts == 1) error("process died after candidate commit")
            }
        }
        val extract = pipeline(setOf(processor))
        try { extract(owner, signal()); fail("expected processor failure") } catch (_: IllegalStateException) { }
        assertEquals(1, saved.size)
        assertTrue(saved.values.single().followupsPending and CaptureFollowup.POST_PROCESS != 0)
        assertEquals(CaptureExtractionResult.Ignored(CaptureSourceOutcome.DUPLICATE), extract(owner, signal()))
        assertEquals(2, attempts)
        assertEquals(0, saved.values.single().followupsPending and CaptureFollowup.POST_PROCESS)
        coVerify(exactly = 1) { candidates.insertIfAbsent(any()) }
    }

    @Test fun `ingress checks live permission again after the suspended gate`() = runTest {
        val revokedGate = object : CaptureAccessGate {
            override fun currentOwnerId() = owner
            override suspend fun evaluateCurrent() = access
            override suspend fun evaluate(ownerId: String) = access
            override fun liveDenial(ownerId: String) = CaptureAccessDecision.PermissionMissing
        }
        var reads = 0
        NotificationCaptureIngress(catalog, revokedGate, pipeline()).receive(CaptureSourceCatalog.DEBUG_SHELL, owner) { reads++; signal() }
        assertEquals(0, reads)
    }
}
