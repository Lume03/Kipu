package com.kipu.app.feature.categories.data.sync

import android.content.Context
import com.kipu.app.core.sync.OutboxPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryConflictEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import com.kipu.app.feature.categories.data.remote.CategoryApiResponse
import com.kipu.app.feature.categories.data.remote.CategoryCommandResponseDto
import com.kipu.app.feature.categories.data.remote.CreateCategoryRequestDto
import com.kipu.app.feature.categories.data.remote.DeleteMerchantAliasRuleRequestDto
import com.kipu.app.feature.categories.data.remote.DeleteMerchantCategoryPreferenceRequestDto
import com.kipu.app.feature.categories.data.remote.ResolveCategoryConflictRequestDto
import com.kipu.app.feature.categories.data.remote.SetCategoryActiveRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateCategoryPresentationRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateMovementClassificationRequestDto
import com.kipu.app.feature.categories.data.remote.PreserveMerchantSourceTextRequestDto
import com.kipu.app.feature.categories.data.remote.UpsertMerchantAliasRuleRequestDto
import com.kipu.app.feature.categories.data.remote.UpsertMerchantCategoryPreferenceRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json


@HiltWorker
class SyncCategoryCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
    private val merchantRulesDao: MerchantRulesDao,
    private val api: CategoriesApi,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    private sealed interface CommandOutcome {
        data object Applied : CommandOutcome
        data object Retry : CommandOutcome
        data class Conflict(val code: String, val state: String = "CONFLICT") : CommandOutcome
    }

    companion object {
        const val KEY_USER_ID = "key_user_id"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun failureOutcome(failure: OutboxPolicy.Failure, code: String): CommandOutcome = when(failure) {
        OutboxPolicy.Failure.RETRYABLE -> CommandOutcome.Retry
        OutboxPolicy.Failure.WAITING_FOR_AUTH -> CommandOutcome.Conflict(code, "WAITING_FOR_AUTH")
        OutboxPolicy.Failure.CONFLICT -> CommandOutcome.Conflict(code)
        OutboxPolicy.Failure.PERMANENT -> CommandOutcome.Conflict(code, "FAILED_PERMANENT")
    }


    private fun parseRemoteInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val access = sessionCoordinator.localAccess.value as? LocalAccess.Available
        if (access?.userId != userId) {
            SecureLog.w("SyncCategoryCommandsWorker", "Skipping category sync for an inactive owner")
            return Result.success()
        }

        val remoteSession=sessionCoordinator.remoteSession.value
        if(remoteSession !is com.kipu.app.core.session.RemoteSession.Valid || remoteSession.userId!=userId || !remoteSession.expiresAt.isAfter(java.time.Instant.now())) {
            categoryDao.waitForAuth(userId)
            return Result.success()
        }

        // Fill missing server categories without overwriting pending local edits.
        val hydration = CategoryHydration(categoryDao, merchantDao, applicationContext, api)
        var needsRetry = false
        try {
            hydration.hydrateCategories(userId)
            hydration.hydrateMerchantCatalog()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { needsRetry = true }

        categoryDao.resumeAuth(userId)
        categoryDao.blockBehindFailures(userId)
        while (!isStopped) {
            currentCoroutineContext().ensureActive()
            val cmd = categoryDao.claim(userId, System.currentTimeMillis()) ?: break
            val outcome = processCommand(cmd)
            currentCoroutineContext().ensureActive()
            if((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId!=userId) return Result.retry()
            val now = System.currentTimeMillis()
            when (outcome) {
                CommandOutcome.Applied -> categoryDao.complete(cmd,"COMPLETED",null,null,now)
                is CommandOutcome.Conflict -> {
                    categoryDao.complete(cmd,outcome.state,outcome.code,null,now)
                }
                CommandOutcome.Retry -> {
                    needsRetry = true
                    categoryDao.complete(cmd,"ERROR","REMOTE_UNAVAILABLE",now+30_000,now)
                }
            }
        }

        return if (needsRetry || categoryDao.hasOutstanding(userId)) Result.retry() else Result.success()
    }

    private suspend fun processCommand(cmd: com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity): CommandOutcome {
        return try {
            when (cmd.commandType) {
                "CREATE_CATEGORY" -> {
                    val dto = json.decodeFromString<CreateCategoryRequestDto>(cmd.payloadJson)
                    api.createCategory(dto).asCommandOutcome()
                }
                "UPDATE_PRESENTATION" -> {
                    val dto = json.decodeFromString<UpdateCategoryPresentationRequestDto>(cmd.payloadJson)
                    when (val res = api.updatePresentation(dto)) {
                        is CategoryApiResponse.Success -> {
                            if (res.data.status == "CONFLICT") {
                                // Conflict record was generated remotely; ensure local conflict state
                                categoryDao.insertConflict(
                                    CategoryConflictEntity(
                                        id = java.util.UUID.randomUUID().toString(),
                                        categoryId = dto.categoryId,
                                        userId = cmd.userId,
                                        conflictType = "PRESENTATION",
                                        localVersion = cmd.payloadJson,
                                        remoteVersion = "CONFLICT_DETECTED",
                                        status = "OPEN",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                CommandOutcome.Conflict("REVISION_CONFLICT")
                            } else if (!res.data.success) {
                                CommandOutcome.Retry
                            } else {
                                CommandOutcome.Applied
                            }
                        }
                        is CategoryApiResponse.Error -> failureOutcome(OutboxPolicy.classify(httpStatus=res.statusCode), "HTTP_${res.statusCode}")
                        is CategoryApiResponse.NetworkFailure -> CommandOutcome.Retry
                    }
                }
                "SET_CATEGORY_ACTIVE" -> {
                    val dto = json.decodeFromString<SetCategoryActiveRequestDto>(cmd.payloadJson)
                    api.setCategoryActive(dto).asCommandOutcome()
                }
                "UPDATE_MOVEMENT_CLASSIFICATION" -> {
                    val dto = json.decodeFromString<UpdateMovementClassificationRequestDto>(cmd.payloadJson)
                    api.updateMovementClassification(dto).asCommandOutcome()
                }
                "PRESERVE_MERCHANT_SOURCE_TEXT" -> {
                    val dto = json.decodeFromString<PreserveMerchantSourceTextRequestDto>(cmd.payloadJson)
                    api.preserveMerchantSourceText(dto).asCommandOutcome()
                }
                "UPSERT_MERCHANT_ALIAS_RULE" -> {
                    val dto = json.decodeFromString<UpsertMerchantAliasRuleRequestDto>(cmd.payloadJson)
                    val outcome = api.upsertMerchantAliasRule(dto).asCommandOutcome()
                    updateAliasSyncState(cmd.userId, dto.ruleId, dto.expectedRevision, outcome)
                    outcome
                }
                "DELETE_MERCHANT_ALIAS_RULE" -> {
                    val dto = json.decodeFromString<DeleteMerchantAliasRuleRequestDto>(cmd.payloadJson)
                    val outcome = api.deleteMerchantAliasRule(dto).asCommandOutcome()
                    updateAliasSyncState(cmd.userId, dto.ruleId, dto.expectedRevision, outcome)
                    outcome
                }
                "UPSERT_MERCHANT_CATEGORY_PREFERENCE" -> {
                    val dto = json.decodeFromString<UpsertMerchantCategoryPreferenceRequestDto>(cmd.payloadJson)
                    val outcome = api.upsertMerchantCategoryPreference(dto).asCommandOutcome()
                    updatePreferenceSyncState(cmd.userId, dto.merchantId, dto.expectedRevision, outcome)
                    outcome
                }
                "DELETE_MERCHANT_CATEGORY_PREFERENCE" -> {
                    val dto = json.decodeFromString<DeleteMerchantCategoryPreferenceRequestDto>(cmd.payloadJson)
                    val merchantId = merchantRulesDao.getPreference(cmd.userId, cmd.aggregateId)?.merchantId ?: cmd.aggregateId
                    val outcome = api.deleteMerchantCategoryPreference(dto).asCommandOutcome()
                    updatePreferenceSyncState(cmd.userId, merchantId, dto.expectedRevision, outcome)
                    outcome
                }
                "RESOLVE_CONFLICT" -> {
                    val dto = json.decodeFromString<ResolveCategoryConflictRequestDto>(cmd.payloadJson)
                    api.resolveConflict(dto).asCommandOutcome()
                }
                else -> failureOutcome(OutboxPolicy.classify(invalidPayload=true), "INVALID_COMMAND")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.serialization.SerializationException) {
            failureOutcome(OutboxPolicy.classify(invalidPayload=true), "INVALID_PAYLOAD")
        } catch (e: Exception) {
            if (cmd.commandType in setOf("PRESERVE_MERCHANT_SOURCE_TEXT", "UPSERT_MERCHANT_ALIAS_RULE", "DELETE_MERCHANT_ALIAS_RULE", "UPSERT_MERCHANT_CATEGORY_PREFERENCE", "DELETE_MERCHANT_CATEGORY_PREFERENCE")) {
                SecureLog.e("SyncCategoryCommandsWorker", "Failed to sync a private merchant command")
            } else {
                SecureLog.e("SyncCategoryCommandsWorker", "Error processing command ${cmd.commandType}", e)
            }
            CommandOutcome.Retry
        }
    }

    private fun CategoryApiResponse<CategoryCommandResponseDto>.asCommandOutcome(): CommandOutcome = when (this) {
        is CategoryApiResponse.Success -> when {
            data.status == "CONFLICT" || data.status == "REJECTED" -> failureOutcome(OutboxPolicy.classify(status=data.status), data.code ?: data.status)
            data.success -> CommandOutcome.Applied
            else -> CommandOutcome.Retry
        }
        is CategoryApiResponse.Error -> failureOutcome(OutboxPolicy.classify(httpStatus=statusCode), "HTTP_$statusCode")
        is CategoryApiResponse.NetworkFailure -> CommandOutcome.Retry
    }

    private suspend fun updateAliasSyncState(
        userId: String,
        ruleId: String,
        expectedRevision: Long?,
        outcome: CommandOutcome,
    ) {
        val state = when (outcome) {
            CommandOutcome.Applied -> "SYNCED"
            is CommandOutcome.Conflict -> when(outcome.state) { "FAILED_PERMANENT" -> "FAILED"; "WAITING_FOR_AUTH" -> "PENDING"; else -> "CONFLICT" }
            CommandOutcome.Retry -> "PENDING"
        }
        merchantRulesDao.setAliasSyncState(
            userId = userId,
            ruleId = ruleId,
            state = state,
            revision = if (outcome == CommandOutcome.Applied) expectedRevision?.plus(1L) ?: 1L else null,
            error = (outcome as? CommandOutcome.Conflict)?.code,
        )
    }

    private suspend fun updatePreferenceSyncState(
        userId: String,
        merchantId: String,
        expectedRevision: Long?,
        outcome: CommandOutcome,
    ) {
        val state = when (outcome) {
            CommandOutcome.Applied -> "SYNCED"
            is CommandOutcome.Conflict -> when(outcome.state) { "FAILED_PERMANENT" -> "FAILED"; "WAITING_FOR_AUTH" -> "PENDING"; else -> "CONFLICT" }
            CommandOutcome.Retry -> "PENDING"
        }
        merchantRulesDao.setPreferenceSyncState(
            userId = userId,
            merchantId = merchantId,
            state = state,
            revision = if (outcome == CommandOutcome.Applied) expectedRevision?.plus(1L) ?: 1L else null,
            error = (outcome as? CommandOutcome.Conflict)?.code,
        )
    }

}
