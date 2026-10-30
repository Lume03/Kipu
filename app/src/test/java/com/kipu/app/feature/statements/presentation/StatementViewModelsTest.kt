package com.kipu.app.feature.statements.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.feature.statements.storedStatement
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

private const val TEST_OWNER = "11111111-1111-1111-1111-111111111111"
private const val TEST_CARD = "22222222-2222-2222-2222-222222222222"

@OptIn(ExperimentalCoroutinesApi::class)
class StatementViewModelsTest {
    private val session = mockk<SessionCoordinator>()
    private val access = MutableStateFlow<LocalAccess>(LocalAccess.Available(TEST_OWNER, RemoteSession.Absent))
    private val store = mockk<StatementImportStore>(relaxed = true)
    private val importer = mockk<ImportCardStatement>()
    private val premium = mockk<StatementImportAccess>()
    private val prepare = mockk<PrepareStatementImportReview>()
    private val confirm = mockk<ConfirmStatementImport>()
    private val instruments = mockk<ObserveInstruments>()
    private val categories = mockk<ObserveCategories>()
    private val uri = mockk<Uri>()
    private val card = CreditCard(CardId(TEST_CARD), UserId(TEST_OWNER), issuer = "BCP", network = CardNetwork.VISA,
        lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 100000, billingDay = 20, dueDay = 5)
    private val line = StatementImportLine("line", "import", TEST_OWNER, "fingerprint", 0, StatementLineKind.PURCHASE,
        LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), "Compra de prueba", 1000, Currency.PEN, null, true)
    private val snapshot = MutableStateFlow<StatementImportSnapshot?>(null)

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { session.localAccess } returns access
        every { store.observePending(TEST_OWNER) } returns flowOf(emptyList())
        every { store.observe(TEST_OWNER, "import") } returns snapshot
        every { instruments.observeAccounts(true) } returns flowOf(emptyList())
        every { instruments.observeCards(true) } returns flowOf(listOf(card))
        every { categories(UserId(TEST_OWNER)) } returns flowOf(emptyList())
        coEvery { premium.canImport(TEST_OWNER) } returns true
        coEvery { prepare(TEST_OWNER, "import") } answers { snapshot.value }
        coEvery { prepare.missingCategoryLineIds(TEST_OWNER, "import") } returns emptyList()
        snapshot.value = StatementImportSnapshot(storedStatement().copy(ownerId = TEST_OWNER, cardId = TEST_CARD), listOf(line))
    }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun flowVm() = StatementImportViewModel(importer, store, premium, session)
    private fun reviewVm() = StatementReviewViewModel(SavedStateHandle(mapOf("importId" to "import")),
        store, prepare, confirm, instruments, categories, session)

    @Test fun `consent is required before document picker`() = runTest {
        val vm = flowVm(); advanceUntilIdle()
        vm.requestImport(); advanceUntilIdle()
        assertTrue(vm.state.value.showConsent)
        assertFalse(vm.state.value.pickDocument)
        vm.acceptConsent()
        assertTrue(vm.state.value.pickDocument)
        vm.documentPicked(null)
        assertFalse(vm.state.value.pickDocument)
    }

    @Test fun `Free user sees plans without consent or picker`() = runTest {
        coEvery { premium.canImport(TEST_OWNER) } returns false
        val vm = flowVm(); advanceUntilIdle()
        vm.requestImport(); advanceUntilIdle()
        assertTrue(vm.state.value.showPremium)
        assertFalse(vm.state.value.showConsent)
        assertFalse(vm.state.value.pickDocument)
    }

    @Test fun `password retries clear the field and caller buffer`() = runTest {
        val secrets = mutableListOf<CharArray?>()
        coEvery { importer(TEST_OWNER, uri, true, any()) } answers {
            secrets += arg<CharArray?>(3)
            if (secrets.size == 1) StatementImportResult.PasswordRequired else StatementImportResult.WrongPassword
        }
        val vm = flowVm(); advanceUntilIdle()
        vm.requestImport(); advanceUntilIdle(); vm.acceptConsent(); vm.documentPicked(uri); advanceUntilIdle()
        assertTrue(vm.state.value.showPassword)
        vm.passwordChanged("clave"); vm.submitPassword()
        assertEquals("", vm.state.value.password)
        advanceUntilIdle()
        assertTrue(vm.state.value.wrongPassword)
        assertTrue(secrets.last()!!.all { it == '\u0000' })
        vm.passwordChanged("otra"); vm.cancelPassword()
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.showPassword)
        vm.submitPassword(); advanceUntilIdle()
        coVerify(exactly = 2) { importer(any(), any(), any(), any()) }
    }

    @Test fun `wrong passwords count attempts and lock the file after three`() = runTest {
        val results = ArrayDeque(listOf(StatementImportResult.PasswordRequired, StatementImportResult.WrongPassword,
            StatementImportResult.WrongPassword, StatementImportResult.WrongPassword))
        coEvery { importer(TEST_OWNER, uri, true, any()) } answers { results.removeFirst() }
        val vm = flowVm(); advanceUntilIdle()
        vm.requestImport(); advanceUntilIdle(); vm.acceptConsent(); vm.documentPicked(uri, "estado.pdf", 2048); advanceUntilIdle()
        assertEquals("estado.pdf", vm.state.value.fileName)
        assertEquals(3, vm.state.value.attemptsLeft)
        vm.passwordChanged("a"); vm.submitPassword(); advanceUntilIdle()
        assertEquals(2, vm.state.value.attemptsLeft); assertTrue(vm.state.value.wrongPassword)
        vm.passwordChanged("b"); vm.submitPassword(); advanceUntilIdle()
        assertEquals(1, vm.state.value.attemptsLeft); assertTrue(vm.state.value.showPassword)
        vm.passwordChanged("c"); vm.submitPassword(); advanceUntilIdle()
        assertEquals(0, vm.state.value.attemptsLeft)
        assertTrue(vm.state.value.lockedOut); assertFalse(vm.state.value.showPassword)
        vm.passwordChanged("d"); vm.submitPassword(); advanceUntilIdle()
        coVerify(exactly = 4) { importer(any(), any(), any(), any()) }
    }

    @Test fun `unusable files map to their own notice`() = runTest {
        val expected = mapOf(StatementImportResult.NoExtractableText to StatementImportNotice.UNREADABLE,
            StatementImportResult.Unsupported to StatementImportNotice.UNSUPPORTED_BANK,
            StatementImportResult.Invalid to StatementImportNotice.NOT_STATEMENT,
            StatementImportResult.AlreadyImported("old", true) to StatementImportNotice.ALREADY_IMPORTED)
        for ((result, notice) in expected) {
            coEvery { importer(TEST_OWNER, uri, true, any()) } returns result
            val vm = flowVm(); advanceUntilIdle(); vm.requestImport(); advanceUntilIdle(); vm.acceptConsent()
            vm.documentPicked(uri); advanceUntilIdle()
            assertEquals(notice, vm.state.value.notice)
            vm.dismissNotice(); assertEquals(null, vm.state.value.notice)
        }
    }

    @Test fun `a fresh import exposes the scan summary for the animation`() = runTest {
        coEvery { importer(TEST_OWNER, uri, true, any()) } returns StatementImportResult.Imported("import")
        val vm = flowVm(); advanceUntilIdle(); vm.requestImport(); advanceUntilIdle(); vm.acceptConsent()
        vm.documentPicked(uri); advanceUntilIdle()
        val scan = requireNotNull(vm.state.value.scan)
        assertEquals("import", scan.importId); assertEquals(1, scan.count); assertEquals("S/ 10.00", scan.penTotal)
        assertEquals("import", vm.state.value.openImportId)
        vm.clearScan(); assertEquals(null, vm.state.value.scan)
    }

    @Test fun `import results map to review duplicate notice and readable errors`() = runTest {
        val results = listOf(StatementImportResult.NoExtractableText, StatementImportResult.Unsupported,
            StatementImportResult.Invalid, StatementImportResult.AlreadyImported("old", true),
            StatementImportResult.AlreadyImported("pending", false), StatementImportResult.Imported("new"),
            StatementImportResult.PremiumRequired)
        for (result in results) {
            coEvery { importer(TEST_OWNER, uri, true, any()) } returns result
            val vm = flowVm(); advanceUntilIdle(); vm.requestImport(); advanceUntilIdle(); vm.acceptConsent()
            vm.documentPicked(uri); advanceUntilIdle()
            when (result) {
                is StatementImportResult.Imported -> assertEquals("new", vm.state.value.openImportId)
                is StatementImportResult.AlreadyImported -> if (result.completed)
                    assertEquals("Ya importaste este estado de cuenta", vm.state.value.message)
                    else assertEquals("pending", vm.state.value.openImportId)
                StatementImportResult.PremiumRequired -> assertTrue(vm.state.value.showPremium)
                else -> assertFalse(vm.state.value.message.isNullOrBlank())
            }
            assertFalse(vm.state.value.busy)
        }
    }

    @Test fun `confirmation highlights missing categories and never calls ledger`() = runTest {
        coEvery { prepare.missingCategoryLineIds(TEST_OWNER, "import") } returns listOf("line")
        val vm = reviewVm(); advanceUntilIdle(); vm.confirm(); advanceUntilIdle()
        assertEquals(setOf("line"), vm.state.value.missingCategoryIds)
        coVerify(exactly = 0) { confirm(any(), any()) }
    }

    @Test fun `confirmation requires an active assigned card`() = runTest {
        snapshot.value = snapshot.value!!.copy(statement = storedStatement())
        val vm = reviewVm(); advanceUntilIdle(); vm.confirm(); advanceUntilIdle()
        assertFalse(vm.state.value.canConfirm)
        assertEquals(StatementLineFailureReason.CardRequired.message(), vm.state.value.message)
        coVerify(exactly = 0) { confirm(any(), any()) }
    }

    @Test fun `partial failures remain reviewable and retry can succeed`() = runTest {
        coEvery { confirm(TEST_OWNER, "import") } returnsMany listOf(
            ConfirmStatementResult(0, listOf(StatementLineFailure("line", StatementLineFailureReason.RefundPurchaseRequired)), false),
            ConfirmStatementResult(1, emptyList(), true))
        val vm = reviewVm(); advanceUntilIdle(); vm.confirm(); advanceUntilIdle()
        assertNull(vm.state.value.successCount)
        assertEquals(StatementLineFailureReason.RefundPurchaseRequired, vm.state.value.failures["line"])
        vm.confirm(); advanceUntilIdle()
        assertEquals(1, vm.state.value.successCount)
        assertTrue(vm.state.value.failures.isEmpty())
    }

    @Test fun `global Premium denial is a readable banner`() = runTest {
        coEvery { confirm(TEST_OWNER, "import") } returns ConfirmStatementResult(0, emptyList(), false, StatementLineFailureReason.PremiumRequired)
        val vm = reviewVm(); advanceUntilIdle(); vm.confirm(); advanceUntilIdle()
        assertTrue(vm.state.value.showPremium)
        assertEquals(StatementLineFailureReason.PremiumRequired.message(), vm.state.value.message)
    }

    @Test fun `points and foreign currency cannot be selected`() = runTest {
        val points = line.copy(id = "points", kind = StatementLineKind.POINTS_REDEMPTION, selected = false)
        val dollars = line.copy(id = "usd", currency = Currency.USD, selected = false)
        snapshot.value = snapshot.value!!.copy(lines = listOf(points, dollars))
        val vm = reviewVm(); advanceUntilIdle()
        vm.selectLine(points, true); vm.selectLine(dollars, true); advanceUntilIdle()
        coVerify(exactly = 0) { store.setLineSelected(any(), any(), true) }
    }

    @Test fun `lock clears source password and review data`() = runTest {
        coEvery { importer(TEST_OWNER, uri, true, any()) } returns StatementImportResult.PasswordRequired
        val vm = flowVm(); val review = reviewVm(); advanceUntilIdle()
        vm.requestImport(); advanceUntilIdle(); vm.acceptConsent(); vm.documentPicked(uri); advanceUntilIdle()
        vm.passwordChanged("secreto")
        access.value = LocalAccess.Protected(TEST_OWNER, "lock"); advanceUntilIdle()
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.showPassword)
        assertNull(review.state.value.snapshot)
        vm.submitPassword(); advanceUntilIdle()
        coVerify(exactly = 1) { importer(any(), any(), any(), any()) }
    }

    @Test fun `every failure reason has Spanish copy`() {
        StatementLineFailureReason.entries.forEach { assertTrue(it.message().length > 12) }
    }
}
