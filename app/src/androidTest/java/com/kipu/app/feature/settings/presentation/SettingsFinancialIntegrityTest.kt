package com.kipu.app.feature.settings.presentation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.capture.data.VerifiedCaptureAccessGate
import com.kipu.app.feature.capture.data.local.RoomCaptureCandidateStore
import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.usecase.EvaluateMerchantAlias
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.local.*
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.settings.data.OfflineFirstProfilePreferencesRepository
import com.kipu.app.feature.settings.data.local.UserProfileCacheEntity
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.mockk.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** HU-04.3 / HU-05.2 / HU-05.4: real Room ledger and real preference/permission paths. */
@RunWith(AndroidJUnit4::class)
class SettingsFinancialIntegrityTest {
    private lateinit var db: KipuDatabase
    private lateinit var register: RegisterTransaction
    private lateinit var gate: VerifiedCaptureAccessGate
    private lateinit var candidates: RoomCaptureCandidateStore
    private val owner = UUID.randomUUID()
    private val ownerId = owner.toString()
    private val accountId = UUID.randomUUID().toString()
    private val stores = ViewModelStore()
    private val source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT
    private var deviceListener = DeviceAuthorization.GRANTED
    private var ownNotifications = DeviceAuthorization.GRANTED
    private val session = mockk<SessionCoordinator>(relaxed = true) {
        every { currentOwner } returns LocalOwner(ownerId, false)
        every { localAccess } returns MutableStateFlow<LocalAccess>(LocalAccess.Available(ownerId, RemoteSession.Absent))
    }
    private val categories = mockk<CategoriesRepository> {
        every { observePremiumVerified(com.kipu.app.core.finance.domain.model.UserId(ownerId)) } returns flowOf(true)
    }
    private val permissions = object : PermissionSourceGateway {
        override fun checkDeviceAuthorization(source: PermissionSource) = when (source) {
            PermissionSource.OWN_NOTIFICATIONS -> ownNotifications
            PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> deviceListener
            PermissionSource.YAPE_RECEIPT_OCR -> DeviceAuthorization.NOT_APPLICABLE
        }
        override fun createSettingsIntent(source: PermissionSource) = Intent()
        override suspend fun refreshAndSaveDeviceAuthorizations() = PermissionSource.entries.associateWith(::checkDeviceAuthorization)
    }

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.movementDao()
        val balance = BalanceProjectionStore(dao)
        val repository = OfflineFirstMovementRepository(
            MovementLocalDataSource(db, dao, balance), balance, TransactionRequestHasher(), MovementRevisionRequestHasher(),
            mockk(relaxed = true), db.accountDao(), db.cardDao(), db.categoryDao(), db.merchantCatalogDao(),
        )
        register = RegisterTransaction(repository, RegisterTransactionValidator())
        candidates = RoomCaptureCandidateStore(db, db.captureCandidateDao())
        gate = VerifiedCaptureAccessGate(session, permissions, db.permissionConsentDao(), db.featureAccessCacheDao(),
            EffectiveEntitlementEvaluator { _, _ -> OfflineEntitlementLeaseDecision.Allowed(Long.MAX_VALUE) })
        db.featureAccessCacheDao().putVerified(FeatureAccessCacheEntity(userId = owner, policyVersion = 1, effectiveTier = "PREMIUM"))
        db.accountDao().insert(AccountEntity(
            id = accountId, userId = ownerId, creationOperationId = UUID.randomUUID().toString(), alias = "Caja PEN",
            type = "CASH", currency = "PEN", presetId = null, color = null, icon = null,
            initialBalanceMinorUnits = 0L, openedAt = 1L, createdAt = 1L, updatedAt = 1L,
        ))
    }

    @After fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) { stores.clear() }
        db.close()
    }

    private suspend fun <T : ViewModel> keep(key: String, create: () -> T): T = withContext(Dispatchers.Main) {
        create().also { stores.put(key, it) }
    }

    private suspend fun permissionViewModel(): PermissionsViewModel {
        val vm = keep("permissions") { PermissionsViewModel(db.permissionConsentDao(), permissions, session, gate, categories) }
        withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
        return vm
    }

    private fun command(amount: Long = 12_345L, currency: String = "PEN", account: String = accountId) = RegisterTransactionCommand(
        idempotencyKey = UUID.randomUUID().toString(), userId = ownerId, type = MovementType.INCOME,
        amountMinor = amount, currency = currency, sourceAccountId = account, occurredAt = 10_000L,
    )

    @Test fun changingPreferredCurrencyPreservesExistingMovementsLedgerAndBalances() = runBlocking {
        val usdAccount = UUID.randomUUID().toString()
        db.accountDao().insert(db.accountDao().getById(ownerId, accountId)!!.copy(
            id = usdAccount, creationOperationId = UUID.randomUUID().toString(), alias = "Caja USD", currency = "USD"))
        val pen = register(command()) as RegisterTransactionResult.Success
        val usd = register(command(6_789L, "USD", usdAccount)) as RegisterTransactionResult.Success
        val beforeRows = db.movementDao().observeTransactions(ownerId).first()
        val beforePenLedger = db.movementDao().getLedgerEntriesForTransaction(ownerId, pen.transaction.id)
        val beforeUsdLedger = db.movementDao().getLedgerEntriesForTransaction(ownerId, usd.transaction.id)
        val beforePenBalance = db.movementDao().getBalanceProjection(ownerId, accountId)
        val beforeUsdBalance = db.movementDao().getBalanceProjection(ownerId, usdAccount)
        db.profilePreferencesDao().putProfile(UserProfileCacheEntity(owner, "Owner", currencyCode = "PEN"))
        val sessionProvider = mockk<AuthenticatedSessionProvider> { coEvery { currentSession() } returns null }
        val repository = OfflineFirstProfilePreferencesRepository(db.profilePreferencesDao(), mockk<SupabaseClient>(), sessionProvider)
        val vm = keep("settings") { SettingsViewModel(repository, session, mockk(relaxed = true)) }
        withTimeout(5_000) { vm.uiState.first { !it.isLoading && it.currencyCode == "PEN" } }

        withContext(Dispatchers.Main) { vm.onCurrencyCodeChanged("USD"); vm.savePreferences() }
        withTimeout(5_000) { db.profilePreferencesDao().observeProfile(owner).first { it?.currencyCode == "USD" } }
        withTimeout(5_000) { vm.uiState.first { !it.isSaving && it.currencyCode == "USD" } }

        assertEquals("USD", repository.getProfile(owner)?.currencyCode)
        assertEquals(1, db.profilePreferencesDao().pendingOutboxCount(owner))
        assertEquals(beforeRows, db.movementDao().observeTransactions(ownerId).first())
        assertEquals(beforePenLedger, db.movementDao().getLedgerEntriesForTransaction(ownerId, pen.transaction.id))
        assertEquals(beforeUsdLedger, db.movementDao().getLedgerEntriesForTransaction(ownerId, usd.transaction.id))
        assertEquals(beforePenBalance, db.movementDao().getBalanceProjection(ownerId, accountId))
        assertEquals(beforeUsdBalance, db.movementDao().getBalanceProjection(ownerId, usdAccount))
        assertEquals(12_345L, db.movementDao().calculateLedgerSumForAccount(ownerId, accountId))
        assertEquals(6_789L, db.movementDao().calculateLedgerSumForAccount(ownerId, usdAccount))
        assertEquals("PEN", db.accountDao().getById(ownerId, accountId)?.currency)
        assertEquals("USD", db.accountDao().getById(ownerId, usdAccount)?.currency)
    }

    @Test fun deniedNotificationPermissionStillAllowsManualRegistration() = runBlocking {
        val vm = permissionViewModel()
        withContext(Dispatchers.Main) { vm.grantConsent(PermissionSource.OWN_NOTIFICATIONS) }
        withTimeout(5_000) { vm.uiState.first { it.items.any { item -> item.source == PermissionSource.OWN_NOTIFICATIONS && item.consentState == ConsentState.GRANTED } } }
        // Android rejects the optional runtime permission after Kipu's explanation/consent.
        ownNotifications = DeviceAuthorization.DENIED
        withContext(Dispatchers.Main) { vm.refresh() }
        withTimeout(5_000) { vm.uiState.first { it.items.any { item -> item.source == PermissionSource.OWN_NOTIFICATIONS && item.deviceAuth == DeviceAuthorization.DENIED } } }
        assertFalse(vm.uiState.value.items.first { it.source == PermissionSource.OWN_NOTIFICATIONS }.isProcessingAuthorized)

        val result = register(command())

        assertTrue(result is RegisterTransactionResult.Success)
        val saved = db.movementDao().getTransactionById(ownerId, (result as RegisterTransactionResult.Success).transaction.id)
        assertNotNull(saved)
        assertEquals(12_345L, saved?.amountMinor)
        assertEquals("MANUAL", saved?.captureSource)
        assertEquals(12_345L, db.movementDao().calculateLedgerSumForAccount(ownerId, accountId))
        assertEquals(12_345L, db.movementDao().getBalanceProjection(ownerId, accountId)?.balanceMinor)
    }

    @Test fun revokedSourceStopsNewCaptureAndPreservesConfirmedHistory() = runBlocking { verifyRevocation(false) }
    @Test fun revokedAndroidPermissionStopsNewCaptureAndPreservesConfirmedHistory() = runBlocking { verifyRevocation(true) }

    private suspend fun verifyRevocation(revokeAndroid: Boolean) {
        val vm = permissionViewModel()
        withContext(Dispatchers.Main) { vm.grantConsent(source) }
        withTimeout(5_000) { vm.uiState.first { it.items.any { item -> item.source == source && item.isProcessingAuthorized } } }
        assertTrue(gate.evaluateCurrent() is CaptureAccessDecision.Allowed)
        val transaction = (register(command().copy(captureSource = MovementSource.NOTIFICATION)) as RegisterTransactionResult.Success).transaction
        val candidate = CaptureCandidateRecord(
            id = UUID.randomUUID().toString(), ownerId = ownerId, sourcePackage = "com.bcp.innovacxion.yapeapp",
            provider = CaptureProvider.YAPE, sourceEventFingerprint = "already-confirmed", status = CaptureCandidateStatus.PENDING,
            direction = CaptureDirection.INCOME, amountMinor = 12_345L, currency = "PEN", occurredAt = 10_000L,
            occurredAtInferred = false, merchantSourceText = null, merchantId = null, categoryId = null, cardLast4 = null,
            instrumentKind = null, accountId = accountId, cardId = null, instrumentAmbiguous = false,
            confidence = CaptureConfidenceLevel.HIGH, confidenceReasons = emptyList(), creditMode = null, installmentCount = null,
            resolvedTransactionId = null, resolutionKey = null, evidenceSnippet = null, evidenceExpiresAt = null,
            userCorrectedFields = emptyList(), createdAt = 10_000L, updatedAt = 10_000L, resolvedAt = null,
        )
        assertTrue(candidates.insertIfAbsent(candidate))
        assertTrue(candidates.markResolved(ownerId, candidate.id, CaptureCandidateStatus.CONFIRMED, transaction.id, "confirmation-key", emptyList(), 11_000L))
        val beforeCandidate = candidates.get(ownerId, candidate.id)
        val beforeRows = db.movementDao().observeTransactions(ownerId).first()
        val beforeLedger = db.movementDao().getLedgerEntriesForTransaction(ownerId, transaction.id)
        val beforeBalance = db.movementDao().getBalanceProjection(ownerId, accountId)

        if (revokeAndroid) {
            deviceListener = DeviceAuthorization.DENIED
            withContext(Dispatchers.Main) { vm.refresh() }
        } else withContext(Dispatchers.Main) { vm.revokeConsent(source) }
        withTimeout(5_000) { vm.uiState.first { it.items.any { item -> item.source == source && !item.isProcessingAuthorized } } }
        assertEquals(if (revokeAndroid) CaptureAccessDecision.PermissionMissing else CaptureAccessDecision.ConsentMissing, gate.evaluateCurrent())

        // The real extraction entry point must deny before parsing/resolving or touching stores.
        val events = mockk<CaptureSourceEventStore>()
        val newCandidates = mockk<CaptureCandidateStore>()
        val resolver = mockk<CaptureInstrumentResolver>()
        val extractor = ExtractCaptureCandidate(CaptureSourceCatalog(false), gate, events, newCandidates,
            resolver, categories, EvaluateMerchantAlias(), Clock.systemUTC(), emptySet(), mockk(), mockk())
        repeat(2) { index ->
            assertEquals(CaptureExtractionResult.AccessDenied, extractor(ownerId, CaptureSignal(CaptureProvider.YAPE,
                "com.bcp.innovacxion.yapeapp", "new-$index", 12_000L, "Yape", "Recibiste S/ 20.00")))
        }
        coVerify(exactly = 0) { resolver.resolve(any(), any(), any()) }
        coVerify(exactly = 0) { events.recordIfAbsent(any()) }
        coVerify(exactly = 0) { newCandidates.insertIfAbsent(any()) }
        assertEquals(beforeCandidate, candidates.get(ownerId, candidate.id))
        assertEquals(beforeRows, db.movementDao().observeTransactions(ownerId).first())
        assertEquals(beforeLedger, db.movementDao().getLedgerEntriesForTransaction(ownerId, transaction.id))
        assertEquals(beforeBalance, db.movementDao().getBalanceProjection(ownerId, accountId))
        assertEquals(12_345L, db.movementDao().calculateLedgerSumForAccount(ownerId, accountId))
    }
}
