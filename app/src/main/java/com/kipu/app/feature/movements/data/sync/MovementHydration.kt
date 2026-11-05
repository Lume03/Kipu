package com.kipu.app.feature.movements.data.sync

import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditPaymentAllocationEntity
import com.kipu.app.feature.accounts.domain.model.CreditLiabilityAccountIds
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.accounts.data.remote.SyncFeedCursor
import com.kipu.app.feature.accounts.data.remote.parseFeedInstant
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementSyncCheckpointEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID
import androidx.room.withTransaction
import javax.inject.Inject

class MovementHydration @javax.inject.Inject constructor(
    private val movementDao: MovementDao,
    private val financialApi: FinancialInstrumentsApi,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val database: KipuDatabase,
    private val sessionCoordinator: SessionCoordinator,
) {
    private class InvalidSyncPage : RuntimeException()
    private val json = Json { ignoreUnknownKeys = true }
    private val FOREIGN_FEED_ENTITY_TYPES = setOf("CATEGORY", "BUDGET", "GOAL", "GOAL_EVENT", "DEBT", "DEBT_EVENT", "DEBT_INSTALLMENT", "SAVINGS_GOAL", "OBLIGATION", "RECURRENCE",
        // Merchant rules travel in the same feed and are applied by the categories sync; they must not block restoring money data.
        "MERCHANT_ALIAS_RULE", "MERCHANT_CATEGORY_PREFERENCE")
    private fun requireOwner(userId: String) = requireSyncOwner(sessionCoordinator, userId)
    private suspend fun <T> ownerRequest(userId: String, request: suspend () -> T): T {
        requireOwner(userId)
        return request().also { requireOwner(userId) }
    }
    private suspend fun <T> ownerTransaction(userId: String, block: suspend () -> T): T = database.withTransaction {
        requireOwner(userId)
        block().also { requireOwner(userId) }
    }
    suspend fun hydrateFully(userId: String): Boolean {
        requireOwner(userId)
        return pullChanges(userId, full = true)
    }
    suspend fun pullChanges(userId: String, full: Boolean = false): Boolean {
        var checkpoint = if (full) 0L else movementDao.getSyncCheckpoint(userId)?.sequence ?: 0L
        repeat(10000) {
            when (val response = ownerRequest(userId) { financialApi.pullChanges(PullChangesRequestDto(afterSequence = checkpoint), userId) }) {
                is FinancialApiResponse.Success -> {
                    val page = response.data
                    if (page.changes.isEmpty()) return !page.hasMore && page.nextSequence == checkpoint
                    if (!SyncFeedCursor.isValidPage(checkpoint, page)) return false
                    val appliedPage = try {
                        ownerTransaction(userId) {
                            var expectedSequence = checkpoint
                            for (change in page.changes.sortedBy { it.sequence }) {
                                val payload = change.payload
                                val applied = try {
                                    when (change.entityType.uppercase()) {
                                        "ACCOUNT" -> when (change.operation.uppercase()) {
                                            "UPSERT" -> payload?.let {
                                                applyPulledAccount(userId, json.decodeFromJsonElement<SyncAccountPayload>(it), change.revision)
                                            } ?: false
                                            "ARCHIVE" -> accountDao.getById(userId, change.entityId)?.let {
                                                if (change.revision >= it.remoteRevision && !hasUnsentInstrument(userId, change.entityId)) {
                                                    accountDao.setArchived(userId, change.entityId, true, System.currentTimeMillis() * 1_000L)
                                                    accountDao.updateRemoteRevision(userId, change.entityId, change.revision, System.currentTimeMillis() * 1_000L)
                                                }
                                                true
                                            } ?: false
                                            else -> false
                                        }
                                        "CARD" -> when (change.operation.uppercase()) {
                                            "UPSERT" -> payload?.let {
                                                applyPulledCard(userId, json.decodeFromJsonElement<SyncCardPayload>(it), change.revision)
                                            } ?: false
                                            "ARCHIVE" -> cardDao.getById(userId, change.entityId)?.let {
                                                if (change.revision >= it.remoteRevision && !hasUnsentInstrument(userId, change.entityId)) {
                                                    cardDao.setArchived(userId, change.entityId, true, System.currentTimeMillis() * 1_000L)
                                                    cardDao.updateRemoteRevision(userId, change.entityId, change.revision, System.currentTimeMillis() * 1_000L)
                                                }
                                                true
                                            } ?: false
                                            "DELETE" -> applyPulledCardDeletion(userId, change.entityId)
                                            else -> false
                                        }
                                        "TRANSACTION", "MOVEMENT" -> if (change.operation.equals("UPSERT", true) && payload != null) {
                                            applyPulledTransaction(userId, json.decodeFromJsonElement<SyncTransactionPayload>(payload), change.revision)
                                        } else false
                                        // Shared-feed types applied by their own sync must not block restoring
                                        // money data; an unknown future type still rolls the page back.
                                        in FOREIGN_FEED_ENTITY_TYPES -> true
                                        else -> false
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    SecureLog.e("SyncMovementsWorker", "Could not apply sync change: ${e.javaClass.simpleName}")
                                    false
                                }
                                if (!applied) {
                                    SecureLog.w("SyncMovementsWorker", "Rejected sync change ${change.sequence} ${change.entityType} ${change.operation}")
                                    throw InvalidSyncPage()
                                }
                                expectedSequence = change.sequence
                            }
                            if (expectedSequence != page.nextSequence) throw InvalidSyncPage()
                            movementDao.saveSyncCheckpoint(MovementSyncCheckpointEntity(userId, expectedSequence))
                            checkpoint = expectedSequence
                            true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SecureLog.e("SyncMovementsWorker", "Sync page rolled back: ${e.javaClass.simpleName}")
                        false
                    }
                    if (!appliedPage) return false
                    if (!page.hasMore) return true
                }
                is FinancialApiResponse.Error, is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

    /** Replays the already-applied feed only to reconcile ledger nets; never moves the checkpoint. */
    suspend fun repairDuplicatedLedger(userId: String): Boolean {
        val checkpoint = movementDao.getSyncCheckpoint(userId)?.sequence ?: return true
        var cursor = 0L
        var allEligibleRepaired = true
        while (cursor < checkpoint) {
            val response = try {
                ownerRequest(userId) { financialApi.pullChanges(PullChangesRequestDto(afterSequence = cursor), userId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return false
            }
            val page = when (response) {
                is FinancialApiResponse.Success -> response.data
                is FinancialApiResponse.Error, is FinancialApiResponse.NetworkFailure -> return false
            }
            for (change in page.changes.sortedBy { it.sequence }) {
                if (change.sequence > checkpoint) return allEligibleRepaired
                val entityType = change.entityType.uppercase()
                if ((entityType != "TRANSACTION" && entityType != "MOVEMENT") || !change.operation.equals("UPSERT", true)) continue
                val payload = change.payload ?: continue
                try {
                    val remote = json.decodeFromJsonElement<SyncTransactionPayload>(payload)
                    if (remote.status.equals("VOIDED", ignoreCase = true)) continue
                    ownerTransaction(userId) {
                        val local = movementDao.getTransactionById(userId, remote.id) ?: return@ownerTransaction
                        if (local.revision != change.revision || local.status == "VOIDED" ||
                            movementDao.getPendingOutboxForTransaction(userId, local.id).isNotEmpty()
                        ) return@ownerTransaction
                        // Throw so partial writes, projection failure and reconciliation fail together.
                        if (!reconcilePulledLedgerEntries(userId, remote, local.occurredAt, change.revision)) throw InvalidSyncPage()
                        movementDao.getLedgerEntriesForTransaction(userId, local.id).map { it.accountId }.distinct().forEach { accountId ->
                            val account = accountDao.getById(userId, accountId) ?: throw InvalidSyncPage()
                            movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                                userId = userId, accountId = accountId,
                                balanceMinor = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L,
                                currencyCode = account.currency, lastTransactionAt = local.occurredAt,
                            ))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    allEligibleRepaired = false
                    SecureLog.w("SyncMovementsWorker", "Ledger repair skipped change ${change.sequence}: ${e.javaClass.simpleName}")
                }
            }
            if (page.nextSequence <= cursor) return false
            if (!page.hasMore) return allEligibleRepaired && page.nextSequence >= checkpoint
            cursor = page.nextSequence
        }
        return allEligibleRepaired
    }

    private suspend fun applyPulledAccount(userId: String, remote: SyncAccountPayload, revision: Long): Boolean =
        ownerTransaction(userId) {
            if (remote.id.isBlank() || remote.currency !in setOf("PEN", "USD") || remote.initialBalanceMinorUnits < 0L) {
                return@ownerTransaction false
            }
            if (remote.type == "CREDIT_LIABILITY" && remote.initialBalanceMinorUnits != 0L) {
                return@ownerTransaction false
            }
            if (remote.linkedAccountId != null && remote.type != "WALLET") return@ownerTransaction false
            val existing = accountDao.getById(userId, remote.id)
            if (existing != null && (revision < existing.remoteRevision || hasUnsentInstrument(userId, remote.id))) return@ownerTransaction true
            val localType = if (remote.type == "WALLET") "DIGITAL_WALLET" else remote.type
            if (existing != null && (existing.type != localType || existing.currency != remote.currency)) {
                return@ownerTransaction false
            }
            if (existing == null) {
                val openedAt = parseFeedInstant(remote.openedAt).toEpochMilli()
                accountDao.insertIfAbsent(AccountEntity(
                    id = remote.id, userId = userId,
                    creationOperationId = remote.creationOperationId ?: "remote:${remote.id}",
                    alias = remote.alias, type = if (remote.type == "WALLET") "DIGITAL_WALLET" else remote.type, currency = remote.currency,
                    presetId = remote.presetId, color = remote.color, icon = remote.icon,
                    initialBalanceMinorUnits = remote.initialBalanceMinorUnits,
                    openedAt = openedAt, remoteRevision = revision,
                    createdAt = openedAt, updatedAt = System.currentTimeMillis(),
                    linkedAccountId = remote.linkedAccountId,
                ))
                if (remote.initialBalanceMinorUnits != 0L) {
                    movementDao.insertPulledLedgerEntries(listOf(LedgerEntryEntity(
                        id = "remote:opening:${remote.id}", userId = userId,
                        transactionId = "remote:opening:${remote.id}", accountId = remote.id,
                        role = "DESTINATION", signedAmountMinor = remote.initialBalanceMinorUnits,
                        currencyCode = remote.currency, createdAt = openedAt,
                    )))
                }
            }
            accountDao.updateAppearance(userId, remote.id, remote.alias, remote.presetId, remote.color, remote.icon, System.currentTimeMillis() * 1_000L)
            accountDao.setArchived(userId, remote.id, remote.isArchived, System.currentTimeMillis() * 1_000L)
            if (existing != null && remote.linkedAccountId != null) {
                accountDao.applyRemoteAffiliation(
                    userId, remote.id, remote.linkedAccountId, remote.presetId, revision,
                    System.currentTimeMillis() * 1_000L,
                )
            }
            accountDao.updateRemoteRevision(userId, remote.id, revision, System.currentTimeMillis() * 1_000L)
            val account = accountDao.getById(userId, remote.id) ?: return@ownerTransaction false
            val balance = movementDao.calculateLedgerSumForAccount(userId, remote.id)
                ?: remote.initialBalanceMinorUnits
            movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                userId = userId, accountId = remote.id, balanceMinor = balance,
                currencyCode = account.currency, lastTransactionAt = account.openedAt,
            ))
            true
        }

    private suspend fun applyPulledCard(userId: String, remote: SyncCardPayload, revision: Long): Boolean =
        ownerTransaction(userId) {
            if (remote.id.isBlank() || remote.id != remote.id.trim()) return@ownerTransaction false
            if (remote.type !in setOf("CREDIT", "DEBIT") || remote.currency !in setOf("PEN", "USD")) return@ownerTransaction false
            if (!Card.isValidLastFourDigits(remote.lastFourDigits)) return@ownerTransaction false
            if (remote.issuer.trim().length !in 1..80 || Card.isForbiddenSensitiveCardInput(remote.issuer)) return@ownerTransaction false
            if (remote.alias?.let { it.trim().length !in 1..80 } == true) return@ownerTransaction false
            if (remote.network !in setOf("VISA", "MASTERCARD", "AMEX", "DINERS", "OTHER")) return@ownerTransaction false
            val accountId = remote.accountId
            if (remote.type == "DEBIT") {
                val linkedAccount = accountId?.let { accountDao.getById(userId, it) } ?: return@ownerTransaction false
                if (linkedAccount.type == "CREDIT_LIABILITY" || linkedAccount.currency != remote.currency) return@ownerTransaction false
            }
            if (remote.type == "CREDIT") {
                if (accountId == null || remote.creditLimitMinorUnits == null || remote.creditLimitMinorUnits < 0L ||
                    remote.billingDay !in 1..31 || remote.dueDay !in 1..31
                ) return@ownerTransaction false
                var liabilityAccount = accountDao.getById(userId, accountId)
                if (liabilityAccount == null) {
                    val openedAt = remote.createdAt?.let { runCatching { parseFeedInstant(it).toEpochMilli() }.getOrNull() }
                        ?: System.currentTimeMillis()
                    accountDao.insertIfAbsent(
                        AccountEntity(
                            id = accountId,
                            userId = userId,
                            creationOperationId = CreditLiabilityAccountIds.creationOperationId(userId, remote.id),
                            alias = "Pasivo tarjeta •••• ${remote.lastFourDigits}",
                            type = "CREDIT_LIABILITY",
                            currency = remote.currency,
                            presetId = null,
                            color = null,
                            icon = null,
                            initialBalanceMinorUnits = 0L,
                            openedAt = openedAt,
                            isArchived = false,
                            remoteRevision = 0L,
                            createdAt = openedAt,
                            updatedAt = System.currentTimeMillis() * 1_000L,
                        ),
                    )
                    liabilityAccount = accountDao.getById(userId, accountId)
                }
                if (liabilityAccount?.type != "CREDIT_LIABILITY" || liabilityAccount.currency != remote.currency ||
                    liabilityAccount.initialBalanceMinorUnits != 0L
                ) return@ownerTransaction false
            }
            if (remote.type == "DEBIT" && (remote.creditLimitMinorUnits != null || remote.billingDay != null || remote.dueDay != null)) return@ownerTransaction false

            val existing = cardDao.getById(userId, remote.id)
            if (existing != null && (revision < existing.remoteRevision || hasUnsentInstrument(userId, remote.id))) return@ownerTransaction true
            val nowMicros = System.currentTimeMillis() * 1_000L
            val createdAt = remote.createdAt?.let { runCatching { parseFeedInstant(it).toEpochMilli() }.getOrNull() }
                ?: existing?.createdAt
                ?: System.currentTimeMillis()
            val projection = CardEntity(
                id = remote.id,
                userId = userId,
                creationOperationId = existing?.creationOperationId ?: remote.creationOperationId ?: "remote:${remote.id}",
                accountId = accountId,
                alias = remote.alias,
                type = remote.type,
                currency = remote.currency,
                network = remote.network,
                issuer = remote.issuer,
                lastFourDigits = remote.lastFourDigits,
                creditLimitMinorUnits = remote.creditLimitMinorUnits,
                billingDay = remote.billingDay,
                dueDay = remote.dueDay,
                presetId = remote.presetId,
                stylePresetId = remote.stylePresetId,
                color = remote.color,
                icon = remote.icon,
                isArchived = remote.isArchived,
                remoteRevision = revision,
                createdAt = createdAt,
                updatedAt = nowMicros,
                personalTeaBps = remote.personalTeaBps,
                closingDayInclusive = remote.closingDayInclusive ?: existing?.closingDayInclusive ?: false,
                cycleDateBasis = com.kipu.app.core.finance.domain.CycleDateBasis.fromValue(
                    remote.cycleDateBasis ?: existing?.cycleDateBasis,
                ).name,
            )
            if (existing == null) cardDao.insert(projection) else cardDao.update(projection)
            true
        }

    private suspend fun applyPulledCardDeletion(userId: String, cardId: String): Boolean =
        ownerTransaction(userId) {
            val existing = cardDao.getById(userId, cardId) ?: return@ownerTransaction true
            if (hasUnsentInstrument(userId, cardId)) return@ownerTransaction true
            if (cardDao.countTransactionsForCard(userId, cardId) != 0) return@ownerTransaction false
            cardDao.delete(userId, existing.id)
            true
        }

    private fun hasUnsentInstrument(userId: String, aggregateId: String): Boolean =
        database.openHelper.readableDatabase.query(androidx.sqlite.db.SimpleSQLiteQuery(
            "SELECT EXISTS(SELECT 1 FROM instrument_sync_outbox WHERE user_id=? AND aggregate_id=? AND state IN ('PENDING','IN_FLIGHT','ERROR'))",
            arrayOf(userId, aggregateId),
        )).use { it.moveToFirst(); it.getInt(0) != 0 }

    private suspend fun applyPulledTransaction(
        userId: String,
        remote: SyncTransactionPayload,
        revision: Long,
    ): Boolean = ownerTransaction(userId) {
        // The server names an ordinary movement STANDARD; locally that kind is null.
        val remoteKind = remote.operationKind?.takeUnless { it.equals("STANDARD", ignoreCase = true) }
        val existing = movementDao.getTransactionById(userId, remote.id)
        val accountIds = remote.ledgerEntries.map { it.accountId }.distinct()
        if (accountIds.any { accountDao.getById(userId, it) == null }) return@ownerTransaction false
        val isCardPurchase = remoteKind.equals("CARD_PURCHASE", true)
        val isCardPayment = remoteKind.equals("CARD_PAYMENT", true)
        if (isCardPurchase || isCardPayment) {
            val cardId = remote.cardId ?: return@ownerTransaction false
            val card = cardDao.getById(userId, cardId) ?: return@ownerTransaction false
            if (card.type != "CREDIT" || card.currency != remote.currencyCode) return@ownerTransaction false
            if (isCardPurchase && (remote.type != "EXPENSE" || remote.sourceAccountId != null || remote.installmentCount?.let { it in 1..36 } != true || remote.categoryId.isNullOrBlank())) return@ownerTransaction false
            if (isCardPayment && (remote.type != "TRANSFER" || remote.sourceAccountId == null || remote.destinationAccountId != null)) return@ownerTransaction false
        } else if (remote.cardId != null || remote.installments.isNotEmpty() || remote.allocations.isNotEmpty()) {
            return@ownerTransaction false
        }
        if (isCardPurchase) {
            val card = cardDao.getById(userId, requireNotNull(remote.cardId)) ?: return@ownerTransaction false
            val liabilityId = card.accountId ?: return@ownerTransaction false
            val liability = remote.ledgerEntries.singleOrNull() ?: return@ownerTransaction false
            if (liability.accountId != liabilityId || liability.role != "LIABILITY" ||
                liability.signedAmountMinor != -remote.amountMinor || liability.currencyCode != remote.currencyCode
            ) return@ownerTransaction false
        }
        if (isCardPayment) {
            val card = cardDao.getById(userId, requireNotNull(remote.cardId)) ?: return@ownerTransaction false
            val liabilityId = card.accountId ?: return@ownerTransaction false
            val sourceId = remote.sourceAccountId ?: return@ownerTransaction false
            val entries = remote.ledgerEntries
            if (remote.allocations.isEmpty() || entries.size != 2 ||
                entries.count { it.accountId == sourceId && it.role == "SOURCE" && it.signedAmountMinor == -remote.amountMinor && it.currencyCode == remote.currencyCode } != 1 ||
                entries.count { it.accountId == liabilityId && it.role == "LIABILITY" && it.signedAmountMinor == remote.amountMinor && it.currencyCode == remote.currencyCode } != 1 ||
                entries.sumOf { it.signedAmountMinor } != 0L
            ) return@ownerTransaction false
        }

        if (existing != null) {
            if (revision < existing.revision) {
                return@ownerTransaction true
            }
            if (existing.cardId != remote.cardId || existing.operationKind != remoteKind ||
                existing.installmentCount != remote.installmentCount
            ) return@ownerTransaction false

            val pendingLocal = movementDao.getPendingOutboxForTransaction(userId, remote.id)
            if (pendingLocal.isNotEmpty() && revision <= existing.revision) return@ownerTransaction true
            if (pendingLocal.isNotEmpty() && revision > existing.revision) {
                val pending = pendingLocal.first()
                val revId = pending.revisionId
                val rev = revId?.let { movementDao.getRevision(userId, it) }
                if (rev != null) {
                    movementDao.insertHydratedConflictProposal(
                        MovementConflictProposalEntity(
                            userId = userId,
                            proposalId = java.util.UUID.randomUUID().toString(),
                            transactionId = remote.id,
                            commandId = pending.id,
                            baseRevision = rev.baseRevision,
                            proposedRevisionId = rev.revisionId,
                            proposedSnapshot = rev.newPayload,
                            localRevisionIds = rev.revisionId,
                            remoteRevisionId = null,
                            resolution = "UNRESOLVED",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                movementDao.updateTransactionSyncStatus(userId, remote.id, MovementSyncStatus.CONFLICT.name)
                return@ownerTransaction true
            }

            val occurredAt = runCatching { parseFeedInstant(remote.occurredAt).toEpochMilli() }.getOrNull() ?: existing.occurredAt
            if (isCardPurchase && !applyPulledInstallmentSnapshot(userId, remote, occurredAt)) return@ownerTransaction false
            if (isCardPayment && !applyPulledPaymentAllocations(userId, remote, occurredAt)) return@ownerTransaction false
            if (!reconcilePulledLedgerEntries(userId, remote, occurredAt, revision)) return@ownerTransaction false

            movementDao.updateTransactionFromRemote(
                userId = userId,
                transactionId = remote.id,
                amountMinor = remote.amountMinor,
                currencyCode = remote.currencyCode,
                sourceAccountId = remote.sourceAccountId,
                destinationAccountId = remote.destinationAccountId,
                categoryId = remote.categoryId,
                merchantId = remote.merchantId,
                merchantProvisionalText = remote.merchantProvisionalText,
                occurredAt = occurredAt,
                note = remote.note,
                status = remote.status,
                revision = revision,
                syncStatus = MovementSyncStatus.SYNCED.name,
                updatedAt = System.currentTimeMillis(),
            )

            val allAccountIds = (accountIds + listOfNotNull(existing.sourceAccountId, existing.destinationAccountId)).distinct()
            allAccountIds.forEach { accountId ->
                val account = accountDao.getById(userId, accountId) ?: return@forEach
                val balance = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L
                movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                    userId = userId, accountId = accountId, balanceMinor = balance,
                    currencyCode = account.currency, lastTransactionAt = occurredAt,
                ))
            }
            return@ownerTransaction true
        }
        val occurredAt = parseFeedInstant(remote.occurredAt).toEpochMilli()
        movementDao.insertPulledTransaction(TransactionEntity(
            id = remote.id, userId = userId, type = remote.type,
            amountMinor = remote.amountMinor, currencyCode = remote.currencyCode,
            sourceAccountId = remote.sourceAccountId, destinationAccountId = remote.destinationAccountId,
            categoryId = remote.categoryId, merchantId = remote.merchantId,
            merchantProvisionalText = remote.merchantProvisionalText,
            occurredAt = occurredAt, note = remote.note,
            status = remote.status, syncStatus = MovementSyncStatus.SYNCED.name,
            createdAt = occurredAt, updatedAt = occurredAt,
            cardId = remote.cardId,
            operationKind = remoteKind,
            installmentCount = remote.installmentCount,
            source = remote.paymentChannel,
        ))
        if (isCardPurchase && !applyPulledInstallmentSnapshot(userId, remote, occurredAt)) return@ownerTransaction false
        if (isCardPayment && !applyPulledPaymentAllocations(userId, remote, occurredAt)) return@ownerTransaction false
        if (!reconcilePulledLedgerEntries(userId, remote, occurredAt, revision)) return@ownerTransaction false
        accountIds.forEach { accountId ->
            val account = accountDao.getById(userId, accountId) ?: return@ownerTransaction false
            val balance = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L
            movementDao.upsertBalanceProjection(com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                userId = userId, accountId = accountId, balanceMinor = balance,
                currencyCode = account.currency, lastTransactionAt = occurredAt,
            ))
        }
        true
    }

    private suspend fun reconcilePulledLedgerEntries(
        userId: String,
        remote: SyncTransactionPayload,
        occurredAt: Long,
        revision: Long,
    ): Boolean {
        val existingEntries = movementDao.getLedgerEntriesForTransaction(userId, remote.id)

        // A remote void carries ledger_effects instead of ledger_entries: neutralize what is still
        // net on each account, like a local void does. Deterministic ids keep it idempotent.
        if (remote.status.equals("VOIDED", ignoreCase = true) && remote.ledgerEntries.isEmpty()) {
            val compensations = existingEntries.groupBy { it.accountId }.mapNotNull { (accountId, entries) ->
                val net = entries.sumOf { it.signedAmountMinor }
                if (net == 0L) return@mapNotNull null
                val original = entries.first()
                LedgerEntryEntity(
                    id = "remote:void:${remote.id}:$accountId",
                    userId = userId,
                    transactionId = remote.id,
                    accountId = accountId,
                    role = original.role,
                    signedAmountMinor = -net,
                    currencyCode = original.currencyCode,
                    createdAt = occurredAt,
                )
            }
            if (compensations.isNotEmpty()) movementDao.insertPulledLedgerEntries(compensations)
            return true
        }

        if (existingEntries.isEmpty()) {
            if (remote.ledgerEntries.isEmpty()) return true
            movementDao.insertPulledLedgerEntries(remote.ledgerEntries.mapIndexed { index, entry ->
                LedgerEntryEntity(
                    id = entry.id ?: "remote:${remote.id}:ledger:$index",
                    userId = userId,
                    transactionId = remote.id,
                    accountId = entry.accountId,
                    role = entry.role,
                    signedAmountMinor = entry.signedAmountMinor,
                    currencyCode = entry.currencyCode,
                    createdAt = entry.createdAt?.let { runCatching { parseFeedInstant(it).toEpochMilli() }.getOrNull() } ?: occurredAt,
                )
            })
            return true
        }

        // Offline entries and server entries carry different UUIDs for the same financial effect
        // (the server also names them PRIMARY). The feed lists every entry of the movement, so
        // reconcile the authoritative net per account; never post an effect twice.
        if (remote.ledgerEntries.isEmpty()) return false
        val before = existingEntries.groupBy { it.accountId to it.currencyCode }
            .mapValues { (_, entries) -> entries.sumOf { it.signedAmountMinor } }
        val after = remote.ledgerEntries.groupBy { it.accountId to it.currencyCode }
            .mapValues { (_, entries) -> entries.sumOf { it.signedAmountMinor } }
        val corrections = (before.keys + after.keys).mapNotNull { (accountId, currency) ->
            val delta = Math.subtractExact(after[accountId to currency] ?: 0L, before[accountId to currency] ?: 0L)
            if (delta == 0L) return@mapNotNull null
            val role = remote.ledgerEntries.firstOrNull { it.accountId == accountId }?.role
                ?: existingEntries.first { it.accountId == accountId }.role
            LedgerEntryEntity(id = "remote:net:$revision:${remote.id}:$accountId:$currency:$delta", userId = userId,
                transactionId = remote.id, accountId = accountId, role = role, signedAmountMinor = delta,
                currencyCode = currency, createdAt = occurredAt)
        }
        if (corrections.isNotEmpty()) movementDao.insertPulledLedgerEntries(corrections)
        return true
    }

    private suspend fun applyPulledInstallmentSnapshot(
        userId: String,
        remote: SyncTransactionPayload,
        occurredAt: Long,
    ): Boolean {
        val count = remote.installmentCount ?: return false
        if (count !in 1..36 || remote.installments.size != count ||
            remote.installments.map { it.installmentNumber }.toSet().size != count ||
            remote.installments.sumOf { it.principalMinor } != remote.amountMinor
        ) return false
        val creditDao = database.creditDao()
        val current = creditDao.getInstallmentsForTransaction(userId, remote.id)
        val currentByNumber = current.associateBy { it.installmentNumber }
        val now = System.currentTimeMillis() * 1_000L
        if (current.isEmpty()) {
            val rows = remote.installments.map { installment ->
                if (installment.id.isBlank() || installment.principalMinor < 0L || installment.interestMinor < 0L) return false
                com.kipu.app.feature.accounts.data.remote.toLocalInstallment(
                    installment, userId, remote.id, count, occurredAt, now,
                )
            }
            creditDao.insertPulledInstallments(rows)
            return true
        }
        if (current.size != count) return false
        for (installment in remote.installments) {
            val local = currentByNumber[installment.installmentNumber] ?: return false
            if (installment.id.isBlank() || installment.principalMinor < 0L || installment.interestMinor < 0L) return false
            val allocated = creditDao.getAllocatedMinorForInstallment(userId, local.id)
            val status = when {
                allocated == 0L -> "PENDING"
                allocated >= Math.addExact(local.principalMinor, local.interestMinor) -> "PAID"
                else -> "PARTIAL"
            }
            val updated = creditDao.adoptRemoteInstallmentId(
                userId = userId,
                transactionId = remote.id,
                installmentNumber = installment.installmentNumber,
                remoteId = installment.id,
                dueDate = local.dueDate,
                principalMinor = local.principalMinor,
                interestMinor = local.interestMinor,
                status = status,
                revision = local.revision,
                updatedAt = now,
            )
            if (updated != 1) return false
        }
        creditDao.refreshInstallmentStatuses(userId)
        return true
    }

    private suspend fun applyPulledPaymentAllocations(
        userId: String,
        remote: SyncTransactionPayload,
        occurredAt: Long,
    ): Boolean {
        if (remote.allocations.any { it.amountMinor <= 0L } || remote.allocations.sumOf { it.amountMinor } != remote.amountMinor) return false
        val creditDao = database.creditDao()
        val existing = creditDao.getAllocationsForPayment(userId, remote.id)
        val remotePairs = remote.allocations.map { it.installmentId to it.amountMinor }.sortedBy { it.first }
        if (existing.isNotEmpty()) {
            return existing.map { it.installmentId to it.allocatedMinor }.sortedBy { it.first } == remotePairs
        }
        for (allocation in remote.allocations) {
            val installment = creditDao.getInstallmentById(userId, allocation.installmentId) ?: return false
            creditDao.insertPulledAllocations(listOf(
                CreditPaymentAllocationEntity(
                    id = "remote:${remote.id}:${allocation.installmentId}",
                    userId = userId,
                    paymentTransactionId = remote.id,
                    installmentId = allocation.installmentId,
                    allocatedMinor = allocation.amountMinor,
                    createdAt = occurredAt,
                ),
            ))
            val totalAllocated = creditDao.getAllocatedMinorForInstallment(userId, installment.id)
            creditDao.updateInstallmentStatus(
                userId = userId,
                installmentId = installment.id,
                status = if (totalAllocated >= Math.addExact(installment.principalMinor, installment.interestMinor)) "PAID" else "PARTIAL",
                revision = installment.revision + 1L,
                updatedAt = occurredAt,
            )
        }
        creditDao.refreshInstallmentStatuses(userId)
        return true
    }


}
