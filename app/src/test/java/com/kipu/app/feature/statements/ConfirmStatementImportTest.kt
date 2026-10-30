package com.kipu.app.feature.statements

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.accounts.domain.usecase.PayCreditCard
import com.kipu.app.feature.capture.domain.CaptureRegistrationReceipts
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.domain.usecase.ResolvePreferredCategory
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.statements.domain.*
import io.mockk.*
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConfirmStatementImportTest {
    private val cardFactory = registerInstanceFactory { CardId("61000000-0000-4000-8000-000000000010") }
    private val accountFactory = registerInstanceFactory { AccountId("61000000-0000-4000-8000-000000000020") }
    private val operationFactory = registerInstanceFactory { OperationId("61000000-0000-4000-8000-000000000030") }
    private val ownerFactory = registerInstanceFactory { UserId("61000000-0000-4000-8000-000000000001") }

    @org.junit.After fun releaseFactories() {
        cardFactory.deregister()
        accountFactory.deregister()
        operationFactory.deregister()
        ownerFactory.deregister()
    }
    private val owner = "61000000-0000-4000-8000-000000000001"
    private var snapshot = StatementImportSnapshot(storedStatement().copy(ownerId = owner, cardId = "61000000-0000-4000-8000-000000000010"), emptyList())
    private var allowed = true
    private val store = mockk<StatementImportStore> {
        every { observe(owner, "import") } answers { flowOf(snapshot) }
        coEvery { editLine(owner, any(), any()) } answers {
            val id = secondArg<String>()
            val edit = thirdArg<StatementImportLineEdit>()
            snapshot = snapshot.copy(lines = snapshot.lines.map {
                if (it.id == id) it.copy(categoryId = edit.categoryId, merchantId = edit.merchantId) else it
            })
            true
        }
        coEvery { markLineRegistered(owner, any(), any()) } answers {
            val id = secondArg<String>()
            val movement = thirdArg<String>()
            snapshot = snapshot.copy(lines = snapshot.lines.map {
                if (it.id == id) it.copy(status = StatementImportLineStatus.REGISTERED, selected = false, registeredMovementId = movement) else it
            })
            true
        }
        coEvery { complete(owner, "import") } answers {
            snapshot = snapshot.copy(statement = snapshot.statement.copy(status = StatementImportStatus.COMPLETED),
                lines = snapshot.lines.map { if (it.status == StatementImportLineStatus.PENDING && !it.selected) it.copy(status = StatementImportLineStatus.EXCLUDED) else it })
            true
        }
    }
    private val access = StatementImportAccess { allowed }
    private val matching = mockk<StatementImportMatching> {
        coEvery { activeCreditCards(owner) } returns listOf(StatementImportCard("61000000-0000-4000-8000-000000000010", "BCP", "1234", Currency.PEN))
    }
    private val applied = mutableMapOf<String, Transaction>()
    private val receipts = CaptureRegistrationReceipts { _, key -> applied[key] }
    private val purchases = mutableListOf<Transaction>()
    private val credit = mockk<ConfirmCreditPurchase> {
        coEvery { this@mockk.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val money = secondArg<Money>()
            val key = arg<String>(5)
            val transaction = purchase("tx-$key", money.minorUnits).copy(
                occurredAt = arg<java.time.Instant>(3).toEpochMilli(),
                merchantProvisionalText = thirdArg(), installmentCount = arg(4), categoryId = arg(6), merchantId = arg(7),
            )
            applied[key] = transaction
            purchases += transaction
            Result.success(Unit)
        }
    }
    private val pay = mockk<PayCreditCard> {
        coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } returns Result.success(Unit)
    }
    private val movements = mockk<MovementRepository> {
        every { observeTransactions(owner) } answers { flowOf(purchases.map { TransactionItem(it) }) }
    }
    private val refundRepository = mockk<MovementRefundRepository> {
        coEvery { getRefundedAmount(owner, any()) } returns 0L
    }
    private val refundCommands = mutableListOf<RefundTransactionCommand>()
    private val refund = mockk<RegisterRefundTransaction> {
        coEvery { this@mockk.invoke(any()) } answers {
            val command = firstArg<RefundTransactionCommand>()
            refundCommands += command
            val transaction = Transaction(command.refundTransactionId, owner, MovementType.INCOME,
                command.amountMinor, command.currency, occurredAt = command.occurredAt, cardId = "61000000-0000-4000-8000-000000000010", operationKind = "REFUND")
            applied[command.idempotencyKey] = transaction
            RefundTransactionResult.Success(transaction)
        }
    }
    private val category = Category(CategoryId("category"), null, null, CategoryOrigin.SYSTEM, true)
    private val categories = mockk<CategoriesRepository> {
        every { observeCategories(UserId(owner)) } returns flowOf(listOf(category))
        every { observeMerchantCatalog() } returns flowOf(emptyList())
        every { observeMerchantAliasRules(UserId(owner)) } returns flowOf(emptyList())
        every { observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(emptyList())
    }
    private val preference = mockk<SetMerchantCategoryPreference> {
        coEvery { this@mockk.invoke(any(), any(), any()) } returns Result.success(mockk())
    }
    private val review = PrepareStatementImportReview(store, categories, ResolvePreferredCategory(categories), preference)
    private val transactions = object : DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = block()
    }
    private val confirm = ConfirmStatementImport(store, access, matching, credit, pay, refund,
        movements, refundRepository, receipts, review, transactions)

    private fun line(id: String = "line", kind: StatementLineKind = StatementLineKind.PURCHASE) = StatementImportLine(
        id, "import", owner, "fingerprint-$id", 0, kind, statementDate, statementDate,
        "Synthetic merchant", 1000, Currency.PEN, null, true, "category",
    )

    private fun purchase(id: String, amount: Long = 1000) = Transaction(id, owner, MovementType.EXPENSE,
        amount, "PEN", categoryId = "category", merchantProvisionalText = "Synthetic merchant",
        occurredAt = statementDate.minusDays(1).atTime(12, 0).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli(),
        cardId = "61000000-0000-4000-8000-000000000010", operationKind = "CARD_PURCHASE", installmentCount = 1)

    private fun use(vararg lines: StatementImportLine) { snapshot = snapshot.copy(lines = lines.toList()) }

    @Test fun `each direct purchase kind reaches existing credit ledger with one installment and Lima noon`() = runTest {
        val kinds = listOf(StatementLineKind.PURCHASE, StatementLineKind.INTEREST, StatementLineKind.INSURANCE, StatementLineKind.FEE)
        use(*kinds.map { line(it.name, it) }.toTypedArray())
        val result = confirm(owner, "import")
        assertEquals(4, result.registered)
        assertTrue(result.completed)
        assertTrue(result.failed.isEmpty())
        coVerify(exactly = 4) { credit(CardId("61000000-0000-4000-8000-000000000010"), Money(1000, Currency.PEN), "Synthetic merchant",
            java.time.Instant.parse("2026-09-20T17:00:00Z"), 1, any(), "category", null, null) }
        assertTrue(snapshot.lines.all { it.status == StatementImportLineStatus.REGISTERED })
    }

    @Test fun `installment purchase uses total principal and requested count`() = runTest {
        use(line(kind = StatementLineKind.INSTALLMENT_PURCHASE).copy(amountMinor = 60000, installmentCount = 6))
        assertTrue(confirm(owner, "import").completed)
        coVerify { credit(CardId("61000000-0000-4000-8000-000000000010"), Money(60000, Currency.PEN), any(), any(), 6, any(), "category", null, null) }
    }

    @Test fun `invalid installments stay pending`() = runTest {
        use(line(kind = StatementLineKind.INSTALLMENT_PURCHASE).copy(installmentCount = 1))
        assertEquals(StatementLineFailureReason.InvalidInstallments, confirm(owner, "import").failed.single().reason)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `unknown needs reclassification and monthly installment charge is not importable`() = runTest {
        use(line("unknown", StatementLineKind.UNKNOWN), line("monthly", StatementLineKind.INSTALLMENT_MONTHLY_CHARGE))
        val result = confirm(owner, "import")
        assertEquals(listOf(StatementLineFailureReason.ClassificationRequired, StatementLineFailureReason.NotSupported), result.failed.map { it.reason })
        assertFalse(result.completed)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `card payment requires source and selected payment delegates to PayCreditCard`() = runTest {
        use(line("missing", StatementLineKind.CARD_PAYMENT), line("pay", StatementLineKind.CARD_PAYMENT).copy(sourceAccountId = "61000000-0000-4000-8000-000000000020"),
            line("unselected", StatementLineKind.CARD_PAYMENT).copy(selected = false))
        val result = confirm(owner, "import")
        assertEquals(1, result.registered)
        assertEquals(StatementLineFailure("missing", StatementLineFailureReason.SourceAccountRequired), result.failed.single())
        assertFalse(result.completed)
        coVerify(exactly = 1) { pay(CardId("61000000-0000-4000-8000-000000000010"), AccountId("61000000-0000-4000-8000-000000000020"), Money(1000, Currency.PEN),
            java.time.Instant.parse("2026-09-20T17:00:00Z"), any()) }
    }

    @Test fun `payment rejects debt overflow without preventing following purchases`() = runTest {
        coEvery { pay(any(), any(), any(), any(), any()) } returns Result.failure(IllegalArgumentException("Payment amount exceeds current card debt"))
        use(line("pay", StatementLineKind.CARD_PAYMENT).copy(sourceAccountId = "61000000-0000-4000-8000-000000000020"), line("purchase"))
        val result = confirm(owner, "import")
        assertEquals(1, result.registered)
        assertEquals(StatementLineFailureReason.PaymentExceedsDebt, result.failed.single().reason)
    }

    @Test fun `missing card and expired Premium never touch the ledger`() = runTest {
        use(line())
        snapshot = snapshot.copy(statement = snapshot.statement.copy(cardId = null))
        assertEquals(StatementLineFailureReason.CardRequired, confirm(owner, "import").error)
        snapshot = snapshot.copy(statement = snapshot.statement.copy(cardId = "61000000-0000-4000-8000-000000000010"))
        allowed = false
        assertEquals(StatementLineFailureReason.PremiumRequired, confirm(owner, "import").error)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `currency mismatch and archived card do not register`() = runTest {
        use(line().copy(currency = Currency.USD))
        assertEquals(StatementLineFailureReason.CurrencyMismatch, confirm(owner, "import").failed.single().reason)
        coEvery { matching.activeCreditCards(owner) } returns emptyList()
        use(line())
        assertEquals(StatementLineFailureReason.CardUnavailable, confirm(owner, "import").failed.single().reason)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `USD purchase works against a USD credit card`() = runTest {
        coEvery { matching.activeCreditCards(owner) } returns listOf(StatementImportCard("61000000-0000-4000-8000-000000000010", "BCP", "1234", Currency.USD))
        use(line().copy(currency = Currency.USD))
        assertEquals(1, confirm(owner, "import").registered)
        coVerify { credit(any(), Money(1000, Currency.USD), any(), any(), 1, any(), any(), any(), any()) }
    }

    @Test fun `partial failure retries do not repeat successful lines and complete excludes unselected`() = runTest {
        use(line("ok"), line("fix").copy(currency = Currency.USD), line("excluded").copy(selected = false))
        val first = confirm(owner, "import")
        assertEquals(1, first.registered)
        assertFalse(first.completed)
        snapshot = snapshot.copy(lines = snapshot.lines.map { if (it.id == "fix") it.copy(currency = Currency.PEN) else it })
        val second = confirm(owner, "import")
        assertEquals(1, second.registered)
        assertTrue(second.completed)
        assertEquals(2, purchases.size)
        assertEquals(StatementImportLineStatus.EXCLUDED, snapshot.lines.last().status)
    }

    @Test fun `split commit recovers receipt before changed draft validation and does not duplicate`() = runTest {
        use(line())
        coEvery { store.markLineRegistered(owner, "line", any()) } returns false
        val first = confirm(owner, "import")
        assertEquals(StatementLineFailureReason.RegistrationFailed, first.failed.single().reason)
        assertEquals(1, purchases.size)
        coEvery { store.markLineRegistered(owner, "line", any()) } answers {
            snapshot = snapshot.copy(lines = snapshot.lines.map { it.copy(status = StatementImportLineStatus.REGISTERED, selected = false, registeredMovementId = thirdArg()) })
            true
        }
        snapshot = snapshot.copy(lines = snapshot.lines.map { it.copy(categoryId = null, amountMinor = 2000) })
        assertTrue(confirm(owner, "import").completed)
        assertEquals(1, purchases.size)
    }

    @Test fun `selected possible duplicate is an explicit confirmation using the same operation key`() = runTest {
        use(line().copy(possibleDuplicate = true))
        assertEquals(1, confirm(owner, "import").registered)
        coVerify(exactly = 0) { matching.hasDuplicate(any(), any(), any(), any(), any()) }
    }

    @Test fun `refund matches same batch purchase even when refund was listed first`() = runTest {
        use(line("refund", StatementLineKind.REFUND), line("purchase"))
        val result = confirm(owner, "import")
        assertEquals(2, result.registered)
        assertTrue(result.completed)
        assertEquals(purchases.single().id, refundCommands.single().purchaseTransactionId)
    }

    @Test fun `refund chooses unique normalized merchant match over unrelated purchases`() = runTest {
        purchases += purchase("match").copy(merchantProvisionalText = "  SYNTHETIC  MERCHANT  ")
        purchases += purchase("other").copy(merchantProvisionalText = "Other synthetic merchant")
        use(line(kind = StatementLineKind.REFUND))
        assertTrue(confirm(owner, "import").completed)
        assertEquals("match", refundCommands.single().purchaseTransactionId)
    }

    @Test fun `ambiguous refunds and points never create unrelated credits`() = runTest {
        purchases += listOf(purchase("one"), purchase("two"))
        use(line("refund", StatementLineKind.REFUND), line("points", StatementLineKind.POINTS_REDEMPTION))
        val result = confirm(owner, "import")
        assertEquals(listOf(StatementLineFailureReason.RefundPurchaseRequired, StatementLineFailureReason.NotSupported), result.failed.map { it.reason })
        assertTrue(refundCommands.isEmpty())
        assertEquals(0, result.registered)
    }

    @Test fun `refund candidate must be eligible owner card currency date and remainder`() = runTest {
        val valid = purchase("valid")
        purchases += listOf(valid.copy(id = "foreign", userId = "other"), valid.copy(id = "61000000-0000-4000-8000-000000000010", cardId = "other"),
            valid.copy(id = "currency", currency = "USD"), valid.copy(id = "void", status = TransactionStatus.VOIDED),
            valid.copy(id = "old", occurredAt = valid.occurredAt - 121L * 86400000),
            valid.copy(id = "future", occurredAt = valid.occurredAt + 3L * 86400000), valid.copy(id = "small", amountMinor = 500),
            valid.copy(id = "refunded"), valid.copy(id = "installments", installmentCount = 3))
        coEvery { refundRepository.getRefundedAmount(owner, "refunded") } returns 1
        use(line(kind = StatementLineKind.REFUND))
        assertEquals(StatementLineFailureReason.RefundPurchaseRequired, confirm(owner, "import").failed.single().reason)
        assertTrue(refundCommands.isEmpty())
    }

    @Test fun `category missing remains pending and is exposed to review`() = runTest {
        use(line().copy(categoryId = null))
        assertEquals(listOf("line"), review.missingCategoryLineIds(owner, "import"))
        assertEquals(StatementLineFailureReason.CategoryRequired, confirm(owner, "import").failed.single().reason)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `learning happens only after successful registration`() = runTest {
        every { categories.observeMerchantCatalog() } returns flowOf(listOf(MerchantCatalogEntry(MerchantId("merchant"), "Synthetic merchant", "synthetic merchant")))
        use(line("ok").copy(merchantId = "merchant"), line("bad").copy(merchantId = "merchant", currency = Currency.USD))
        val result = confirm(owner, "import")
        assertEquals(1, result.registered)
        coVerify(exactly = 1) { preference(UserId(owner), MerchantId("merchant"), CategoryId("category")) }
    }

    @Test fun `learning failure does not hide a durable registration`() = runTest {
        every { categories.observeMerchantCatalog() } returns flowOf(listOf(MerchantCatalogEntry(MerchantId("merchant"), "Synthetic merchant", "synthetic merchant")))
        coEvery { preference(any(), any(), any()) } throws IllegalStateException("unavailable")
        use(line().copy(merchantId = "merchant"))
        assertTrue(confirm(owner, "import").completed)
        assertEquals(1, purchases.size)
    }

    @Test fun `cancellation propagates without completing batch`() = runTest {
        coEvery { credit(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws CancellationException()
        use(line())
        try { confirm(owner, "import"); fail("Cancellation expected") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { store.complete(any(), any()) }
    }

    @Test fun `completed import is idempotent and discarded import is rejected`() = runTest {
        snapshot = snapshot.copy(statement = snapshot.statement.copy(status = StatementImportStatus.COMPLETED))
        assertTrue(confirm(owner, "import").completed)
        snapshot = snapshot.copy(statement = snapshot.statement.copy(status = StatementImportStatus.DISCARDED))
        assertEquals(StatementLineFailureReason.ImportNotPending, confirm(owner, "import").error)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `review prefills learned category through existing alias rules without changing user edits`() = runTest {
        val merchant = MerchantId("merchant")
        every { categories.observeMerchantCatalog() } returns flowOf(listOf(MerchantCatalogEntry(merchant, "Canonical merchant", "canonical merchant")))
        every { categories.observeMerchantAliasRules(UserId(owner)) } returns flowOf(listOf(
            MerchantAliasRule(MerchantAliasRuleId("alias"), UserId(owner), "synthetic merchant", merchant)))
        every { categories.observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(listOf(
            MerchantCategoryPreference(UserId(owner), merchant, CategoryId("category"))))
        use(line("suggest").copy(categoryId = null), line("edited").copy(categoryId = "user-category"))
        val prepared = review(owner, "import")!!
        assertEquals("category", prepared.lines.first().categoryId)
        assertEquals("merchant", prepared.lines.first().merchantId)
        assertEquals("user-category", prepared.lines.last().categoryId)
        assertTrue(review.missingCategoryLineIds(owner, "import").isEmpty())
        use(prepared.lines.first())
        assertTrue(confirm(owner, "import").completed)
        coVerify(exactly = 0) { preference(any(), any(), any()) }
    }

    @Test fun `review does not turn a general merchant default into user learning`() = runTest {
        every { categories.observeMerchantCatalog() } returns flowOf(listOf(
            MerchantCatalogEntry(MerchantId("merchant"), "Synthetic merchant", "synthetic merchant", defaultCategoryId = CategoryId("category"))))
        use(line().copy(categoryId = null))
        assertNull(review(owner, "import")!!.lines.single().categoryId)
        assertEquals(StatementLineFailureReason.CategoryRequired, confirm(owner, "import").failed.single().reason)
        coVerify(exactly = 0) { preference(any(), any(), any()) }
    }

    @Test fun `system bank and insurance categories are reused and never learned as corrections`() = runTest {
        val bank = "00000000-0000-0000-0000-000000000901"
        val insurance = "00000000-0000-0000-0000-000000000903"
        every { categories.observeCategories(UserId(owner)) } returns flowOf(listOf(category.copy(id = CategoryId(bank)), category.copy(id = CategoryId(insurance))))
        use(line("interest", StatementLineKind.INTEREST).copy(categoryId = null),
            line("insurance", StatementLineKind.INSURANCE).copy(categoryId = null),
            line("fee", StatementLineKind.FEE).copy(categoryId = null))
        val prepared = review(owner, "import")!!
        assertEquals(listOf(bank, insurance, bank), prepared.lines.map { it.categoryId })
        assertTrue(confirm(owner, "import").completed)
        assertEquals(listOf(bank, insurance, bank), purchases.map { it.categoryId })
        coVerify(exactly = 0) { preference(any(), any(), any()) }
    }

    @Test fun `inactive system category is not silently assigned`() = runTest {
        every { categories.observeCategories(UserId(owner)) } returns flowOf(listOf(
            category.copy(id = CategoryId("00000000-0000-0000-0000-000000000903"), isActive = false)))
        use(line(kind = StatementLineKind.INSURANCE).copy(categoryId = null))
        assertNull(review(owner, "import")!!.lines.single().categoryId)
        assertEquals(listOf("line"), review.missingCategoryLineIds(owner, "import"))
        assertEquals(StatementLineFailureReason.CategoryRequired, confirm(owner, "import").failed.single().reason)
    }

    @Test fun `gate is revalidated after an earlier successful line`() = runTest {
        coEvery { credit(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            allowed = false
            Result.success(Unit)
        }
        use(line("one"), line("two"))
        val result = confirm(owner, "import")
        assertEquals(1, result.registered)
        assertEquals(StatementLineFailure("two", StatementLineFailureReason.PremiumRequired), result.failed.single())
        assertFalse(result.completed)
        coVerify(exactly = 1) { credit(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `payment result cancellation propagates rather than becoming a partial failure`() = runTest {
        coEvery { pay(any(), any(), any(), any(), any()) } returns Result.failure(CancellationException())
        use(line(kind = StatementLineKind.CARD_PAYMENT).copy(sourceAccountId = "61000000-0000-4000-8000-000000000020"))
        try { confirm(owner, "import"); fail("Cancellation expected") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { store.markLineRegistered(any(), any(), any()) }
    }
}
