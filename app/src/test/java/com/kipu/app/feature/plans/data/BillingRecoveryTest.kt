package com.kipu.app.feature.plans.data

import com.kipu.app.feature.plans.data.billing.BillingRepository
import com.kipu.app.feature.plans.data.billing.PlayBillingGateway
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.InstallationPublicIdentity
import com.kipu.app.feature.plans.data.entitlement.InstallationSigningKeyProvider
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementClock
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementClockReading
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantVerifier
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.remote.VerifyPurchaseApi
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.domain.model.*
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingRecoveryTest {
    private data class RestoreFixture(
        val repository: BillingRepository,
        val api: VerifyPurchaseApi,
        val sessions: AuthenticatedSessionProvider,
        val accessCache: FeatureAccessCacheDao,
    )

    private val gateway = mockk<PlayBillingGateway> {
        every { purchaseUpdates } returns MutableSharedFlow<StorePurchaseUpdate>()
    }
    private fun repository(api: VerifyPurchaseApi = mockk(), sessions: AuthenticatedSessionProvider = mockk()) =
        BillingRepository(gateway, api, sessions, mockk(), mockk(), mockk(), mockk(), mockk())

    private fun verifiedPremium() = BillingVerificationResult.Verified(
        productId = "premium",
        providerPurchaseState = GooglePlayPurchaseState.PURCHASED,
        lifecycle = PurchaseLifecycle.ACTIVE,
        expiresAtEpochMillis = 1_800_000_000_000L,
        effectivePremium = true,
        acknowledgementPending = false,
        effectiveExpiresAtEpochMillis = 1_800_000_000_000L,
        offlineEntitlementGrant = SignedOfflineEntitlementGrant("payload", "signature", "key-1"),
    )

    private fun premiumRestoreFixture(): RestoreFixture {
        val session = AuthenticatedSession(UUID.fromString("a3000000-0000-4000-8000-000000000001"), "access-token")
        val sessions = mockk<AuthenticatedSessionProvider> {
            coEvery { currentSession() } returns session
        }
        val api = mockk<VerifyPurchaseApi> {
            coEvery { verify(any(), any(), any()) } returns verifiedPremium()
        }
        val accessCache = mockk<FeatureAccessCacheDao> {
            coEvery { putVerified(any()) } just Runs
            coEvery { putCommercialVerified(any(), any()) } returns true
        }
        val installationKeys = mockk<InstallationSigningKeyProvider> {
            every { getOrCreatePublicIdentity() } returns InstallationPublicIdentity("public-key", "thumbprint")
        }
        val clock = mockk<OfflineEntitlementClock> {
            every { read() } returns OfflineEntitlementClockReading(1_000L, 7)
        }
        val claims = OfflineEntitlementGrantClaims(
            version = 1,
            keyId = "key-1",
            grantId = "a3000000-0000-4000-8000-000000000002",
            userId = session.userId.toString(),
            installationKeyThumbprint = "thumbprint",
            policyVersion = 1,
            tier = "PREMIUM",
            serverVerifiedAtMillis = 1_700_000_000_000L,
            entitlementEndsAtMillis = 1_800_000_000_000L,
            notAfterMillis = 1_800_000_000_000L,
        )
        val grantVerifier = mockk<OfflineEntitlementGrantVerifier> {
            every { verify(any(), any()) } returns claims
        }
        val evaluator = mockk<EffectiveEntitlementEvaluator> {
            every { evaluate(any(), any()) } returns OfflineEntitlementLeaseDecision.Allowed(1_700_000_000_000L)
        }
        return RestoreFixture(
            repository = BillingRepository(gateway, api, sessions, accessCache, installationKeys, clock, grantVerifier, evaluator),
            api = api,
            sessions = sessions,
            accessCache = accessCache,
        )
    }


    @Test fun callbacksAreVerifiedWithoutScreenAndLateObserverOnlyReadsResult() = runTest {
        val updates = MutableSharedFlow<StorePurchaseUpdate>(extraBufferCapacity = 8)
        every { gateway.purchaseUpdates } returns updates
        val fixture = premiumRestoreFixture()
        fixture.repository.startProcessing(backgroundScope)
        fixture.repository.startProcessing(backgroundScope) // Repeated startup must not create another processor.
        updates.emit(StorePurchaseUpdate("premium", EphemeralPurchaseToken("pending-approved"), StorePurchaseState.PURCHASED))
        runCurrent()
        coVerify(exactly = 1) { fixture.api.verify(any(), any(), any()) }
        coVerify(exactly = 1) { fixture.accessCache.putCommercialVerified(any(), any()) }
        assertEquals(verifiedPremium(), fixture.repository.purchaseResults.first())
        coVerify(exactly = 1) { fixture.api.verify(any(), any(), any()) }
    }

    @Test fun lateScreenDoesNotReplayPreviousAccountsPremiumResult() = runTest {
        val updates = MutableSharedFlow<StorePurchaseUpdate>(extraBufferCapacity = 8)
        every { gateway.purchaseUpdates } returns updates
        val fixture = premiumRestoreFixture()
        fixture.repository.startProcessing(backgroundScope)
        updates.emit(StorePurchaseUpdate("premium", EphemeralPurchaseToken("owner-a"), StorePurchaseState.PURCHASED))
        runCurrent()
        coEvery { fixture.sessions.currentSession() } returns AuthenticatedSession(UUID.randomUUID(), "owner-b-token")
        val observed = mutableListOf<BillingVerificationResult>()
        backgroundScope.launch { fixture.repository.purchaseResults.collect { observed += it } }
        runCurrent()
        assertEquals(emptyList<BillingVerificationResult>(), observed)
    }

    @Test fun callbackFailureDoesNotStopApplicationProcessor() = runTest {
        val updates = MutableSharedFlow<StorePurchaseUpdate>(extraBufferCapacity = 8)
        every { gateway.purchaseUpdates } returns updates
        val fixture = premiumRestoreFixture()
        coEvery { fixture.api.verify(any(), any(), any()) } throws IllegalStateException("offline")
        fixture.repository.startProcessing(backgroundScope)
        updates.emit(StorePurchaseUpdate("premium", EphemeralPurchaseToken("first"), StorePurchaseState.PURCHASED))
        runCurrent()
        assertEquals(BillingVerificationResult.Retryable("VERIFICATION_UNAVAILABLE"), fixture.repository.purchaseResults.first())
        coEvery { fixture.api.verify(any(), any(), any()) } returns verifiedPremium()
        updates.emit(StorePurchaseUpdate("premium", EphemeralPurchaseToken("retry"), StorePurchaseState.PURCHASED))
        runCurrent()
        assertEquals(verifiedPremium(), fixture.repository.purchaseResults.first())
        coVerify(exactly = 1) { fixture.accessCache.putCommercialVerified(any(), any()) }
    }

    @Test fun restoredCanceledPaidSubscriptionInstallsVerifiedOfflineGrant() = runTest {
        val fixture = premiumRestoreFixture()
        val canceled = verifiedPremium().copy(
            providerPurchaseState = GooglePlayPurchaseState.CANCELLED,
            lifecycle = PurchaseLifecycle.CANCELED_ACTIVE,
        )
        coEvery { fixture.api.verify(any(), any(), any()) } returns canceled
        coEvery { gateway.recoverPurchaseUpdates() } returns listOf(
            StorePurchaseUpdate("premium", EphemeralPurchaseToken("canceled-paid"), StorePurchaseState.PURCHASED),
        )
        assertEquals(canceled, fixture.repository.restoreAndVerifyAccess())
        coVerify(exactly = 1) { fixture.accessCache.putCommercialVerified(match {
            it.entitlementExpiresAt?.toEpochMilli() == canceled.effectiveExpiresAtEpochMillis
        }, any()) }
    }

    @Test fun noPurchaseCompletesWithoutLoadingCatalogOrClearingAccess() = runTest {
        val fixture = premiumRestoreFixture()
        coEvery { gateway.recoverPurchaseUpdates() } returns emptyList()
        assertEquals(BillingVerificationResult.Retryable("NO_RECOVERABLE_PURCHASE"), fixture.repository.restoreAndVerifyAccess())
        coVerify(exactly = 0) { gateway.queryOffers(any()) }
        coVerify(exactly = 0) { fixture.api.verify(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.accessCache.putVerified(any()) }
    }

    @Test fun pendingPurchaseIsTerminalWithoutCreatingPremiumEvidence() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } returns listOf(StorePurchaseUpdate(productId = "premium", purchaseToken = null, state = StorePurchaseState.PENDING))
        assertEquals(BillingVerificationResult.PaymentPending("premium"), repository().restoreAndVerifyAccess())
    }

    @Test fun networkFailureHasAnExplicitRetryResult() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } throws IllegalStateException("offline")
        assertEquals(BillingVerificationResult.Retryable("VERIFICATION_UNAVAILABLE"), repository().restoreAndVerifyAccess())
    }

    @Test fun noPlatformCallbackCannotLeaveRecoveryLoadingForever() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } coAnswers { awaitCancellation() }
        assertEquals(BillingVerificationResult.Retryable("VERIFICATION_TIMEOUT"), repository().restoreAndVerifyAccess())
    }

    @Test fun restoreVerifiesEveryCurrentCandidateForTheSameAccount() = runTest {
        val fixture = premiumRestoreFixture()
        val session = fixture.sessions.currentSession()!!
        coEvery { gateway.recoverPurchaseUpdates() } returns listOf(
            StorePurchaseUpdate("premium-monthly", EphemeralPurchaseToken("candidate-token-a"), StorePurchaseState.PURCHASED),
            StorePurchaseUpdate("premium-annual", EphemeralPurchaseToken("candidate-token-b"), StorePurchaseState.PURCHASED),
        )

        val result = fixture.repository.restoreAndVerifyAccess()

        assertEquals(true, result.effectivePremium)
        coVerify(exactly = 2) {
            fixture.api.verify(session, match { it.restoreCandidate }, any())
        }
    }

    @Test fun restoreStopsWhenTheKipuOwnerChangesDuringVerification() = runTest {
        val fixture = premiumRestoreFixture()
        val ownerA = AuthenticatedSession(UUID.fromString("a3000000-0000-4000-8000-000000000001"), "owner-a")
        val ownerB = AuthenticatedSession(UUID.fromString("a3000000-0000-4000-8000-000000000003"), "owner-b")
        coEvery { fixture.sessions.currentSession() } returnsMany listOf(ownerA, ownerA, ownerB, ownerB)
        coEvery { fixture.api.verify(any(), any(), any()) } returns BillingVerificationResult.Retryable("UNAVAILABLE")
        coEvery { gateway.recoverPurchaseUpdates() } returns listOf(
            StorePurchaseUpdate("premium-monthly", EphemeralPurchaseToken("candidate-token-a"), StorePurchaseState.PURCHASED),
            StorePurchaseUpdate("premium-annual", EphemeralPurchaseToken("candidate-token-b"), StorePurchaseState.PURCHASED),
        )

        val result = fixture.repository.restoreAndVerifyAccess()

        assertEquals(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"), result)
        coVerify(exactly = 1) { fixture.api.verify(ownerA, any(), any()) }
        coVerify(exactly = 0) { fixture.api.verify(ownerB, any(), any()) }
    }

    @Test fun tokenOwnedByAnotherKipuAccountIsRejectedWithoutChangingLocalAccess() = runTest {
        val fixture = premiumRestoreFixture()
        coEvery { fixture.api.verify(any(), any(), any()) } returns BillingVerificationResult.Rejected("TOKEN_ACCOUNT_CONFLICT")
        coEvery { gateway.recoverPurchaseUpdates() } returns listOf(
            StorePurchaseUpdate("premium-monthly", EphemeralPurchaseToken("foreign-token"), StorePurchaseState.PURCHASED),
        )

        assertEquals(BillingVerificationResult.Rejected("TOKEN_ACCOUNT_CONFLICT"), fixture.repository.restoreAndVerifyAccess())
        coVerify(exactly = 1) { fixture.api.verify(any(), any(), any()) }
    }
}
