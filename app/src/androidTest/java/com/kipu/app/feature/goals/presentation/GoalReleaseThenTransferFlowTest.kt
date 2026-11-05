package com.kipu.app.feature.goals.presentation

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.goals.data.OfflineFirstGoalRepository
import com.kipu.app.feature.goals.data.local.*
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.*
import com.kipu.app.feature.movements.data.local.*
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.movements.presentation.QuickMovementContent
import com.kipu.app.feature.movements.presentation.QuickMovementUiState
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Two independent production forms backed by real Room commands; the host switch models opening a new movement. */
class GoalReleaseThenTransferFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun releaseLeavesMoneyInOriginalAccountUntilSeparateTransferIsExplicitlySaved() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).build()
        val owner = UUID.randomUUID().toString()
        val accountA = UUID.randomUUID().toString()
        val accountB = UUID.randomUUID().toString()
        try {
            listOf(accountA, accountB).forEach { id ->
                database.accountDao().insert(AccountEntity(id = id, userId = owner, creationOperationId = "create-$id",
                    alias = id, type = "SAVINGS", currency = "PEN", presetId = null, color = null, icon = null,
                    initialBalanceMinorUnits = 0, openedAt = 1, createdAt = 1, updatedAt = 1))
            }
            database.movementDao().insertTransaction(TransactionEntity("opening", owner, "INCOME", 10_000,
                "PEN", sourceAccountId = accountA, occurredAt = 1, status = "CONFIRMED", syncStatus = "SYNCED"))
            database.movementDao().insertLedgerEntries(listOf(LedgerEntryEntity("opening-entry", owner, "opening", accountA,
                "DESTINATION", 10_000, "PEN", 1)))
            database.goalDao().insertGoal(GoalEntity("goal", owner, "Viaje", 20_000, "PEN",
                revision = 1, syncState = "SYNCED", createdAt = 1, updatedAt = 1))
            database.goalDao().insertEvent(GoalEventEntity("allocation", owner, "goal", eventType = "ALLOCATION", deltaMinor = 5_000,
                currencyCode = "PEN", backingAccountId = accountA, operationId = "allocation", occurredAt = 1, createdAt = 1))
            val repository = OfflineFirstGoalRepository(GoalLocalDataSource(database, database.goalDao(), database.goalOutboxDao(),
                database.movementDao()), mockk(relaxed = true))
            val goal = repository.observeGoal(owner, "goal").first()!!
            val history = repository.observeEvents(owner, "goal").first()
            val account = GoalBackingAccount(accountA, "Ahorros", Currency.PEN, Money(10_000, Currency.PEN),
                Money(5_000, Currency.PEN), Money(5_000, Currency.PEN), Money.zero(Currency.PEN))
            val detail = GoalDetailUiState(userId = owner, goal = goal, events = history,
                progress = GoalProgressCalculator.calculate(goal, history), backingAccounts = listOf(account), isLoading = false)
            val openTransfer = mutableStateOf(false)
            val form = mutableStateOf(QuickMovementUiState(type = MovementType.TRANSFER,
                selectedSourceAccountId = accountA, selectedDestinationAccountId = accountB,
                availableAccounts = listOf(accountA, accountB).map { id ->
                    Account(id = AccountId(id), userId = UserId(owner), alias = id, type = AccountType.SAVINGS,
                        currency = Currency.PEN, initialBalance = Money.zero(Currency.PEN), openedAt = Instant.EPOCH)
                }))
            val releaseResult = AtomicReference<GoalCommandResult?>()
            val transferResult = AtomicReference<RegisterTransactionResult?>()
            val source = MovementLocalDataSource(database, database.movementDao(), BalanceProjectionStore(database.movementDao()))
            compose.setContent {
                val scope = rememberCoroutineScope()
                if (!openTransfer.value) GoalDetailScreen(detail, {}, {},
                    onRelease = { id, amount, _ -> scope.launch(Dispatchers.IO) {
                        releaseResult.set(repository.releaseGoalReserve(owner, "goal", id, amount))
                    } }, onReassign = { _, _, _ -> }, onArchive = {}, startWithWithdrawal = true)
                else QuickMovementContent(form.value, {}, { form.value = form.value.copy(amountText = it) }, {}, {}, {}, {}, {}, {}, {}, {}, {},
                    onSave = { scope.launch(Dispatchers.IO) {
                        transferResult.set(source.commitTransactionAtomic(RegisterTransactionCommand(UUID.randomUUID().toString(), owner,
                            MovementType.TRANSFER, java.math.BigDecimal(form.value.amountText).movePointRight(2).longValueExact(), "PEN",
                            accountA, accountB, occurredAt = 2), "transfer-after-release"))
                    } }, onClose = {}, accountBalances = mapOf(accountA to Money(10_000, Currency.PEN), accountB to Money.zero(Currency.PEN)),
                    creditCardDebts = emptyMap(), creditCardAvailableCredits = emptyMap(), mostUsedAccountId = null,
                    onNavigateToNewAccount = {}, onCreateSubcategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    isCreatingSubcategory = false, categoryCreationError = null)
            }
            compose.onNodeWithTag("goal_contribution_amount").performTextInput("20.00")
            compose.onNodeWithTag("goal_contribution_amount").performImeAction()
            compose.onNodeWithTag("goal_release_confirm").performClick()
            compose.waitUntil(5_000) { releaseResult.get() != null }
            assertTrue(releaseResult.get() is GoalCommandResult.Applied)
            assertEquals(10_000L, database.movementDao().calculateLedgerSumForAccount(owner, accountA))
            assertNull(transferResult.get())
            assertEquals(3_000L, GoalProgressCalculator.calculate(goal, repository.observeEvents(owner, "goal").first()).reserved.minorUnits)
            assertEquals(listOf("RELEASE_GOAL"), database.goalOutboxDao().getPendingForUser(owner).map { it.commandType })
            compose.runOnIdle { openTransfer.value = true }
            compose.onNodeWithTag("input_amount").performTextInput("20.00")
            compose.runOnIdle {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                    .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            assertEquals(10_000L, database.movementDao().calculateLedgerSumForAccount(owner, accountA))
            assertNull(transferResult.get())
            compose.onNodeWithTag("btn_save_transaction").performClick()
            compose.waitUntil(5_000) { transferResult.get() != null }
            assertTrue("Unexpected transfer result: ${transferResult.get()}", transferResult.get() is RegisterTransactionResult.Success)
            assertEquals(8_000L, database.movementDao().calculateLedgerSumForAccount(owner, accountA))
            assertEquals(2_000L, database.movementDao().calculateLedgerSumForAccount(owner, accountB))
            val transfer = (transferResult.get() as RegisterTransactionResult.Success).transaction
            assertEquals(MovementType.TRANSFER, transfer.type)
            assertEquals(2, database.movementDao().getLedgerEntriesForTransaction(owner, transfer.id).size)
            assertEquals(3_000L, GoalProgressCalculator.calculate(goal, repository.observeEvents(owner, "goal").first()).reserved.minorUnits)
        } finally { database.close() }
    }
}
