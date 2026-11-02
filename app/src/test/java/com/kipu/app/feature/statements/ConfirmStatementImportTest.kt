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
        coEvery { setMerchantSuggestion(owner, any(), any()) } answers {
            val id = secondArg<String>(); val merchant = thirdArg<String?>()
            snapshot = snapshot.copy(lines = snapshot.lines.map { if (it.id == id) it.copy(suggestedMerchantId = merchant) else it }); true
        }
        coEvery { chooseMerchant(owner, any(), any(), any()) } answers {
            val id = secondArg<String>(); val merchant = thirdArg<String?>(); val category = arg<String?>(3)
            snapshot = snapshot.copy(lines = snapshot.lines.map { if (it.id == id) it.copy(merchantId = merchant, categoryId = category,
                suggestedMerchantId = null, merchantReviewed = true) else it }); true
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
        coEvery { this@mockk.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
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
        every { observePremiumVerified(UserId(owner)) } returns flowOf(true)
        coEvery { saveMerchantAliasRule(any(), any()) } returns Result.success(Unit)
        every { observeCategories(UserId(owner)) } returns flowOf(listOf(category))
        every { observeMerchantCatalog() } returns flowOf(emptyList())
        every { observeMerchantAliasRules(UserId(owner)) } returns flowOf(emptyList())
        every { observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(emptyList())
    }
    private val preference = mockk<SetMerchantCategoryPreference> {
        coEvery { this@mockk.invoke(any(), any(), any()) } returns Result.success(mockk())
    }
    private val review = PrepareStatementImportReview(store, categories, ResolvePreferredCategory(categories), preference, com.kipu.app.feature.categories.domain.usecase.SaveMerchantAliasRule(categories))
    private val transactions = object : DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = block()
    }
    private val accountCommands = mutableListOf<RegisterTransactionCommand>()
    private val registerTransaction = mockk<RegisterTransaction> {
        coEvery { this@mockk.invoke(any()) } answers {
            val cmd = firstArg<RegisterTransactionCommand>()
            accountCommands += cmd
            val tx = purchase("account-${cmd.idempotencyKey}").copy(type = cmd.type, sourceAccountId = cmd.sourceAccountId, cardId = null, operationKind = null, installmentCount = null)
            applied[cmd.idempotencyKey] = tx
            RegisterTransactionResult.Success(tx)
        }
    }
    private fun deposit() {
        snapshot = snapshot.copy(statement = snapshot.statement.copy(source = StatementSource.DEPOSIT_ACCOUNT))
        coEvery { matching.activeDebitCards(owner) } returns listOf(StatementImportDebitCard(
            snapshot.statement.cardId!!, "BCP", "Savings", "9999", Currency.PEN, "linked-account"))
    }
    private fun catalog(vararg names: String) {
        every { categories.observeMerchantCatalog() } returns flowOf(names.map {
            MerchantCatalogEntry(MerchantId(it.lowercase()), it, it.lowercase(), defaultCategoryId = category.id)
        })
    }

    @Test fun `review suggests without assigning and acceptance resolves preference before catalog default for credit and debit`() = runTest {
        catalog("Tambo")
        val learned = category.copy(id = CategoryId("learned"))
        every { categories.observeCategories(UserId(owner)) } returns flowOf(listOf(category, learned))
        for (kind in listOf(StatementLineKind.PURCHASE, StatementLineKind.ACCOUNT_DEBIT, StatementLineKind.ACCOUNT_CREDIT)) {
            every { categories.observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(emptyList())
            use(line().copy(kind = kind, description = "YQ-TAMBO BARRERA-R", originalDescription = "YQ-TAMBO BARRERA-R", categoryId = null))
            var prepared = review(owner, "import")!!.lines.single()
            assertEquals("tambo", prepared.suggestedMerchantId)
            assertNull(prepared.merchantId); assertNull(prepared.categoryId)
            assertTrue(review.chooseMerchant(owner, prepared.id, "tambo", "import"))
            prepared = snapshot.lines.single()
            assertEquals("category", prepared.categoryId); assertTrue(prepared.merchantReviewed); assertNull(prepared.suggestedMerchantId)
            every { categories.observeMerchantCategoryPreferences(UserId(owner)) } returns flowOf(listOf(
                MerchantCategoryPreference(UserId(owner), MerchantId("tambo"), learned.id)))
            assertTrue(review.chooseMerchant(owner, prepared.id, "tambo", "import"))
            assertEquals("learned", snapshot.lines.single().categoryId)
        }
    }

    @Test fun `alias wins over suggestion while person and transfer descriptions stay unresolved`() = runTest {
        catalog("Tambo", "Rappi", "Yape", "Nacion")
        every { categories.observeMerchantAliasRules(UserId(owner)) } returns flowOf(listOf(
            MerchantAliasRule(MerchantAliasRuleId("rule"), UserId(owner), "yq-tambo barrera-r", MerchantId("rappi"))))
        use(line("alias").copy(description = "YQ-TAMBO BARRERA-R", originalDescription = "YQ-TAMBO BARRERA-R", categoryId = null),
            line("person").copy(description = "Yape Doris Luq", originalDescription = "Yape Doris Luq", categoryId = null),
            line("transfer").copy(description = "TRANSF.BCO.NACION", originalDescription = "TRANSF.BCO.NACION", categoryId = null))
        val lines = review(owner, "import")!!.lines
        assertEquals("rappi", lines[0].merchantId); assertEquals("category", lines[0].categoryId)
        assertNull(lines[0].suggestedMerchantId)
        lines.drop(1).forEach { assertNull(it.merchantId); assertNull(it.suggestedMerchantId); assertNull(it.categoryId) }
    }

    @Test fun `catalog default must be eligible for movement direction`() = runTest {
        catalog("Tambo")
        every { categories.observeCategories(UserId(owner)) } returns flowOf(listOf(category.copy(categoryType = CategoryType.EXPENSE)))
        use(line().copy(kind = StatementLineKind.ACCOUNT_CREDIT, merchantId = "tambo", categoryId = null))
        assertNull(review(owner, "import")!!.lines.single().categoryId)
    }

    @Test fun `confirmation learns original alias and category only when enabled after success`() = runTest {
        catalog("Tambo")
        use(line("on").copy(description = "Edited note", originalDescription = "YQ-TAMBO BARRERA-R", merchantId = "tambo", merchantReviewed = true),
            line("off").copy(originalDescription = "Tambo 222", merchantId = "tambo", merchantReviewed = true,
                rememberMerchantAlias = false, rememberMerchantCategory = false))
        val result = confirm(owner, "import")
        assertTrue(result.completed); assertEquals(2, result.registered); assertTrue(result.learningWarnings.isEmpty())
        coVerify(exactly = 1) { categories.saveMerchantAliasRule(match { it.normalizedPattern == "yq-tambo barrera-r" && it.merchantId.value == "tambo" }, null) }
        coVerify(exactly = 1) { preference(UserId(owner), MerchantId("tambo"), CategoryId("category")) }
    }

    @Test fun `disabled learning saves neither rule nor preference for debit and credit`() = runTest {
        catalog("Tambo"); deposit()
        use(line("debit", StatementLineKind.ACCOUNT_DEBIT).copy(merchantId = "tambo", merchantReviewed = true, rememberMerchantAlias = false, rememberMerchantCategory = false),
            line("income", StatementLineKind.ACCOUNT_CREDIT).copy(merchantId = "tambo", merchantReviewed = true, rememberMerchantAlias = false, rememberMerchantCategory = false))
        val result = confirm(owner, "import")
        assertEquals(2, result.registered); assertTrue(result.completed)
        coVerify(exactly = 0) { categories.saveMerchantAliasRule(any(), any()) }
        coVerify(exactly = 0) { preference(any(), any(), any()) }
    }

    @Test fun `conflicting aliases and merchant categories are skipped independently with warnings`() = runTest {
        catalog("Tambo", "Rappi")
        use(line("one").copy(originalDescription = "Same text", merchantId = "tambo", merchantReviewed = true),
            line("two").copy(originalDescription = " SAME TEXT ", merchantId = "rappi", merchantReviewed = true),
            line("three").copy(originalDescription = "Other text", merchantId = "tambo", categoryId = "other", merchantReviewed = true))
        val result = confirm(owner, "import")
        assertTrue(result.completed); assertEquals(3, result.registered)
        assertEquals(setOf(StatementLearningWarningKind.ALIAS_CONFLICT, StatementLearningWarningKind.CATEGORY_CONFLICT), result.learningWarnings.map { it.kind }.toSet())
        coVerify(exactly = 0) { categories.saveMerchantAliasRule(match { it.normalizedPattern == "same text" }, any()) }
        coVerify(exactly = 0) { preference(UserId(owner), MerchantId("tambo"), any()) }
        coVerify(exactly = 1) { categories.saveMerchantAliasRule(match { it.normalizedPattern == "other text" }, null) }
        coVerify(exactly = 1) { preference(UserId(owner), MerchantId("rappi"), CategoryId("category")) }
    }

    @Test fun `debit account expenses and income learn their accepted merchant and category`() = runTest {
        catalog("Tambo", "Rappi"); deposit()
        use(line("debit", StatementLineKind.ACCOUNT_DEBIT).copy(originalDescription = "YQ-TAMBO BARRERA-R", merchantId = "tambo", merchantReviewed = true),
            line("income", StatementLineKind.ACCOUNT_CREDIT).copy(originalDescription = "DLC*RAPPI PERU", merchantId = "rappi", merchantReviewed = true))
        val result = confirm(owner, "import")
        assertTrue(result.completed); assertEquals(2, result.registered); assertTrue(result.learningWarnings.isEmpty())
        coVerify(exactly = 2) { categories.saveMerchantAliasRule(any(), null) }
        coVerify(exactly = 1) { preference(UserId(owner), MerchantId("tambo"), CategoryId("category")) }
        coVerify(exactly = 1) { preference(UserId(owner), MerchantId("rappi"), CategoryId("category")) }
    }

    @Test fun `unverified Premium prevents both kinds of learning`() = runTest {
        catalog("Tambo")
        every { categories.observePremiumVerified(UserId(owner)) } returns flowOf(false)
        use(line().copy(merchantId = "tambo", merchantReviewed = true))
        assertTrue(confirm(owner, "import").completed)
        coVerify(exactly = 0) { categories.saveMerchantAliasRule(any(), any()) }
        coVerify(exactly = 0) { preference(any(), any(), any()) }
    }

    @Test fun `reopening review preserves merchant rejection and explicit category`() = runTest {
        catalog("Tambo")
        use(line().copy(originalDescription = "YQ-TAMBO BARRERA-R", description = "YQ-TAMBO BARRERA-R", merchantReviewed = true))
        val prepared = review(owner, "import")!!.lines.single()
        assertNull(prepared.merchantId); assertNull(prepared.suggestedMerchantId); assertEquals("category", prepared.categoryId)
        coVerify(exactly = 0) { store.setMerchantSuggestion(any(), any(), any()) }
    }

    @Test fun `alias learning failure is warned while preference and durable registration succeed`() = runTest {
        catalog("Tambo")
        coEvery { categories.saveMerchantAliasRule(any(), any()) } returns Result.failure(IllegalStateException("offline"))
        use(line().copy(merchantId = "tambo", merchantReviewed = true))
        val result = confirm(owner, "import")
        assertTrue(result.completed); assertTrue(result.failed.isEmpty()); assertEquals(1, purchases.size)
        assertEquals(StatementLearningWarningKind.ALIAS_SAVE_FAILED, result.learningWarnings.single().kind)
        coVerify(exactly = 1) { preference(UserId(owner), MerchantId("tambo"), CategoryId("category")) }
    }

    @Test fun `deposit debit and credit register expense and income in linked account and retry is idempotent`() = runTest {
        deposit()
        use(line("debit", StatementLineKind.ACCOUNT_DEBIT), line("credit", StatementLineKind.ACCOUNT_CREDIT))
        assertTrue(confirm(owner, "import").completed)
        assertEquals(listOf(MovementType.INCOME, MovementType.EXPENSE), accountCommands.map { it.type })
        assertTrue(accountCommands.all { it.sourceAccountId == "linked-account" && it.ignoreSimilarityWarning &&
            it.occurredAt == java.time.Instant.parse("2026-09-20T17:00:00Z").toEpochMilli() })
        assertTrue(confirm(owner, "import").completed)
        assertEquals(2, accountCommands.size)
    }
    @Test fun `deposit registers chronologically credits before debits each day and preserves PDF order`() = runTest {
        deposit()
        val debit = line("debit", StatementLineKind.ACCOUNT_DEBIT)
        val credit = line("credit", StatementLineKind.ACCOUNT_CREDIT)
        use(debit.copy(position = 1, processDate = statementDate.minusDays(1)),
            credit.copy(id = "later", lineFingerprint = "later", position = 0, occurredDate = statementDate.plusDays(1)),
            credit.copy(id = "second", lineFingerprint = "second", position = 0, processDate = statementDate.plusDays(1)),
            credit.copy(position = 2),
            credit.copy(id = "first", lineFingerprint = "first", position = 1),
            debit.copy(id = "earlier", lineFingerprint = "earlier", position = 6, occurredDate = statementDate.minusDays(1)))
        assertTrue(confirm(owner, "import").completed)
        val expected = listOf("earlier", "first", "credit", "second", "debit", "later").map { id ->
            StatementResolutionKey.of("import", if (id in listOf("debit", "credit")) "fingerprint-$id" else id)
        }
        assertEquals(expected, accountCommands.map { it.idempotencyKey })
    }

    @Test fun `deposit rejects missing category wrong currency and credit card`() = runTest {
        deposit()
        use(line("category", StatementLineKind.ACCOUNT_CREDIT).copy(categoryId = null),
            line("currency", StatementLineKind.ACCOUNT_DEBIT).copy(currency = Currency.USD))
        assertEquals(listOf("category"), review.missingCategoryLineIds(owner, "import"))
        assertEquals(listOf(StatementLineFailureReason.CategoryRequired, StatementLineFailureReason.CurrencyMismatch), confirm(owner, "import").failed.map { it.reason })
        coEvery { matching.activeDebitCards(owner) } returns emptyList()
        assertTrue(confirm(owner, "import").failed.all { it.reason == StatementLineFailureReason.CardUnavailable })
        assertTrue(accountCommands.isEmpty())
    }
    @Test fun `deposit recovers receipt after split commit before edited category validation`() = runTest {
        deposit()
        use(line(kind = StatementLineKind.ACCOUNT_DEBIT))
        coEvery { store.markLineRegistered(owner, "line", any()) } returns false
        assertEquals(StatementLineFailureReason.RegistrationFailed, confirm(owner, "import").failed.single().reason)
        coEvery { store.markLineRegistered(owner, "line", any()) } answers {
            snapshot = snapshot.copy(lines = snapshot.lines.map { it.copy(status = StatementImportLineStatus.REGISTERED, selected = false) })
            true
        }
        snapshot = snapshot.copy(lines = snapshot.lines.map { it.copy(categoryId = null, amountMinor = 2000) })
        assertTrue(confirm(owner, "import").completed)
        assertEquals(1, accountCommands.size)
    }
    private val confirm = ConfirmStatementImport(store, access, matching, credit, pay, refund,
        movements, refundRepository, receipts, review, transactions, registerTransaction)

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

    @Test fun `refund without matching merchant remains pending even with one eligible purchase`() = runTest {
        purchases += purchase("other").copy(merchantProvisionalText = "Other merchant")
        use(line(kind = StatementLineKind.REFUND))
        val result = confirm(owner, "import")
        assertEquals(StatementLineFailureReason.RefundPurchaseRequired, result.failed.single().reason)
        assertEquals(StatementImportLineStatus.PENDING, snapshot.lines.single().status)
        assertTrue(refundCommands.isEmpty())
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
        coEvery { credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws CancellationException()
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
        coEvery { credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            allowed = false
            Result.success(Unit)
        }
        use(line("one"), line("two"))
        val result = confirm(owner, "import")
        assertEquals(1, result.registered)
        assertEquals(StatementLineFailure("two", StatementLineFailureReason.PremiumRequired), result.failed.single())
        assertFalse(result.completed)
        coVerify(exactly = 1) { credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `payment result cancellation propagates rather than becoming a partial failure`() = runTest {
        coEvery { pay(any(), any(), any(), any(), any()) } returns Result.failure(CancellationException())
        use(line(kind = StatementLineKind.CARD_PAYMENT).copy(sourceAccountId = "61000000-0000-4000-8000-000000000020"))
        try { confirm(owner, "import"); fail("Cancellation expected") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { store.markLineRegistered(any(), any(), any()) }
    }
}
