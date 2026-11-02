package com.kipu.app.feature.setup

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.model.AuthCredentials
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.auth.domain.model.CooldownState
import com.kipu.app.feature.auth.domain.model.OtpException
import com.kipu.app.feature.auth.domain.model.OtpFailure
import com.kipu.app.feature.auth.presentation.SessionEntryCurtain
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import io.mockk.*
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.data.PlanOnboardingStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = SetupDraftStore()
    private val curtain = SessionEntryCurtain()
    private val owner = "00000000-0000-0000-0000-0000000000aa"
    private val sessions = mockk<SessionCoordinator>(relaxed = true)
    private val onboarding = mockk<PlanOnboardingStore>(relaxed = true)
    private val finalizer = mockk<SetupFinalizer>()

    private var registerResult: Result<AuthResult> = Result.success(AuthResult.ConfirmationRequired("ana@correo.com"))
    private var verifyResult: Result<AuthResult> = Result.success(AuthResult.Success("00000000-0000-0000-0000-0000000000aa"))
    private var resendResult: Result<Unit> = Result.success(Unit)
    private var registerCalls = 0
    private var lastCode: String? = null

    private val auth = object : AuthRepository {
        override val cooldownState: StateFlow<CooldownState> = MutableStateFlow(CooldownState())
        override suspend fun register(credentials: AuthCredentials): Result<AuthResult> { registerCalls++; return registerResult }
        override suspend fun signIn(credentials: AuthCredentials): Result<AuthResult> = Result.failure(IllegalStateException())
        override suspend fun verifyOtp(email: String, code: String): Result<AuthResult> { lastCode = code; return verifyResult }
        override suspend fun resendOtp(email: String): Result<Unit> = resendResult
        override suspend fun signOut(explicit: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(): Result<AuthResult?> = Result.success(null)
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { sessions.currentOwner } returns null
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(): SetupViewModel {
        coEvery { finalizer.apply(any(), any(), any()) } returns Result.success(Unit)
        return SetupViewModel(store, auth, curtain, finalizer, sessions, onboarding)
    }

    @Test fun `bank choice preselects BCP and names the account after it`() {
        val vm = vm()
        vm.chooseKind(SetupAccountKind.BANK)
        val account = store.draft.value.account
        assertEquals(AccountPreset.BCP, account.preset)
        assertEquals("BCP", account.alias)
        vm.choosePreset(AccountPreset.BBVA)
        assertEquals("BBVA", store.draft.value.account.alias)
        vm.onAlias("Mi sueldo")
        vm.choosePreset(AccountPreset.INTERBANK)
        assertEquals("Mi sueldo", store.draft.value.account.alias)
    }

    @Test fun `balance text accepts at most two decimals and never negatives`() {
        val base = SetupAccountDraft(kind = SetupAccountKind.CASH, preset = AccountPreset.CASH, alias = "Efectivo")
        assertEquals(0L, base.balanceMinorUnits)
        assertEquals(125_050L, base.copy(balanceText = "1,250.50").balanceMinorUnits)
        assertNull(base.copy(balanceText = "10.123").balanceMinorUnits)
        assertNull(base.copy(balanceText = "-5").balanceMinorUnits)
        assertNull(base.copy(balanceText = "abc").balanceMinorUnits)
        assertTrue(base.isComplete)
        assertFalse(base.copy(alias = " ").isComplete)
        assertEquals(AccountType.CASH, base.accountType)
        assertEquals(Currency.PEN, base.currency)
    }

    @Test fun `the last active category cannot be hidden`() {
        val vm = vm()
        val ids = vm.categoryCatalog.map { it.id }
        val allButLast = ids.dropLast(1)
        allButLast.forEach { assertTrue(vm.toggleCategory(it)) }
        assertFalse(vm.toggleCategory(ids.last()))
        assertEquals(allButLast.toSet(), store.draft.value.hiddenCategoryIds)
        assertTrue(vm.toggleCategory(ids[0]))
        assertEquals(allButLast.drop(1).toSet(), store.draft.value.hiddenCategoryIds)
    }

    @Test fun `setup offers the full system catalog and hiding all keeps the first`() {
        val vm = vm()
        assertEquals(InitialCategoryCatalog.entries.size, vm.categoryCatalog.size)
        assertEquals(12, vm.categoryCatalog.map { it.id }.toSet().size)
        vm.setAllCategories(false)
        assertEquals(vm.categoryCatalog.drop(1).map { it.id }.toSet(), store.draft.value.hiddenCategoryIds)
        vm.setAllCategories(true)
        assertTrue(store.draft.value.hiddenCategoryIds.isEmpty())
    }

    @Test fun `verifying opens setup without registering or saving choices`() = runTest(dispatcher) {
        store.begin("ana@correo.com")
        val vm = vm()
        vm.beginConfirmation()
        vm.onCodeChanged("48 29-01")
        runCurrent()
        assertEquals("482901", lastCode)
        assertEquals(SetupEvent.Verified(owner), vm.events.first())
        assertTrue(vm.otp.value.verified)
        assertFalse(vm.otp.value.canSubmit)
        assertEquals(0, registerCalls)
        coVerify(exactly = 0) { finalizer.apply(any(), any(), any()) }
        assertNull(curtain.state.value)
        assertEquals("ana@correo.com", store.draft.value.email)
    }

    @Test fun `authenticated summary saves once then celebrates without registering`() = runTest(dispatcher) {
        every { sessions.currentOwner } returns LocalOwner(owner)
        val vm = vm()
        vm.chooseKind(SetupAccountKind.CASH)
        vm.confirmAll(); vm.confirmAll()
        runCurrent()
        coVerify(exactly = 1) { finalizer.apply(owner, any(), any()) }
        coVerify { onboarding.setupCompleted(owner) }
        assertEquals(0, registerCalls)
        assertTrue(curtain.state.value!!.authenticated)
        assertTrue(curtain.state.value!!.registering)
        assertEquals(SetupEvent.Completed(owner), vm.events.first())
    }

    @Test fun `summary without active owner cannot save or finish setup`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseKind(SetupAccountKind.CASH)
        vm.confirmAll(); runCurrent()
        assertTrue(vm.save.value.error != null)
        coVerify(exactly = 0) { finalizer.apply(any(), any(), any()) }
        coVerify(exactly = 0) { onboarding.setupCompleted(any()) }
    }

    @Test fun `wrong code clears the cells and reports the attempts left`() = runTest(dispatcher) {
        verifyResult = Result.failure(OtpException(OtpFailure.INVALID, attemptsLeft = 3))
        store.begin("ana@correo.com")
        val vm = vm()
        vm.beginConfirmation(); advanceUntilIdle()
        vm.onCodeChanged("111111"); advanceUntilIdle()
        val state = vm.otp.value
        assertEquals(OtpFailure.INVALID, state.failure)
        assertEquals(3, state.attemptsLeft)
        assertEquals("", state.code)
    }

    @Test fun `locked code counts down and unlocks`() = runTest(dispatcher) {
        verifyResult = Result.failure(OtpException(OtpFailure.LOCKED, retryAfterSeconds = 3))
        store.begin("ana@correo.com")
        val vm = vm()
        vm.beginConfirmation(); advanceUntilIdle()
        vm.onCodeChanged("111111"); advanceTimeBy(1)
        assertEquals(OtpFailure.LOCKED, vm.otp.value.failure)
        advanceTimeBy(3_500)
        assertEquals(0, vm.otp.value.lockedSeconds)
        assertNull(vm.otp.value.failure)
    }

    @Test fun `resend is blocked while the timer runs and works after`() = runTest(dispatcher) {
        store.begin("ana@correo.com")
        val vm = vm()
        vm.beginConfirmation(); runCurrent()
        vm.resend()
        assertNull(vm.otp.value.notice)
        advanceTimeBy(61_000)
        assertEquals(0, vm.otp.value.resendSeconds)
        vm.resend(); advanceTimeBy(10)
        assertEquals(OtpNotice.RESENT, vm.otp.value.notice)
        assertEquals(60, vm.otp.value.resendSeconds)
    }

    @Test fun `failed summary stays pending and retry only saves configuration`() = runTest(dispatcher) {
        every { sessions.currentOwner } returns LocalOwner(owner)
        coEvery { finalizer.apply(any(), any(), any()) } returns Result.failure(IllegalStateException("boom")) andThen Result.success(Unit)
        val vm = SetupViewModel(store, auth, curtain, finalizer, sessions, onboarding)
        vm.chooseKind(SetupAccountKind.CASH)
        vm.confirmAll(); runCurrent()
        assertEquals("boom", vm.save.value.error)
        coVerify(exactly = 0) { onboarding.setupCompleted(any()) }
        assertNull(curtain.state.value)
        vm.confirmAll(); runCurrent()
        assertNull(vm.save.value.error)
        assertTrue(vm.events.first() is SetupEvent.Completed)
        assertEquals(0, registerCalls)
    }

    @Test fun `unconfirmed login permits resend immediately`() = runTest(dispatcher) {
        store.begin("ana@correo.com", codeSent = false)
        val vm = vm()
        vm.beginConfirmation()
        assertEquals(0, vm.otp.value.resendSeconds)
        vm.resend(); runCurrent()
        assertEquals(OtpNotice.RESENT, vm.otp.value.notice)
    }
}
