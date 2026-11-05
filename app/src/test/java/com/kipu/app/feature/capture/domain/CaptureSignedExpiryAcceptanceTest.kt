package com.kipu.app.feature.capture.domain

import com.kipu.app.core.session.*
import com.kipu.app.feature.capture.data.VerifiedCaptureAccessGate
import com.kipu.app.feature.plans.data.entitlement.*
import com.kipu.app.feature.plans.data.local.*
import com.kipu.app.feature.plans.domain.OfflineEntitlementLeasePolicy
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import com.kipu.app.feature.settings.data.local.*
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.*
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import io.mockk.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.*
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** HU-45 E4 / HU-47 E5: real signature verification and monotonic expiry, without remote state. */
class CaptureSignedExpiryAcceptanceTest {
    private val owner = UUID.randomUUID()
    private val serverTime = CAPTURE_NOW
    private var elapsed = 10_000L
    private val lifetime = 60_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(serverTime), ZoneOffset.UTC)
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val install = object : InstallationSigningKeyProvider {
        override fun getOrCreatePublicIdentity() = InstallationPublicIdentity("public", "device")
    }
    private val verifier = OfflineEntitlementGrantVerifier(
        OfflineEntitlementGrantPublicKeyResolver { if (it == "test") keys.public else null }, install,
        OfflineEntitlementGrantBase64UrlDecoder { Base64.getUrlDecoder().decode(it) },
    )
    private val evaluator = OfflineEntitlementLeaseEvaluator(verifier, install,
        OfflineEntitlementClock { OfflineEntitlementClockReading(elapsed, 7) }, OfflineEntitlementLeasePolicy())
    private fun cache(): FeatureAccessCacheEntity {
        val end = Instant.ofEpochMilli(serverTime + lifetime)
        val payload = """{"version":1,"keyId":"test","grantId":"d45373b2-9b32-4e19-a847-f585e59c50ba","userId":"$owner","installationKeyThumbprint":"device","policyVersion":1,"tier":"PREMIUM","serverVerifiedAt":"${Instant.ofEpochMilli(serverTime)}","entitlementEndsAt":"$end","notAfter":"$end"}""".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run { initSign(keys.private); update(payload); sign() }
        fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val grant = SignedOfflineEntitlementGrant(encode(payload), encode(signature), "test")
        return FeatureAccessCacheEntity.fromVerifiedGrant(owner, requireNotNull(verifier.verify(grant, owner.toString())), grant, 10_000L, 7)
    }
    private val session = mockk<SessionCoordinator> {
        every { localAccess } returns MutableStateFlow<LocalAccess>(LocalAccess.Available(owner.toString(), RemoteSession.Absent))
    }
    private val consent = mockk<PermissionConsentDao> {
        coEvery { findAccountConsent(owner, any()) } returns AccountSourceConsentEntity(owner,
            PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name, ConsentState.GRANTED.name, CapabilityState.ALLOWED.name)
    }
    private val permissions = mockk<PermissionSourceGateway> {
        every { checkDeviceAuthorization(any()) } returns DeviceAuthorization.GRANTED
    }
    private val cacheDao = mockk<FeatureAccessCacheDao> { coEvery { get(owner) } returns cache() }
    private val gate = VerifiedCaptureAccessGate(session, permissions, consent, cacheDao, evaluator)
    private val store = FakeCaptureCandidateStore()
    private val commands = mutableListOf<RegisterTransactionCommand>()
    private val register = mockk<RegisterTransaction> {
        coEvery { this@mockk.invoke(any()) } answers {
            val command = firstArg<RegisterTransactionCommand>(); commands += command
            RegisterTransactionResult.Success(Transaction("tx", command.userId, command.type, command.amountMinor,
                command.currency, sourceAccountId = command.sourceAccountId, categoryId = command.categoryId, occurredAt = command.occurredAt))
        }
    }
    private val confirm = testConfirmCaptureCandidate(store, gate, register, mockk<ConfirmCreditPurchase>(),
        { _, _, _ -> null }, clock, emptySet())

    @Test fun signedPremiumExpiresAtBoundaryButManualEntryAndDedupeRemainAvailable() = runTest {
        val candidate = captureCandidate(owner.toString())
        store.insertIfAbsent(candidate)
        elapsed += lifetime - 1
        assertTrue(gate.evaluateCurrent() is CaptureAccessDecision.Allowed)
        elapsed++
        assertEquals(CaptureAccessDecision.PremiumRequired, gate.evaluateCurrent())
        val command = ConfirmCaptureCommand.fromCandidate(candidate)!!
        assertEquals(CaptureConfirmationResult.AccessDenied(CaptureAccessDecision.PremiumRequired), confirm(command))
        assertTrue(commands.isEmpty())
        assertEquals(CaptureCandidateStatus.PENDING, store.get(candidate.ownerId, candidate.id)!!.status)
        assertEquals(CaptureConfirmationResult.Registered("tx", false), confirm(command, CaptureConfirmationMode.MANUAL_DRAFT))
        assertEquals(CaptureConfirmationResult.Registered("tx", true), confirm(command, CaptureConfirmationMode.MANUAL_DRAFT))
        assertEquals(CaptureResolutionKey.of(candidate.ownerId, candidate.id), commands.single().idempotencyKey)
    }

    @Test fun premiumExpiresBetweenCaptureAndAutomaticSaveAndLeavesPendingEvidence() = runTest {
        val candidate = captureCandidate(owner.toString())
        assertTrue(gate.evaluateCurrent() is CaptureAccessDecision.Allowed)
        store.insertIfAbsent(candidate)
        // The wall clock stays fixed: only trusted monotonic elapsed time advances.
        elapsed += lifetime
        assertEquals(AutoRegisterResult.AccessDenied, AutoRegisterCaptureCandidate(store, confirm, clock)(candidate))
        val pending = store.get(candidate.ownerId, candidate.id)!!
        assertEquals(CaptureCandidateStatus.PENDING, pending.status)
        assertEquals(candidate.evidenceSnippet, pending.evidenceSnippet)
        assertNull(pending.resolvedTransactionId)
        assertTrue(CaptureReason.AUTO_REGISTER_DENIED in pending.confidenceReasons)
        coVerify(exactly = 0) { register(any()) }
    }
    @Test fun expiredSignedGrantBlocksFreshNotificationBeforeAnyEvidenceOrCandidateWrite() = runTest {
        assertTrue(gate.evaluateCurrent() is CaptureAccessDecision.Allowed)
        elapsed += lifetime
        val events = mockk<CaptureSourceEventStore>()
        val candidates = mockk<CaptureCandidateStore>()
        val extract = ExtractCaptureCandidate(CaptureSourceCatalog(false), gate, events, candidates,
            mockk(), mockk(), com.kipu.app.feature.categories.domain.usecase.EvaluateMerchantAlias(), clock,
            emptySet(), TestCaptureTransactions, CaptureResolutionFollowups(candidates, emptySet(), emptySet()))
        val signal = CaptureSignal(CaptureProvider.BCP, "com.bcp.bank.bcp", "fresh-key", serverTime, "BCP", "Consumo de S/ 10.00")
        assertEquals(CaptureExtractionResult.AccessDenied, extract(owner.toString(), signal))
        coVerify(exactly = 0) { events.recordIfAbsent(any()) }
        coVerify(exactly = 0) { candidates.insertIfAbsent(any()) }
        confirmVerified(events, candidates)
    }

}
