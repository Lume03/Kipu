package com.kipu.app.feature.setup

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.usecase.CreateLiquidAccount
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.usecase.EnsureInitialCategoryCatalog
import com.kipu.app.feature.plans.domain.ConfirmPlanSelection
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Pieces of the draft that are written once the owner exists; each one is retried on its own. */
enum class SetupStep { CATEGORIES, ACCOUNT, PLAN }

/**
 * Writes the initial setup when the verified owner confirms the summary: hides the categories the owner
 * did not keep, creates the first account and stores the plan preference. Steps already done are skipped on retry.
 */
class SetupFinalizer @Inject constructor(
    private val createAccount: CreateLiquidAccount,
    private val ensureCatalog: EnsureInitialCategoryCatalog,
    private val categories: CategoriesRepository,
    private val confirmPlan: ConfirmPlanSelection,
    private val planPreferences: PlanPreferencesRepository,
    private val instruments: FinancialInstrumentsRepository,
) {
    suspend fun apply(ownerId: String, draft: SetupDraft, done: MutableSet<SetupStep>): Result<Unit> {
        val owner = UserId(ownerId)
        if (SetupStep.CATEGORIES !in done) {
            step { applyCategories(owner, draft) }.onFailure { return Result.failure(it) }
            done += SetupStep.CATEGORIES
        }
        if (SetupStep.ACCOUNT !in done) {
            step { applyAccount(owner, draft.account) }.onFailure { return Result.failure(it) }
            done += SetupStep.ACCOUNT
        }
        if (SetupStep.PLAN !in done) {
            step { applyPlan(draft) }.onFailure { return Result.failure(it) }
            done += SetupStep.PLAN
        }
        return Result.success(Unit)
    }

    private suspend fun applyCategories(owner: UserId, draft: SetupDraft) {
        ensureCatalog(owner).getOrThrow()
        draft.hiddenCategoryIds.forEach { id -> categories.setCategoryActive(CategoryId(id), false).getOrThrow() }
    }

    private suspend fun applyAccount(owner: UserId, draft: SetupAccountDraft) {
        // A process restart after saving the account must not create a second opening balance.
        val accountId = AccountId(UUID.nameUUIDFromBytes("kipu:initial-account:${owner.value}".toByteArray()).toString())
        val existing = instruments.observeAccountById(accountId).first()
        if (existing != null) {
            check(existing.userId == owner) { "Initial account owner mismatch" }
            return
        }
        val type = requireNotNull(draft.accountType) { "Falta elegir el tipo de cuenta" }
        val preset = AccountPreset.normalizedFor(type, draft.preset)
        val currency = draft.currency
        val account = Account(
            id = accountId,
            userId = owner,
            alias = draft.alias.trim(),
            type = type,
            currency = currency,
            preset = preset,
            colorToken = preset?.defaultColorToken,
            iconToken = preset?.defaultIconToken,
            initialBalance = Money(requireNotNull(draft.balanceMinorUnits) { "Saldo inválido" }, currency),
            openedAt = Instant.now(),
        )
        createAccount(account).getOrThrow()
    }

    private suspend fun applyPlan(draft: SetupDraft) {
        val eligibility = runCatching { planPreferences.getTrialEligibility() }.getOrDefault(TrialEligibilitySnapshot.UNKNOWN)
        confirmPlan(draft.plan, eligibility)
    }

    private inline fun step(block: () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
