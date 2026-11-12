package com.kipu.app.feature.accounts.data.sync

import com.kipu.app.core.sync.OutboxPolicy
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.kipu.app.core.session.RemoteSession
import android.content.Context
import androidx.room.withTransaction
import com.kipu.app.feature.movements.data.sync.requireSyncOwner
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
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
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.remote.CreateAccountRequestDto
import com.kipu.app.feature.accounts.data.remote.CommandResponseDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdatePersonalTeaRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdateCreditCardTermsRequestDto
import com.kipu.app.feature.accounts.data.remote.DeleteUnusedCardRequestDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.RecordOpeningAdjustmentRequestDto
import com.kipu.app.feature.accounts.data.remote.RegisterCardRequestDto
import com.kipu.app.feature.accounts.data.remote.SetArchivedRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdateAppearanceRequestDto
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementLedgerAliasEntity
import com.kipu.app.feature.movements.data.local.MovementLedgerEffectEntity
import com.kipu.app.feature.movements.data.local.TransactionRevisionEntity
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.MovementRevisionSnapshotCodec
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstrumentSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncInstrumentCommandsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncInstrumentCommandsWorker.KEY_USER_ID to userId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncInstrumentCommandsWorker.WORK_PREFIX}_$userId",
            // REPLACE, like movements: KEEP dropped a signal sent while the worker finished,
            // and leases plus idempotency protect a cancelled in-flight command.
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(
            "${SyncInstrumentCommandsWorker.WORK_PREFIX}_$userId"
        )
    }
}

@HiltWorker
class SyncInstrumentCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncDao: InstrumentSyncDao,
    private val cardDao: CardDao,
    private val accountDao: AccountDao,
    private val database: KipuDatabase,
    private val api: FinancialInstrumentsApi,
    private val movementDao: MovementDao,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_PREFIX = "instrument-sync"
        private const val COMMAND_LEASE_MICROS = 60_000_000L
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetUserId = inputData.getString(KEY_USER_ID) ?: currentVerifiedOwner

        if (targetUserId.isNullOrEmpty()) {
            SecureLog.w("SyncInstrumentWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        if (targetUserId != currentVerifiedOwner) {
            SecureLog.w("SyncInstrumentWorker", "Target owner differs from verified active owner")
            return Result.retry()
        }

        val recovery = applicationContext.getSharedPreferences("instrument_contract_recovery", android.content.Context.MODE_PRIVATE)
        val recoveryKey = "affiliation-20261021-v4:$targetUserId"
        if (!recovery.getBoolean(recoveryKey, false)) {
            ownerTransaction(targetUserId) { syncDao.recoverLegacyAffiliationCommands(targetUserId, System.currentTimeMillis() * 1_000L) }
            recovery.edit().putBoolean(recoveryKey, true).apply()
        }

        val remote = sessionCoordinator.remoteSession.value
        if (remote !is RemoteSession.Valid || remote.userId != targetUserId || !remote.expiresAt.isAfter(java.time.Instant.now())) {
            ownerTransaction(targetUserId) { syncDao.waitForAuth(targetUserId) }
            return Result.success()
        }
        ownerTransaction(targetUserId) { syncDao.resumeAuth(targetUserId) }
        val attemptedOperations = mutableSetOf<String>()
        var retryRequired = false
        suspend fun resultForRemainingCommands(): Result =
            if (retryRequired || syncDao.getPendingCount(targetUserId) > 0) Result.retry() else Result.success()

        // Re-query after each batch so a just-acknowledged predecessor can unlock its
        // successor in this run. Claim is a conditional Room update, so overlapping
        // workers cannot dispatch the same live lease.
        while (!isStopped) {
            currentCoroutineContext().ensureActive()
            // Revision zero is local-only. An edit queued before creation was
            // acknowledged must target its first official revision, never zero.
            // Revisit skipped edits after creation; the one-time contract marker
            // must not strand them while their predecessor is still pending.
            for (edit in syncDao.getPrematureCreationEdits(targetUserId)) {
                val parent = syncDao.getByOperationId(targetUserId, requireNotNull(edit.predecessorOperationId))
                if (parent?.state != "SYNCED" || parent.commandType !in setOf("CREATE_ACCOUNT", "REGISTER_CARD")) continue
                val payload = when (edit.commandType) {
                    "UPDATE_APPEARANCE" -> json.encodeToString(json.decodeFromString<UpdateAppearanceRequestDto>(edit.payloadJson).copy(expectedRevision = 1))
                    else -> {
                        val old = json.decodeFromString<UpdateCreditCardTermsRequestDto>(edit.payloadJson)
                        json.encodeToString(old.copy(card = old.card.copy(expectedRevision = 1)))
                    }
                }
                ownerTransaction(targetUserId) { syncDao.update(edit.copy(payloadJson = payload, expectedRevision = 1,
                    payloadHash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) },
                    state = "PENDING", errorCode = null, nextAttemptAt = null, leaseExpiresAt = null)) }
            }
            val nowMicros = System.currentTimeMillis() * 1_000L
            val pendingCommands = syncDao.getPendingCommands(targetUserId, nowMicros)
                .filterNot { it.operationId in attemptedOperations }
            if (pendingCommands.isEmpty()) return resultForRemainingCommands()

            var claimedAny = false
            for (cmd in pendingCommands) {
                currentCoroutineContext().ensureActive()
                if (isStopped) return Result.retry()
                val claimTime = System.currentTimeMillis() * 1_000L
                val claimed = ownerTransaction(targetUserId) { syncDao.claimCommand(
                    userId = targetUserId,
                    operationId = cmd.operationId,
                    nowMicros = claimTime,
                    leaseExpiresAt = claimTime + COMMAND_LEASE_MICROS,
                ) }
                if (claimed == 0) continue
                claimedAny = true
                attemptedOperations += cmd.operationId
                // Defer recoverable failures once per run without blocking independent
                // commands. The DAO still gates successors on their predecessor's SYNCED state.
                if (!processCommand(cmd, claimTime)) retryRequired = true
            }
            if (!claimedAny) return resultForRemainingCommands()
        }
        SecureLog.w("SyncInstrumentWorker", "Command batch limit reached; scheduling another pass")
        return Result.retry()
    }

    private suspend fun <T> ownerTransaction(owner: String, block: suspend () -> T): T =
        database.withTransaction {
            requireSyncOwner(sessionCoordinator, owner)
            block().also { requireSyncOwner(sessionCoordinator, owner) }
        }

    private suspend fun processCommand(cmd: InstrumentSyncOutboxEntity, nowMicros: Long): Boolean = try {
        dispatchCommand(chainRevision(cmd), nowMicros)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: kotlinx.serialization.SerializationException) {
        updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
        true
    } catch (_: Exception) {
        updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "NETWORK_ERROR")
        false
    }

    /** Persist once before sending; retries retain the exact request identity and revision. */
    private suspend fun chainRevision(cmd: InstrumentSyncOutboxEntity): InstrumentSyncOutboxEntity {
        if (cmd.attemptCount != 0 || cmd.predecessorOperationId == null) return cmd
        if (syncDao.getByOperationId(cmd.userId, cmd.predecessorOperationId)?.state != "SYNCED") return cmd
        val revision = when (cmd.aggregateType) {
            "ACCOUNT" -> accountDao.getById(cmd.userId, cmd.aggregateId)?.remoteRevision
            "CARD" -> cardDao.getById(cmd.userId, cmd.aggregateId)?.remoteRevision
            else -> null
        }?.coerceAtLeast(1L) ?: return cmd
        val payload = when (cmd.commandType) {
            "UPDATE_APPEARANCE" -> json.encodeToString(json.decodeFromString<UpdateAppearanceRequestDto>(cmd.payloadJson).copy(expectedRevision = revision))
            "UPDATE_CREDIT_CARD_TERMS" -> {
                val old = json.decodeFromString<UpdateCreditCardTermsRequestDto>(cmd.payloadJson)
                val rebased = old.copy(requestHash = "pending", card = old.card.copy(expectedRevision = revision))
                val canonical = json.encodeToString(rebased)
                val requestHash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
                json.encodeToString(rebased.copy(requestHash = requestHash))
            }
            // Personal TEA has no expected_revision in its wire contract; FIFO already serializes it.
            else -> return cmd
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        ownerTransaction(cmd.userId) { syncDao.updatePayload(cmd.userId, cmd.operationId, payload, hash, revision) }
        return cmd.copy(payloadJson = payload, payloadHash = hash, expectedRevision = revision)
    }

    private suspend fun dispatchCommand(cmd: InstrumentSyncOutboxEntity, nowMicros: Long): Boolean {
        requireSyncOwner(sessionCoordinator, cmd.userId)
        if (cmd.commandType in setOf("CONFIRM_CREDIT_PURCHASE", "PAY_CREDIT_CARD", "ALLOCATE_CREDIT_PAYMENT")) {
            return processCanonicalCreditCommand(cmd, nowMicros)
        }
        if (cmd.commandType == "UPDATE_CARD_PERSONAL_TEA") {
            return processPersonalTeaCommand(cmd, nowMicros)
        }
        if (cmd.commandType == "UPDATE_CREDIT_CARD_TERMS") {
            return processCreditCardTermsCommand(cmd, nowMicros)
        }

        val result: FinancialApiResponse<Any> = try {
            when (cmd.commandType) {
                "CREATE_ACCOUNT" -> {
                    val dto = json.decodeFromString<CreateAccountRequestDto>(cmd.payloadJson)
                    api.createAccount(dto, expectedOwner = cmd.userId)
                }
                "REGISTER_CARD" -> {
                    val dto = json.decodeFromString<RegisterCardRequestDto>(cmd.payloadJson)
                    val accountId = cardDao.getById(cmd.userId, dto.cardId)?.accountId
                    if (dto.type == "CREDIT" && accountId == null) {
                        FinancialApiResponse.Error(400, "Credit card liability account is missing")
                    } else {
                        api.registerCard(dto.copy(accountId = accountId ?: dto.accountId), expectedOwner = cmd.userId)
                    }
                }
                "UPDATE_DEBIT_CARD_IDENTITY" -> {
                    var dto = json.decodeFromString<com.kipu.app.feature.accounts.data.remote.UpdateDebitCardIdentityRequestDto>(cmd.payloadJson)
                    if (cmd.attemptCount == 0 && cmd.predecessorOperationId != null) {
                        val revision = cardDao.getById(cmd.userId, cmd.aggregateId)?.remoteRevision?.coerceAtLeast(1) ?: dto.expectedRevision
                        dto = dto.copy(expectedRevision = revision)
                        val payload = json.encodeToString(dto)
                        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
                        ownerTransaction(cmd.userId) { syncDao.updatePayload(cmd.userId, cmd.operationId, payload, hash, revision) }
                    }
                    api.updateDebitCardIdentity(dto, expectedOwner = cmd.userId)
                }
                "UPDATE_APPEARANCE" -> {
                    val dto = json.decodeFromString<UpdateAppearanceRequestDto>(cmd.payloadJson)
                    api.updateAppearance(dto, expectedOwner = cmd.userId)
                }
                "SET_ARCHIVED" -> {
                    val dto = json.decodeFromString<SetArchivedRequestDto>(cmd.payloadJson)
                    api.setArchived(dto, expectedOwner = cmd.userId)
                }
                "DELETE_UNUSED_CARD" -> {
                    val dto = json.decodeFromString<DeleteUnusedCardRequestDto>(cmd.payloadJson)
                    api.deleteUnusedCard(dto, expectedOwner = cmd.userId)
                }
                "RECORD_OPENING_ADJUSTMENT" -> {
                    val dto = json.decodeFromString<RecordOpeningAdjustmentRequestDto>(cmd.payloadJson)
                    api.recordOpeningAdjustment(dto, expectedOwner = cmd.userId)
                }
                else -> {
                    SecureLog.w("SyncInstrumentWorker", "Unknown command type: ${cmd.commandType}")
                    FinancialApiResponse.Error(400, "Unknown command type")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: kotlinx.serialization.SerializationException) {
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }

        requireSyncOwner(sessionCoordinator, cmd.userId)
        return when (result) {
            is FinancialApiResponse.Success -> {
                val response = result.data as? CommandResponseDto
                if (response?.status in setOf("CONFLICT", "REJECTED")) {
                    val failure = OutboxPolicy.classify(status = response?.status)
                    updateCreditCommandState(cmd, stateFor(failure), if (failure == OutboxPolicy.Failure.RETRYABLE) nowMicros + 30_000_000L else null, response?.error ?: response?.status)
                    return failure != OutboxPolicy.Failure.RETRYABLE
                }
                if (response == null || !response.status.equals("APPLIED", true) && !response.status.equals("DUPLICATE", true)) {
                    ownerTransaction(cmd.userId) { syncDao.updateState(cmd.userId, cmd.operationId, "ERROR", System.currentTimeMillis() * 1_000L + 10_000_000L, "INVALID_RESPONSE", System.currentTimeMillis() * 1_000L) }
                    return false
                }
                ownerTransaction(cmd.userId) {
                    if (cmd.commandType == "DELETE_UNUSED_CARD") {
                        val card = cardDao.getById(cmd.userId, cmd.aggregateId)
                        cardDao.delete(cmd.userId, cmd.aggregateId)
                        card?.accountId?.let { id ->
                            if (accountDao.getById(cmd.userId, id)?.type == "CREDIT_LIABILITY") accountDao.delete(cmd.userId, id)
                        }
                    }
                    when (cmd.aggregateType) {
                        "CARD" -> response.revision?.let {
                            ownerTransaction(cmd.userId) { cardDao.updateRemoteRevision(cmd.userId, response.cardId ?: cmd.aggregateId, it, System.currentTimeMillis() * 1_000L) }
                        }
                        "ACCOUNT" -> response.revision?.let {
                            ownerTransaction(cmd.userId) { accountDao.updateRemoteRevision(cmd.userId, response.accountId ?: cmd.aggregateId, it, System.currentTimeMillis() * 1_000L) }
                        }
                    }
                    ownerTransaction(cmd.userId) { syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L) }
                }
                true
            }
            is FinancialApiResponse.Error -> handleHttp(cmd, result.statusCode, nowMicros)
            is FinancialApiResponse.NetworkFailure -> {
                if (result.exception is kotlinx.coroutines.CancellationException) throw result.exception
                val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                val nextAttempt = (System.currentTimeMillis() + backoffSeconds * 1000L) * 1000L
                ownerTransaction(cmd.userId) { syncDao.updateState(
                    userId = cmd.userId,
                    operationId = cmd.operationId,
                    newState = "PENDING",
                    nextAttemptAt = nextAttempt,
                    errorCode = result.exception.message,
                    nowMicros = System.currentTimeMillis() * 1000L,
                ) }
                false
            }
        }
    }

    private suspend fun processCanonicalCreditCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val request = try {
            json.decodeFromString<CreditCommandRequestDto>(cmd.payloadJson)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.e("SyncInstrumentWorker", "Error preparing credit command: " + e.javaClass.simpleName)
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        }
        val transactionId = request.transaction.id
        val response = try {
            when (cmd.commandType) {
                "CONFIRM_CREDIT_PURCHASE" -> api.registerCreditPurchase(request, expectedOwner = cmd.userId)
                "PAY_CREDIT_CARD", "ALLOCATE_CREDIT_PAYMENT" -> api.allocateCreditPayment(request, expectedOwner = cmd.userId)
                else -> error("Unsupported canonical credit command")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.e("SyncInstrumentWorker", "Error sending credit command: " + e.javaClass.simpleName)
            updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "NETWORK_ERROR")
            return false
        }

        requireSyncOwner(sessionCoordinator, cmd.userId)
        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    if (response.data.transactionId != null && response.data.transactionId != transactionId) {
                        updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "TRANSACTION_ID_MISMATCH")
                        return false
                    }
                    if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE" &&
                        !reconcilePurchaseInstallments(cmd.userId, request, response.data.installments)
                    ) {
                        ownerTransaction(cmd.userId) {
                            updateCreditCommandState(cmd, "ERROR", System.currentTimeMillis() * 1_000L + 30_000_000L,
                                "INSTALLMENT_RECONCILIATION_FAILED")
                            movementDao.updateTransactionSyncStatus(cmd.userId, transactionId,
                                MovementSyncStatus.PENDING.name, System.currentTimeMillis())
                        }
                        SecureLog.w("SyncInstrumentWorker", "Credit purchase schedule reconciliation failed; local terms preserved, retry deferred to WorkManager backoff")
                        return false
                    }
                    ownerTransaction(cmd.userId) {
                        ownerTransaction(cmd.userId) { syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L) }
                        ownerTransaction(cmd.userId) { movementDao.updateTransactionSyncStatus(
                            userId = cmd.userId,
                            transactionId = transactionId,
                            syncStatus = MovementSyncStatus.SYNCED.name,
                            updatedAt = System.currentTimeMillis(),
                        ) }
                    }
                    if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE") {
                        // The server may have emitted a card utilization alert for this purchase.
                        com.kipu.app.feature.notifications.data.sync.WorkManagerNotificationSyncScheduler(applicationContext)
                            .scheduleSync(cmd.userId)
                    }
                    true
                }
                "CONFLICT" -> {
                    updateCreditCommandState(cmd, "CONFLICT", null, response.data.error?.code ?: "CONFLICT")
                    ownerTransaction(cmd.userId) { movementDao.updateTransactionSyncStatus(
                        cmd.userId, transactionId, MovementSyncStatus.CONFLICT.name, System.currentTimeMillis(),
                    ) }
                    true
                }
                "REJECTED" -> {
                    if (response.data.error?.retryable == true) {
                        updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, response.data.error?.code)
                        return false
                    }
                    rejectCreditCommand(cmd, transactionId, response.data.error?.code ?: "REJECTED")
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> handleHttp(cmd, response.statusCode, nowMicros, transactionId)
            is FinancialApiResponse.NetworkFailure -> {
                if (response.exception is kotlinx.coroutines.CancellationException) throw response.exception
                val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                updateCreditCommandState(
                    cmd,
                    "ERROR",
                    nowMicros + backoffSeconds * 1_000_000L,
                    "NETWORK_ERROR",
                )
                false
            }
        }
    }

    private suspend fun processPersonalTeaCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val request = try {
            json.decodeFromString<UpdatePersonalTeaRequestDto>(cmd.payloadJson)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        }
        val response = api.updatePersonalTea(request, expectedOwner = cmd.userId)
        requireSyncOwner(sessionCoordinator, cmd.userId)
        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    response.data.revision?.let {
                        // Local card metadata remains owner-scoped; update revision after server acceptance.
                        ownerTransaction(cmd.userId) { cardDao.updateRemoteRevision(cmd.userId, cmd.aggregateId, it, System.currentTimeMillis() * 1_000L) }
                    }
                    ownerTransaction(cmd.userId) { syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L) }
                    true
                }
                "CONFLICT", "REJECTED" -> {
                    val failure = OutboxPolicy.classify(status = response.data.status, retryable = response.data.error?.retryable)
                    updateCreditCommandState(cmd, stateFor(failure), if (failure == OutboxPolicy.Failure.RETRYABLE) nowMicros + 30_000_000L else null, response.data.error?.code ?: response.data.status)
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> handleHttp(cmd, response.statusCode, nowMicros)
            is FinancialApiResponse.NetworkFailure -> {
                if (response.exception is kotlinx.coroutines.CancellationException) throw response.exception
                updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "NETWORK_ERROR")
                false
            }
        }
    }

    private suspend fun processCreditCardTermsCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val request = try {
            json.decodeFromString<UpdateCreditCardTermsRequestDto>(cmd.payloadJson)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        }
        val response = api.updateCreditCardTerms(request, expectedOwner = cmd.userId)
        requireSyncOwner(sessionCoordinator, cmd.userId)
        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    response.data.revision?.let {
                        ownerTransaction(cmd.userId) { cardDao.updateRemoteRevision(cmd.userId, cmd.aggregateId, it, System.currentTimeMillis() * 1_000L) }
                    }
                    ownerTransaction(cmd.userId) { syncDao.markSynced(cmd.userId, cmd.operationId, System.currentTimeMillis() * 1_000L) }
                    true
                }
                "CONFLICT", "REJECTED" -> {
                    val failure = OutboxPolicy.classify(status = response.data.status, retryable = response.data.error?.retryable)
                    updateCreditCommandState(cmd, stateFor(failure), if (failure == OutboxPolicy.Failure.RETRYABLE) nowMicros + 30_000_000L else null, response.data.error?.code ?: response.data.status)
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> handleHttp(cmd, response.statusCode, nowMicros)
            is FinancialApiResponse.NetworkFailure -> {
                if (response.exception is kotlinx.coroutines.CancellationException) throw response.exception
                updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "NETWORK_ERROR")
                false
            }
        }
    }

    private fun stateFor(failure: OutboxPolicy.Failure): String = when (failure) {
        OutboxPolicy.Failure.RETRYABLE -> "ERROR"
        OutboxPolicy.Failure.WAITING_FOR_AUTH -> "WAITING_FOR_AUTH"
        OutboxPolicy.Failure.CONFLICT -> "CONFLICT"
        OutboxPolicy.Failure.PERMANENT -> "FAILED_PERMANENT"
    }

    private suspend fun handleHttp(cmd: InstrumentSyncOutboxEntity, code: Int, now: Long, transactionId: String? = null): Boolean {
        val failure = OutboxPolicy.classify(httpStatus = code)
        if (failure == OutboxPolicy.Failure.PERMANENT && transactionId != null) {
            rejectCreditCommand(cmd, transactionId, "HTTP_$code")
        } else {
            updateCreditCommandState(cmd, stateFor(failure), if (failure == OutboxPolicy.Failure.RETRYABLE) now + 30_000_000L else null, "HTTP_$code")
        }
        return failure != OutboxPolicy.Failure.RETRYABLE
    }

    private suspend fun reconcilePurchaseInstallments(
        userId: String,
        request: CreditCommandRequestDto,
        remoteInstallments: List<com.kipu.app.feature.accounts.data.remote.CreditInstallmentResponseDto>,
    ): Boolean = ownerTransaction(userId) {
        val count = request.transaction.installmentCount ?: return@ownerTransaction false
        if (count !in 1..36 || remoteInstallments.size != count) return@ownerTransaction false
        if (remoteInstallments.map { it.installmentNumber }.toSet().size != count) return@ownerTransaction false
        if (remoteInstallments.sumOf { it.principalMinor } != request.transaction.amountMinor) return@ownerTransaction false
        val localInstallments = database.creditDao().getInstallmentsForTransaction(userId, request.transaction.id)
        if (localInstallments.size != count) return@ownerTransaction false
        val localByNumber = localInstallments.associateBy { it.installmentNumber }
        if (remoteInstallments.any { remote ->
                val local = localByNumber[remote.installmentNumber]
                local == null || remote.id.isBlank() || remote.principalMinor < 0L || remote.interestMinor < 0L ||
                    java.time.LocalDate.parse(remote.dueDate).toEpochDay() != local.dueDate ||
                    remote.principalMinor != local.principalMinor || remote.interestMinor != local.interestMinor
            }) return@ownerTransaction false // Retry after migration, without partially adopting an incompatible plan.
        val now = System.currentTimeMillis() * 1_000L
        for (remote in remoteInstallments) {
            val local = requireNotNull(localByNumber[remote.installmentNumber])
            val updated = database.creditDao().adoptRemoteInstallmentId(
                userId = userId,
                transactionId = request.transaction.id,
                installmentNumber = remote.installmentNumber,
                remoteId = remote.id,
                // Confirmed local terms are immutable, including while the backend is still on the old contract.
                dueDate = local.dueDate,
                principalMinor = local.principalMinor,
                interestMinor = local.interestMinor,
                status = local.status,
                revision = 1L,
                updatedAt = now,
            )
            if (updated != 1) return@ownerTransaction false
        }
        database.creditDao().refreshInstallmentStatuses(userId)
        true
    }

    private suspend fun updateCreditCommandState(
        cmd: InstrumentSyncOutboxEntity,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
    ) {
        ownerTransaction(cmd.userId) {
            val current = syncDao.getByOperationId(cmd.userId, cmd.operationId) ?: return@ownerTransaction
            if (current.state != "IN_FLIGHT" || current.attemptCount != cmd.attemptCount + 1) return@ownerTransaction
            if (state in setOf("CONFLICT", "FAILED_PERMANENT") && cmd.commandType == "CREATE_ACCOUNT") {
                val ids = mutableListOf<String>()
                database.openHelper.writableDatabase.query(
                    "SELECT id FROM transactions WHERE user_id=? AND (source_account_id=? OR destination_account_id=?)",
                    arrayOf(cmd.userId, cmd.aggregateId, cmd.aggregateId),
                ).use { cursor -> while (cursor.moveToNext()) ids += cursor.getString(0) }
                val compensator = com.kipu.app.core.sync.LocalRejectionCompensator(database)
                ids.forEach { compensator.neutralize(cmd.userId, it, "ACCOUNT_SYNC_FAILED") }
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE financial_movements SET status='REJECTED' WHERE user_id=? AND account_id=?",
                    arrayOf(cmd.userId, cmd.aggregateId))
                accountDao.getById(cmd.userId, cmd.aggregateId)?.let { account ->
                    accountDao.update(account.copy(initialBalanceMinorUnits = 0L, isArchived = true))
                }
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE movement_outbox SET state='BLOCKED', last_error_code='ACCOUNT_SYNC_FAILED' WHERE user_id=? AND aggregate_id IN (SELECT id FROM transactions WHERE user_id=? AND (source_account_id=? OR destination_account_id=?)) AND state NOT IN ('SYNCED','DISCARDED')",
                    arrayOf(cmd.userId, cmd.userId, cmd.aggregateId, cmd.aggregateId))
            }
            if (state in setOf("CONFLICT", "FAILED_PERMANENT") && cmd.commandType == "REGISTER_CARD") {
                cardDao.setArchived(cmd.userId, cmd.aggregateId, true, System.currentTimeMillis() * 1_000L)
                val ids = mutableListOf<String>()
                database.openHelper.writableDatabase.query("SELECT id FROM transactions WHERE user_id=? AND card_id=?", arrayOf(cmd.userId, cmd.aggregateId)).use {
                    while (it.moveToNext()) ids += it.getString(0)
                }
                ids.forEach { com.kipu.app.core.sync.LocalRejectionCompensator(database).neutralize(cmd.userId, it, "CARD_SYNC_FAILED") }
                database.openHelper.writableDatabase.execSQL("UPDATE financial_movements SET status='REJECTED' WHERE user_id=? AND card_id=?", arrayOf(cmd.userId,cmd.aggregateId))
                database.openHelper.writableDatabase.execSQL("UPDATE credit_installments SET status='VOIDED' WHERE user_id=? AND transaction_id IN (SELECT id FROM transactions WHERE user_id=? AND card_id=?)", arrayOf(cmd.userId,cmd.userId,cmd.aggregateId))
            }
            syncDao.updateState(
            userId = cmd.userId,
            operationId = cmd.operationId,
            newState = state,
            nextAttemptAt = nextAttemptAt,
            errorCode = errorCode,
            nowMicros = System.currentTimeMillis() * 1_000L,
        ) }
    }

    private suspend fun rejectCreditCommand(
        cmd: InstrumentSyncOutboxEntity,
        transactionId: String,
        errorCode: String,
    ) {
        val nowMillis = System.currentTimeMillis()
        ownerTransaction(cmd.userId) {
            val claimed = syncDao.getByOperationId(cmd.userId, cmd.operationId) ?: return@ownerTransaction
            if (claimed.state != "IN_FLIGHT" || claimed.attemptCount != cmd.attemptCount + 1) return@ownerTransaction
            val transaction = movementDao.getTransactionById(cmd.userId, transactionId)
            val creditDao = database.creditDao()
            database.financialMovementDao().updateOperationStatus(cmd.userId, cmd.operationId, "REJECTED")
            val paymentAllocations = if (cmd.commandType in setOf("PAY_CREDIT_CARD", "ALLOCATE_CREDIT_PAYMENT")) {
                creditDao.getAllocationsForPayment(cmd.userId, transactionId)
            } else emptyList()

            if (transaction != null && transaction.status !in setOf("VOIDED", "FAILED")) {
                val beforeRevisionId = transaction.currentRevisionId
                    ?.takeIf { movementDao.getRevision(cmd.userId, it) != null }
                    ?: UUID.randomUUID().toString().also { baselineId ->
                        movementDao.insertRevision(
                            TransactionRevisionEntity(
                                userId = cmd.userId,
                                revisionId = baselineId,
                                transactionId = transaction.id,
                                commandId = null,
                                commandType = "MIGRATION_BASELINE",
                                baseRevision = (transaction.revision - 1L).coerceAtLeast(0L),
                                localRevision = transaction.revision,
                                previousPayload = null,
                                newPayload = MovementRevisionSnapshotCodec.encode(transaction),
                                changeReason = null,
                                provenance = "LOCAL_BASELINE",
                                createdAt = nowMillis,
                            ),
                        )
                        movementDao.getRevision(cmd.userId, baselineId)
                    }
                    ?: error("Rejected movement baseline was not stored")

                val nextRevision = Math.addExact(transaction.revision, 1L)
                val voidRevisionId = UUID.randomUUID().toString()
                val voided = transaction.copy(
                    status = "VOIDED",
                    syncStatus = MovementSyncStatus.FAILED_PERMANENT.name,
                    revision = nextRevision,
                    currentRevisionId = voidRevisionId,
                    source = "LOCAL_REJECTION",
                    updatedAt = nowMillis,
                )
                movementDao.insertRevision(
                    TransactionRevisionEntity(
                        userId = cmd.userId,
                        revisionId = voidRevisionId,
                        transactionId = transaction.id,
                        commandId = cmd.operationId,
                        commandType = "LOCAL_REJECTION_VOID",
                        baseRevision = transaction.revision,
                        localRevision = nextRevision,
                        previousPayload = MovementRevisionSnapshotCodec.encode(transaction),
                        newPayload = MovementRevisionSnapshotCodec.encode(voided),
                        changeReason = errorCode,
                        provenance = "LOCAL_PERMANENT_REJECTION",
                        createdAt = nowMillis,
                    ),
                )

                val priorEffects = movementDao.getLedgerEntriesForTransaction(cmd.userId, transactionId)
                    .filter { it.role != "REVERSAL" }
                val reversals = mutableListOf<com.kipu.app.feature.movements.data.local.LedgerEntryEntity>()
                val reversalMappings = mutableListOf<Pair<MovementLedgerEffectEntity, com.kipu.app.feature.movements.data.local.LedgerEntryEntity>>()
                priorEffects.forEachIndexed { ordinal, original ->
                    val knownAlias = movementDao.getLedgerAlias(cmd.userId, original.id)
                    val originalIdentity = if (knownAlias != null) {
                        movementDao.getLedgerEffect(cmd.userId, knownAlias.commandId, knownAlias.effectOrdinal)
                            ?: error("Ledger alias has no immutable effect identity")
                    } else {
                        val baselineCommandId = UUID.nameUUIDFromBytes(
                            "kipu-ledger-baseline:${cmd.userId}:${original.id}".toByteArray(StandardCharsets.UTF_8),
                        ).toString()
                        val baselineEffect = MovementLedgerEffectEntity(
                            userId = cmd.userId,
                            commandId = baselineCommandId,
                            effectOrdinal = 0,
                            transactionId = transaction.id,
                            revisionId = beforeRevisionId,
                            ledgerEntryId = original.id,
                            reversesCommandId = null,
                            reversesEffectOrdinal = null,
                        )
                        movementDao.insertLedgerEffect(baselineEffect)
                        movementDao.insertLedgerAlias(
                            MovementLedgerAliasEntity(cmd.userId, original.id, baselineCommandId, 0),
                        )
                        baselineEffect
                    }
                    val reversal = com.kipu.app.feature.movements.data.local.LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        userId = cmd.userId,
                        transactionId = transaction.id,
                        accountId = original.accountId,
                        role = "REVERSAL",
                        signedAmountMinor = Math.negateExact(original.signedAmountMinor),
                        currencyCode = original.currencyCode,
                        createdAt = nowMillis,
                    )
                    reversals += reversal
                    reversalMappings += originalIdentity to reversal
                }
                if (reversals.isNotEmpty()) movementDao.insertLedgerEntries(reversals)
                reversalMappings.forEachIndexed { ordinal, (originalIdentity, reversal) ->
                    movementDao.insertLedgerEffect(
                        MovementLedgerEffectEntity(
                            userId = cmd.userId,
                            commandId = cmd.operationId,
                            effectOrdinal = ordinal,
                            transactionId = transaction.id,
                            revisionId = voidRevisionId,
                            ledgerEntryId = reversal.id,
                            reversesCommandId = originalIdentity.commandId,
                            reversesEffectOrdinal = originalIdentity.effectOrdinal,
                        ),
                    )
                    movementDao.insertLedgerAlias(MovementLedgerAliasEntity(cmd.userId, reversal.id, cmd.operationId, ordinal))
                }
                check(movementDao.markTransactionVoidedAfterRejection(
                    cmd.userId, transactionId, nextRevision, voidRevisionId, nowMillis,
                ) == 1) { "Movement rejection must void exactly one transaction" }

                if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE") {
                    creditDao.getInstallmentsForTransaction(cmd.userId, transactionId).forEach { installment ->
                        if (installment.status != "VOIDED") {
                            creditDao.updateInstallmentStatus(
                                userId = cmd.userId,
                                installmentId = installment.id,
                                status = "VOIDED",
                                revision = installment.revision + 1L,
                                updatedAt = nowMillis,
                            )
                        }
                    }
                }
                if (cmd.commandType in setOf("PAY_CREDIT_CARD", "ALLOCATE_CREDIT_PAYMENT")) {
                    paymentAllocations.map { it.installmentId }.distinct().forEach { installmentId ->
                        val installment = creditDao.getInstallmentById(cmd.userId, installmentId) ?: return@forEach
                        if (installment.status == "VOIDED") return@forEach
                        val allocated = creditDao.getAllocatedMinorForInstallment(cmd.userId, installmentId)
                        val status = when {
                            allocated == 0L -> "PENDING"
                            // Estimated interest is informational (RF-C12): principal alone settles the installment.
                            allocated >= installment.principalMinor -> "PAID"
                            else -> "PARTIAL"
                        }
                        if (status != installment.status) {
                            creditDao.updateInstallmentStatus(
                                userId = cmd.userId,
                                installmentId = installmentId,
                                status = status,
                                revision = installment.revision + 1L,
                                updatedAt = nowMillis,
                            )
                        }
                    }
                }

                creditDao.refreshInstallmentStatuses(cmd.userId)

                (priorEffects + reversals).groupBy { it.accountId }.forEach { (accountId, entries) ->
                    database.financialMovementDao().rebuildAccountProjection(cmd.userId, accountId, nowMillis)
                    movementDao.upsertBalanceProjection(
                        com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                            userId = cmd.userId,
                            accountId = accountId,
                            balanceMinor = movementDao.calculateLedgerSumForAccount(cmd.userId, accountId) ?: 0L,
                            currencyCode = entries.first().currencyCode,
                            lastTransactionAt = nowMillis,
                            updatedAt = nowMillis,
                        ),
                    )
                }

                movementDao.markReceiptsRejectedForTransaction(cmd.userId, transactionId, nowMillis)
                val receiptHash = MessageDigest.getInstance("SHA-256")
                    .digest("LOCAL_REJECTION_VOID\n${cmd.operationId}\n$transactionId\n$errorCode".toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                val rejectionReceiptId = UUID.nameUUIDFromBytes(
                    "kipu-local-rejection-receipt:${cmd.userId}:${cmd.operationId}".toByteArray(StandardCharsets.UTF_8),
                ).toString()
                movementDao.insertOrUpdateReceipt(
                    LocalCommandReceiptEntity(
                        userId = cmd.userId,
                        idempotencyKey = rejectionReceiptId,
                        requestHash = receiptHash,
                        transactionId = transactionId,
                        status = "VOIDED",
                        responsePayload = "{\"status\":\"APPLIED\",\"result\":\"VOIDED\",\"result_code\":\"SERVER_REJECTED\",\"revision\":$nextRevision}",
                        contractVersion = 1,
                        commandType = "LOCAL_REJECTION_VOID",
                        expectedRevision = transaction.revision,
                        resultingRevision = nextRevision,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    ),
                )
            }
            ownerTransaction(cmd.userId) { syncDao.updateState(
                userId = cmd.userId,
                operationId = cmd.operationId,
                newState = "FAILED_PERMANENT",
                nextAttemptAt = null,
                errorCode = errorCode,
                nowMicros = nowMillis * 1_000L,
            ) }
        }
    }
}
