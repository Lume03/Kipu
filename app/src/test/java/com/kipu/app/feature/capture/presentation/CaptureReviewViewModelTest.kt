package com.kipu.app.feature.capture.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.CaptureConfirmationMode
import com.kipu.app.feature.capture.domain.CaptureConfirmationResult
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.feature.capture.domain.ConfirmCaptureCandidate
import com.kipu.app.feature.capture.domain.ConfirmCaptureCommand
import com.kipu.app.feature.capture.domain.ConvertCandidateToManual
import com.kipu.app.feature.capture.domain.CreditMode
import com.kipu.app.feature.capture.domain.DiscardCaptureCandidate
import com.kipu.app.feature.capture.domain.FakeCaptureAccessGate
import com.kipu.app.feature.capture.domain.FakeCaptureCandidateStore
import com.kipu.app.feature.capture.domain.captureCandidate
import com.kipu.app.feature.capture.domain.correctedFields
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = UUID.randomUUID().toString()
    private val debitAccountId = UUID.randomUUID().toString()
    private val otherAccountId = UUID.randomUUID().toString()
    private val creditCardId = UUID.randomUUID().toString()

    private val gate = FakeCaptureAccessGate(owner)
    private val clock = Clock.fixed(Instant.parse("2026-10-28T17:00:00Z"), ZoneOffset.UTC)
    private val confirm = mockk<ConfirmCaptureCandidate>()
    private val instruments = mockk<ObserveInstruments>()
    private val categories = mockk<ObserveCategories>()

    private fun account(id: String, alias: String) = Account(
        id = AccountId(id), userId = UserId(owner), alias = alias, type = AccountType.BANK, currency = Currency.PEN,
        initialBalance = Money(0L, Currency.PEN), openedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private val creditCard = CreditCard(
        id = CardId(creditCardId), userId = UserId(owner), alias = "Visa", issuer = "BCP", network = CardNetwork.VISA,
        lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 500_000L, billingDay = 5, dueDay = 20,
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { instruments.observeAccounts(any()) } returns flowOf(listOf(account(debitAccountId, "Cuenta BCP"), account(otherAccountId, "Ahorros")))
        every { instruments.observeCards(any()) } returns flowOf(listOf(creditCard))
        every { categories.invoke(UserId(owner)) } returns flowOf(emptyList())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun candidateRecord(block: (com.kipu.app.feature.capture.domain.CaptureCandidateRecord) -> com.kipu.app.feature.capture.domain.CaptureCandidateRecord = { it }) =
        block(captureCandidate(ownerId = owner, id = UUID.randomUUID().toString()).copy(accountId = debitAccountId, cardId = null, categoryId = "cat-1", merchantId = null))

    private fun viewModel(store: FakeCaptureCandidateStore, candidateId: String): CaptureReviewViewModel {
        val discard = com.kipu.app.feature.capture.domain.testDiscardCaptureCandidate(store, clock)
        val convert = ConvertCandidateToManual(store, gate)
        return CaptureReviewViewModel(
            SavedStateHandle(mapOf(CAPTURE_CANDIDATE_ID_ARG to candidateId)),
            store, gate, instruments, categories, confirm, discard, convert,
        )
    }

    @Test fun `loads the pending candidate and preselects its unique instrument`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        val state = vm.state.value
        assertNotNull(state.candidate)
        assertEquals("120.00", state.amountText)
        assertEquals(debitAccountId, state.selectedInstrument?.id)
        assertTrue(state.canConfirm)
        assertFalse(state.showCreditMode)
    }

    @Test fun `explicit linked wallet selection keeps selection identity and sends its parent account`() = runTest(dispatcher) {
        val walletId = UUID.randomUUID().toString()
        val wallet = account(walletId, "Yape").copy(type = AccountType.DIGITAL_WALLET,
            preset = com.kipu.app.feature.accounts.domain.model.AccountPreset.YAPE, linkedAccountId = AccountId(debitAccountId))
        every { instruments.observeAccounts(any()) } returns flowOf(listOf(account(debitAccountId, "Banco"), wallet))
        val candidate = candidateRecord()
        val command = slot<ConfirmCaptureCommand>()
        coEvery { confirm(capture(command), any()) } returns CaptureConfirmationResult.Registered("tx", false)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.onInstrumentSelected(vm.state.value.instruments.single { it.id == walletId })
        vm.onCategorySelected("cat-1")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(walletId, command.captured.selectedAccountId)
        assertEquals(debitAccountId, command.captured.accountId)
        assertEquals(CaptureInstrumentKind.WALLET, command.captured.instrumentKind)
    }

    @Test fun `ambiguous instrument is never preselected and blocks confirmation`() = runTest(dispatcher) {
        val candidate = candidateRecord { it.copy(instrumentAmbiguous = true, accountId = null, cardId = null, instrumentKind = null) }
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        assertNull(vm.state.value.selectedInstrument)
        assertTrue(vm.state.value.instrumentChoiceRequired)
        vm.confirm()
        advanceUntilIdle()
        assertEquals("Elige la cuenta o tarjeta", vm.state.value.instrumentError)
        coVerify(exactly = 0) { confirm(any(), any()) }
    }

    @Test fun `credit mode appears only for credit cards and switching to debit drops installments`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        val credit = vm.state.value.instruments.first { it.kind == CaptureInstrumentKind.CREDIT_CARD }
        vm.onInstrumentSelected(credit)
        vm.onCreditModeSelected(CreditMode.INSTALLMENTS)
        vm.onInstallmentsChanged("6")
        assertTrue(vm.state.value.showCreditMode)
        assertEquals("6", vm.state.value.installmentsText)

        vm.onInstrumentSelected(vm.state.value.instruments.first { it.kind == CaptureInstrumentKind.DEBIT_ACCOUNT })
        assertFalse(vm.state.value.showCreditMode)
        assertEquals(CreditMode.DIRECT, vm.state.value.creditMode)
        assertEquals("", vm.state.value.installmentsText)
    }

    @Test fun `installments need a valid count before calling the ledger`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.onInstrumentSelected(vm.state.value.instruments.first { it.kind == CaptureInstrumentKind.CREDIT_CARD })
        vm.onCreditModeSelected(CreditMode.INSTALLMENTS)
        vm.onInstallmentsChanged("1")
        vm.confirm()
        advanceUntilIdle()
        assertEquals("Elige entre 2 y 36 cuotas", vm.state.value.installmentsError)
        coVerify(exactly = 0) { confirm(any(), any()) }
    }

    @Test fun `confirm sends the edited values with the credit card and installments`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val command = slot<ConfirmCaptureCommand>()
        coEvery { confirm(capture(command), any()) } returns CaptureConfirmationResult.Registered("tx-1", alreadyResolved = false)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.onAmountChanged("150.50")
        vm.onInstrumentSelected(vm.state.value.instruments.first { it.kind == CaptureInstrumentKind.CREDIT_CARD })
        vm.onCreditModeSelected(CreditMode.INSTALLMENTS)
        vm.onInstallmentsChanged("3")
        vm.onCategorySelected("cat-1")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(15_050L, command.captured.amountMinor)
        assertEquals(CaptureInstrumentKind.CREDIT_CARD, command.captured.instrumentKind)
        assertEquals(creditCardId, command.captured.cardId)
        assertEquals(CreditMode.INSTALLMENTS, command.captured.creditMode)
        assertEquals(3, command.captured.installmentCount)
        assertFalse(command.captured.acceptSimilar)
        coVerify { confirm(any(), CaptureConfirmationMode.INBOX) }
    }

    @Test fun `similar movement warning waits for the user and re-sends accepting it`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val existing = com.kipu.app.feature.movements.domain.model.Transaction(
            id = "tx-0", userId = owner, type = com.kipu.app.feature.movements.domain.model.MovementType.EXPENSE,
            amountMinor = 12_000L, currency = "PEN", occurredAt = 1L, sourceAccountId = debitAccountId, categoryId = "cat-1",
        )
        val sent = mutableListOf<ConfirmCaptureCommand>()
        coEvery { confirm(capture(sent), any()) } answers {
            if (sent.last().acceptSimilar) CaptureConfirmationResult.Registered("tx-1", false)
            else CaptureConfirmationResult.SimilarTransactionWarning(existing)
        }
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(existing, vm.state.value.similarTransaction)
        assertEquals(1, sent.size)

        vm.confirmDespiteSimilar()
        advanceUntilIdle()
        assertNull(vm.state.value.similarTransaction)
        assertEquals(listOf(false, true), sent.map { it.acceptSimilar })
    }

    @Test fun `selecting a canonical merchant sends its id and records merchant correction`() = runTest(dispatcher) {
        val candidate = candidateRecord { it.copy(merchantId = "merchant-old") }
        val command = slot<ConfirmCaptureCommand>()
        coEvery { confirm(capture(command), any()) } returns CaptureConfirmationResult.Registered("tx-1", false)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.onMerchantChanged("merchant-new")
        vm.onCategorySelected("cat-2")
        vm.confirm()
        advanceUntilIdle()
        assertEquals("merchant-new", command.captured.merchantId)
        assertEquals(candidate.merchantSourceText, command.captured.merchantText)
        assertEquals(listOf("MERCHANT", "CATEGORY"), correctedFields(candidate, command.captured))
    }

    @Test fun `unchanged merchant and category produce no corrected fields`() = runTest(dispatcher) {
        val candidate = candidateRecord { it.copy(merchantId = "merchant-old") }
        val command = slot<ConfirmCaptureCommand>()
        coEvery { confirm(capture(command), any()) } returns CaptureConfirmationResult.Registered("tx-1", false)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals("merchant-old", command.captured.merchantId)
        assertEquals(emptyList<String>(), correctedFields(candidate, command.captured))
    }

    @Test fun `clearing the merchant sends no id and retains detected text`() = runTest(dispatcher) {
        val candidate = candidateRecord { it.copy(merchantId = "merchant-old") }
        val command = slot<ConfirmCaptureCommand>()
        coEvery { confirm(capture(command), any()) } returns CaptureConfirmationResult.Registered("tx-1", false)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.onMerchantChanged("")
        vm.confirm()
        advanceUntilIdle()
        assertNull(command.captured.merchantId)
        assertEquals(candidate.merchantSourceText, command.captured.merchantText)
        assertEquals(listOf("MERCHANT"), correctedFields(candidate, command.captured))
    }

    @Test fun `leaving without confirming changes nothing`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val store = FakeCaptureCandidateStore(candidate)
        val vm = viewModel(store, candidate.id)
        advanceUntilIdle()
        vm.onAmountChanged("999")
        vm.onMerchantChanged("Otro")
        assertEquals(candidate, store.row(owner, candidate.id))
        coVerify(exactly = 0) { confirm(any(), any()) }
    }

    @Test fun `premium expired offers manual conversion and then confirms as manual draft`() = runTest(dispatcher) {
        gate.decision = CaptureAccessDecision.PremiumRequired
        val candidate = candidateRecord()
        val store = FakeCaptureCandidateStore(candidate)
        val mode = slot<CaptureConfirmationMode>()
        coEvery { confirm(any(), capture(mode)) } returns CaptureConfirmationResult.Registered("tx-1", false)
        val vm = viewModel(store, candidate.id)
        advanceUntilIdle()
        assertFalse(vm.state.value.canConfirm)
        assertTrue(vm.state.value.canConvertToManual)
        vm.confirm()
        advanceUntilIdle()
        coVerify(exactly = 0) { confirm(any(), any()) }

        vm.convertToManual()
        advanceUntilIdle()
        assertTrue(vm.state.value.manualMode)
        assertTrue(vm.state.value.canConfirm)
        assertEquals(CaptureCandidateStatus.PENDING, store.row(owner, candidate.id)!!.status)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(CaptureConfirmationMode.MANUAL_DRAFT, mode.captured)
    }

    @Test fun `registered result emits an event and access denial reopens the manual path`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        coEvery { confirm(any(), any()) } returns CaptureConfirmationResult.AccessDenied(CaptureAccessDecision.PremiumRequired)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.state.value.canConvertToManual)
        assertNotNull(vm.state.value.generalError)
    }

    @Test fun `discard removes the candidate without the ledger`() = runTest(dispatcher) {
        val candidate = candidateRecord()
        val store = FakeCaptureCandidateStore(candidate)
        val vm = viewModel(store, candidate.id)
        advanceUntilIdle()
        vm.discard()
        advanceUntilIdle()
        assertEquals(CaptureCandidateStatus.DISCARDED, store.row(owner, candidate.id)!!.status)
        assertNull(store.row(owner, candidate.id)!!.evidenceSnippet)
        coVerify(exactly = 0) { confirm(any(), any()) }
    }

    @Test fun `foreign or resolved candidates are not editable`() = runTest(dispatcher) {
        val foreign = captureCandidate(ownerId = UUID.randomUUID().toString(), id = UUID.randomUUID().toString())
        val vm = viewModel(FakeCaptureCandidateStore(foreign), foreign.id)
        advanceUntilIdle()
        assertTrue(vm.state.value.notFound)
        assertNull(vm.state.value.candidate)
    }

    @Test fun `inferred date is flagged until the user edits it`() = runTest(dispatcher) {
        val candidate = candidateRecord { it.copy(occurredAtInferred = true, confidenceReasons = listOf(CaptureReason.DATE_INFERRED)) }
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        assertTrue(vm.state.value.candidate!!.occurredAtInferred && !vm.state.value.dateEdited)
        assertEquals(listOf(CaptureReason.DATE_INFERRED), vm.state.value.reasons)
        vm.onDateSelected(candidate.occurredAt!! + 86_400_000L)
        assertTrue(vm.state.value.dateEdited)
    }

    @Test fun `amount parsing is exact and rejects more than two decimals`() {
        assertEquals(2_550L, parseCaptureAmountMinor("25.5"))
        assertEquals(2_550L, parseCaptureAmountMinor("25,50"))
        assertEquals(100_000L, parseCaptureAmountMinor("1000"))
        assertNull(parseCaptureAmountMinor("25.555"))
        assertNull(parseCaptureAmountMinor("0"))
        assertNull(parseCaptureAmountMinor(""))
        assertNull(parseCaptureAmountMinor("1.2.3"))
        assertEquals("S/ 25.50", formatCaptureAmount(2_550L, "PEN"))
        assertEquals("US$ 15.99", formatCaptureAmount(1_599L, "USD"))
        assertEquals("S/ 2,450.00", formatCaptureAmount(245_000L, "PEN"))
    }

    @Test fun `wallet instruments carry the account id and no card id`() = runTest(dispatcher) {
        val walletId = UUID.randomUUID().toString()
        val wallet = account(walletId, "Yape").copy(type = AccountType.DIGITAL_WALLET)
        every { instruments.observeAccounts(any()) } returns flowOf(listOf(wallet))
        val candidate = candidateRecord { it.copy(instrumentKind = CaptureInstrumentKind.WALLET, accountId = walletId, direction = CaptureDirection.EXPENSE) }
        val command = slot<ConfirmCaptureCommand>()
        coEvery { confirm(capture(command), any()) } returns CaptureConfirmationResult.Registered("tx-1", false)
        val vm = viewModel(FakeCaptureCandidateStore(candidate), candidate.id)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(walletId, command.captured.accountId)
        assertNull(command.captured.cardId)
        assertNull(command.captured.creditMode)
    }

    @Test fun `resolved ambiguous capture reopens choice from database and None persists`() = runTest(dispatcher) {
        val candidate = candidateRecord().copy(status = CaptureCandidateStatus.AUTO_REGISTERED,
            resolvedTransactionId = "tx", expectedMatchState = com.kipu.app.feature.capture.domain.ExpectedMatchState.PENDING_CHOICE)
        val store = FakeCaptureCandidateStore(candidate)
        val expected = mockk<com.kipu.app.feature.capture.domain.ExpectedPaymentStore>()
        every { expected.observeByOwner(owner) } returns flowOf(emptyList())
        coEvery { expected.findExpectedByAmount(any(), any(), any(), any(), any()) } returns emptyList()
        val matcher = com.kipu.app.feature.recurrences.domain.MatchCapturedMovementToExpected(expected, gate, clock, store)
        val vm = CaptureReviewViewModel(SavedStateHandle(mapOf(CAPTURE_CANDIDATE_ID_ARG to candidate.id)),
            store, gate, instruments, categories, confirm, com.kipu.app.feature.capture.domain.testDiscardCaptureCandidate(store, clock), ConvertCandidateToManual(store, gate), matcher)
        advanceUntilIdle()
        assertFalse(vm.state.value.notFound)
        assertEquals(candidate.id, vm.state.value.resolvedForChoice?.id)
        assertFalse(vm.state.value.canConfirm)
        vm.chooseExpected(null)
        advanceUntilIdle()
        assertEquals(com.kipu.app.feature.capture.domain.ExpectedMatchState.NONE, store.row(owner, candidate.id)?.expectedMatchState)
        coVerify(exactly = 0) { confirm(any(), any()) }
    }
}
