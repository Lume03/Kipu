package com.kipu.app.feature.movements.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.session.RemoteSession
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
import com.kipu.app.feature.movements.data.local.MovementRejectionResolver
import com.kipu.app.feature.movements.data.local.MovementOutboxEntity
import com.kipu.app.feature.movements.data.local.MovementSyncCheckpointEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.remote.MovementApiResponse
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import com.kipu.app.feature.movements.data.remote.RefundTransactionRequestDto
import com.kipu.app.feature.movements.data.remote.ReviseOrVoidTransactionRequestDto
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import java.util.UUID
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncMovementsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncMovementsWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncMovementsWorker.WORK_PREFIX}_$userId",
            // A newly committed operation must not wait behind the backoff of
            // an obsolete server failure. Commands remain in Room and leases
            // plus idempotency protect a cancelled in-flight attempt.
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(
            "${SyncMovementsWorker.WORK_PREFIX}_$userId"
        )
    }
}

@HiltWorker
class SyncMovementsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val movementDao: MovementDao,
    private val api: MovementApi,
    private val financialApi: FinancialInstrumentsApi,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val database: KipuDatabase,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    private val hydration = MovementHydration(movementDao, financialApi, accountDao, cardDao, database, sessionCoordinator)

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_PREFIX = "movement-sync"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private fun requireOwner(userId: String) = requireSyncOwner(sessionCoordinator, userId)

    private suspend fun <T> ownerRequest(userId: String, request: suspend () -> T): T {
        requireOwner(userId)
        return request().also { requireOwner(userId) }
    }

    private suspend fun <T> ownerTransaction(userId: String, block: suspend () -> T): T =
        database.withTransaction {
            requireOwner(userId)
            block().also { requireOwner(userId) }
        }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetUserId = inputData.getString(KEY_USER_ID) ?: currentVerifiedOwner

        if (targetUserId.isNullOrEmpty()) {
            SecureLog.w("SyncMovementsWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        if (targetUserId != currentVerifiedOwner) {
            SecureLog.w("SyncMovementsWorker", "Target owner differs from verified active owner")
            return Result.failure()
        }

        requireOwner(targetUserId)
        val recovery = applicationContext.getSharedPreferences("movement_contract_recovery", android.content.Context.MODE_PRIVATE)
        val recoveryKey = "wallet-channel-20261021-v4:$targetUserId"
        if (!recovery.getBoolean(recoveryKey, false)) {
            ownerTransaction(targetUserId) { movementDao.recoverMissingRegistrationEndpoint(targetUserId, System.currentTimeMillis()) }
            recovery.edit().putBoolean(recoveryKey, true).apply()
        }

        ownerTransaction(targetUserId) {
            if (movementDao.recoverAuthRejectedOutbox(targetUserId, System.currentTimeMillis()) > 0) {
                movementDao.markOutboxTransactionsPending(targetUserId)
            }
        }

        val remoteSession = sessionCoordinator.remoteSession.value
        if (remoteSession is RemoteSession.Valid && remoteSession.userId == targetUserId &&
            remoteSession.expiresAt.isAfter(Instant.now())) {
            ownerTransaction(targetUserId) { movementDao.clearAuthRetryBackoff(targetUserId, System.currentTimeMillis()) }
        }

        var anyFailed = false
        // Lease immediately before each network call, rather than leasing a waiting batch.
        for (index in 0 until 10) {
            val now = System.currentTimeMillis()
            val cmd = ownerTransaction(targetUserId) { movementDao.claimPendingOutbox(targetUserId, now, limit = 1).firstOrNull() } ?: break
            if (!processCommand(cmd, now)) anyFailed = true
        }

        if (!hydration.pullChanges(targetUserId)) anyFailed = true

        // Earlier pulls appended the server's copy of offline-registered entries, posting them twice.
        // Runs after pushing, so pending user changes never wait behind this one-time pass.
        val ledgerRepairKey = "ledger-net-20261026-v1:$targetUserId"
        if (!recovery.getBoolean(ledgerRepairKey, false)) {
            if (hydration.repairDuplicatedLedger(targetUserId)) {
                recovery.edit().putBoolean(ledgerRepairKey, true).apply()
            } else {
                anyFailed = true
            }
        }

        // Delayed retries, an unexpired lease or the batch limit must keep the worker scheduled.
        return if (anyFailed || movementDao.hasUnfinishedOutbox(targetUserId)) Result.retry() else Result.success()
    }

    private suspend fun processCommand(cmd: MovementOutboxEntity, now: Long): Boolean {
        requireOwner(cmd.userId)
        return try {
            when (cmd.commandType) {
                "CREATE_REFUND" -> processRefundCommand(cmd, now)
                "REVISE_TRANSACTION", "VOID_TRANSACTION" -> processRevisionCommand(cmd, now)
                else -> processRegisterCommand(cmd, now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.e("SyncMovementsWorker", "Error processing outbox command: ${e.javaClass.simpleName}")
            retryCommand(cmd, now, "PARSE_ERROR")
            false
        }
    }

    private suspend fun processRefundCommand(cmd: MovementOutboxEntity, now: Long): Boolean {
        val request = json.decodeFromString<RefundTransactionRequestDto>(cmd.payload)
        val purchase = movementDao.getTransactionById(cmd.userId, request.purchaseTransactionId)
        if (purchase == null) {
            return finishRefund(cmd, "FAILED_PERMANENT", null, "PURCHASE_NOT_FOUND", MovementSyncStatus.FAILED_PERMANENT.name, "REJECTED")
        }
        when (purchase.syncStatus) {
            MovementSyncStatus.CONFLICT.name, MovementSyncStatus.FAILED_PERMANENT.name ->
                return finishRefund(cmd, "FAILED_PERMANENT", null, "PURCHASE_NOT_SYNCED", MovementSyncStatus.FAILED_PERMANENT.name, "REJECTED")
            MovementSyncStatus.SYNCED.name -> Unit
            else -> {
                if (purchase.cardId != null) {
                    com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler(applicationContext)
                        .scheduleSync(cmd.userId)
                }
                retryCommand(cmd, now, "PURCHASE_SYNC_PENDING")
                return false
            }
        }

        val response = ownerRequest(cmd.userId) { api.registerRefund(request, cmd.userId) }
        return ownerTransaction(cmd.userId) {
            when (response) {
                is MovementApiResponse.Success -> when (response.data.status) {
                    "APPLIED", "DUPLICATE" -> {
                        if (response.data.transactionId != null && response.data.transactionId != cmd.aggregateId) {
                            finishRefund(cmd, "FAILED_PERMANENT", null, "TRANSACTION_ID_MISMATCH", MovementSyncStatus.FAILED_PERMANENT.name, "REJECTED")
                        } else {
                            finishRefund(cmd, "SYNCED", null, null, MovementSyncStatus.SYNCED.name, "SYNCED")
                        }
                    }
                    "REJECTED" -> finishRefund(
                        cmd,
                        "FAILED_PERMANENT",
                        null,
                        response.data.error?.code ?: "REJECTED",
                        MovementSyncStatus.FAILED_PERMANENT.name,
                        "REJECTED",
                    )
                    "CONFLICT" -> finishRefund(
                        cmd, "CONFLICT", null, "CONFLICT", MovementSyncStatus.CONFLICT.name, "CONFLICT",
                    )
                    else -> {
                        retryCommand(cmd, now, "UNKNOWN_STATUS")
                        false
                    }
                }
                is MovementApiResponse.Error -> {
                    val permanent = isPermanentHttpFailure(response.statusCode)
                    if (permanent) {
                        finishRefund(cmd, "FAILED_PERMANENT", null, "HTTP_${response.statusCode}", MovementSyncStatus.FAILED_PERMANENT.name, "REJECTED")
                    } else {
                        retryCommand(cmd, now, "HTTP_${response.statusCode}")
                        false
                    }
                }
                is MovementApiResponse.NetworkFailure -> {
                    if (response.exception is CancellationException) throw response.exception
                    retryCommand(cmd, now, "NETWORK_ERROR")
                    false
                }
            }
        }
    }

    private suspend fun finishRefund(
        cmd: MovementOutboxEntity,
        outboxState: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        transactionSyncStatus: String,
        receiptStatus: String,
    ): Boolean = ownerTransaction(cmd.userId) {
        val updated = movementDao.completeClaimedOutbox(
            cmd, outboxState, nextAttemptAt, errorCode, transactionSyncStatus,
        )
        if (updated) {
            movementDao.updateRefundReceiptStatus(cmd.userId, cmd.aggregateId, receiptStatus, System.currentTimeMillis())
        }
        updated && outboxState == "SYNCED"
    }

    private suspend fun processRegisterCommand(cmd: MovementOutboxEntity, now: Long): Boolean {
        val requestDto = json.decodeFromString<RegisterTransactionRequestDto>(cmd.payload)
        val transaction = requestDto.transaction
        if (transaction.paymentChannel != null && transaction.sourceAccountId != null) {
            val wallet = accountDao.getActiveAffiliatedWallet(cmd.userId, transaction.sourceAccountId, transaction.paymentChannel)
            val accountCommands = listOfNotNull(transaction.sourceAccountId, wallet?.id).mapNotNull { id ->
                database.instrumentSyncDao().getLatestForAggregate(cmd.userId, "ACCOUNT", id)
            }
            if (accountCommands.any { it.state != "SYNCED" }) {
                com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler(applicationContext).scheduleSync(cmd.userId)
                retryCommand(cmd, now, "ACCOUNT_SYNC_PENDING")
                return false
            }
        }
        val response = ownerRequest(cmd.userId) { api.registerTransaction(requestDto, cmd.userId) }
        return ownerTransaction(cmd.userId) {
            when (response) {
                is MovementApiResponse.Success -> when (response.data.status) {
                    "APPLIED", "DUPLICATE" -> {
                        movementDao.completeClaimedOutbox(
                            cmd, "SYNCED", null, null, MovementSyncStatus.SYNCED.name,
                        )
                        true
                    }
                    "CONFLICT" -> {
                        movementDao.completeClaimedOutbox(
                            cmd, "CONFLICT", null, "CONFLICT", MovementSyncStatus.CONFLICT.name,
                        )
                        false
                    }
                    "REJECTED" -> {
                        val error = response.data.error
                        if (error?.retryable == true) {
                            retryCommand(cmd, now, error.code)
                        } else {
                            MovementRejectionResolver(database, movementDao).rejectClaimed(cmd, error?.code ?: "REJECTED")
                        }
                        false
                    }
                    else -> {
                        movementDao.completeClaimedOutbox(cmd, "RETRY", now + 10_000L, "UNKNOWN_STATUS")
                        false
                    }
                }
                is MovementApiResponse.Error -> {
                    val isPermanent = isPermanentHttpFailure(response.statusCode)
                    if (isPermanent) {
                        MovementRejectionResolver(database, movementDao).rejectClaimed(
                            cmd, "HTTP_${response.statusCode}",
                        )
                    } else {
                        retryCommand(cmd, now, "HTTP_${response.statusCode}")
                    }
                    false
                }
                is MovementApiResponse.NetworkFailure -> {
                    if (response.exception is CancellationException) throw response.exception
                    retryCommand(cmd, now, "NETWORK_ERROR")
                    false
                }
            }
        }
    }

    private suspend fun processRevisionCommand(cmd: MovementOutboxEntity, now: Long): Boolean {
        if (cmd.dependsOnCommandId != null) {
            val parentReceipt = movementDao.getReceipt(cmd.userId, cmd.dependsOnCommandId)
            if (parentReceipt == null || parentReceipt.status !in listOf("APPLIED", "REVISED", "VOIDED")) {
                val parentOutbox = movementDao.getOutboxById(cmd.userId, cmd.dependsOnCommandId)
                if (parentOutbox?.state in listOf("CONFLICT", "FAILED_PERMANENT")) {
                    ownerTransaction(cmd.userId) {
                        movementDao.completeClaimedOutbox(
                            cmd, "FAILED_PERMANENT", null, "DEPENDENCY_FAILED",
                            MovementSyncStatus.FAILED_PERMANENT.name,
                        )
                    }
                    return false
                }
                ownerTransaction(cmd.userId) {
                    movementDao.updateOutboxResult(cmd.userId, cmd.id, "PENDING", now + 2_000L, "WAITING_DEPENDENCY")
                }
                return false
            }
        }

        val requestDto = json.decodeFromString<ReviseOrVoidTransactionRequestDto>(cmd.payload)
        val response = ownerRequest(cmd.userId) { api.reviseOrVoidTransaction(requestDto, cmd.userId) }
        return ownerTransaction(cmd.userId) {
            when (response) {
                is MovementApiResponse.Success -> when (response.data.status) {
                    "APPLIED", "DUPLICATE" -> {
                        movementDao.insertOrUpdateReceipt(
                            LocalCommandReceiptEntity(
                                userId = cmd.userId,
                                idempotencyKey = cmd.idempotencyKey,
                                requestHash = requestDto.requestHash,
                                transactionId = cmd.aggregateId,
                                status = response.data.result ?: "APPLIED",
                                responsePayload = cmd.payload,
                                contractVersion = requestDto.contractVersion,
                                commandType = cmd.commandType,
                                expectedRevision = cmd.expectedRevision,
                                resultingRevision = response.data.resultingRevision,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                        movementDao.completeClaimedOutbox(
                            cmd, "SYNCED", null, null, MovementSyncStatus.SYNCED.name,
                        )
                        true
                    }
                    "CONFLICT" -> {
                        val existingProposal = movementDao.getConflictProposals(cmd.userId, cmd.aggregateId)
                            .firstOrNull { it.commandId == cmd.idempotencyKey }
                        if (existingProposal == null) {
                            val latestRevision = movementDao.getRevisions(cmd.userId, cmd.aggregateId).lastOrNull()
                            if (latestRevision != null) {
                                movementDao.insertConflictProposal(
                                    com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity(
                                        userId = cmd.userId,
                                        proposalId = UUID.randomUUID().toString(),
                                        transactionId = cmd.aggregateId,
                                        commandId = cmd.idempotencyKey,
                                        baseRevision = cmd.expectedRevision ?: 1L,
                                        proposedRevisionId = latestRevision.revisionId,
                                        proposedSnapshot = cmd.payload,
                                        localRevisionIds = latestRevision.revisionId,
                                        remoteRevisionId = response.data.currentRevision?.toString(),
                                        resolution = "UNRESOLVED",
                                        createdAt = now,
                                        updatedAt = now,
                                    )
                                )
                            }
                        }
                        movementDao.completeClaimedOutbox(
                            cmd, "CONFLICT", null, "CONFLICT", MovementSyncStatus.CONFLICT.name,
                        )
                        false
                    }
                    "REJECTED" -> {
                        val error = response.data.error
                        if (error?.retryable == true) {
                            retryCommand(cmd, now, error.code)
                        } else {
                            MovementRejectionResolver(database, movementDao).rejectClaimed(cmd, error?.code ?: "REJECTED")
                        }
                        false
                    }
                    else -> {
                        movementDao.completeClaimedOutbox(cmd, "RETRY", now + 10_000L, "UNKNOWN_STATUS")
                        false
                    }
                }
                is MovementApiResponse.Error -> {
                    val isPermanent = isPermanentHttpFailure(response.statusCode)
                    if (isPermanent) {
                        MovementRejectionResolver(database, movementDao).rejectClaimed(
                            cmd, "HTTP_${response.statusCode}",
                        )
                    } else {
                        retryCommand(cmd, now, "HTTP_${response.statusCode}")
                    }
                    false
                }
                is MovementApiResponse.NetworkFailure -> {
                    if (response.exception is CancellationException) throw response.exception
                    retryCommand(cmd, now, "NETWORK_ERROR")
                    false
                }
            }
        }
    }

    private suspend fun retryCommand(cmd: MovementOutboxEntity, now: Long, errorCode: String) {
        ownerTransaction(cmd.userId) {
            val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
            movementDao.completeClaimedOutbox(cmd, "RETRY", nextBackoff, errorCode)
        }
    }
}

/** 401/403 mean the session expired, not that the command is wrong; 408/429 are transient. */
internal fun isPermanentHttpFailure(statusCode: Int): Boolean =
    statusCode in 400..499 && statusCode !in setOf(401, 403, 408, 429)

/** A late response is cancellation, never a retry that can write for the next session. */
internal fun requireSyncOwner(coordinator: SessionCoordinator, userId: String) {
    val owner = coordinator.currentOwner
    if (owner == null || owner.explicitlySignedOut || owner.verifiedUserId != userId) {
        throw CancellationException("Sync owner is no longer active")
    }
}
