package com.kipu.app.feature.settings.data

import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.sync.OutboxPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import io.github.jan.supabase.exceptions.RestException
import com.kipu.app.feature.settings.data.local.ProfilePreferenceOutboxEntity
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.feature.settings.data.local.UserProfileCacheEntity
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.SyncState
import com.kipu.app.feature.settings.domain.model.ThemeMode
import com.kipu.app.feature.settings.domain.model.UserProfile
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.network.remoteCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class UpdateProfileRpcParams(
    @SerialName("p_operation_id") val operationId: String,
    @SerialName("p_expected_revision") val expectedRevision: Long,
    @SerialName("p_payload") val payload: JsonObject,
)

@Serializable
data class UpdateProfileRpcResult(
    val result: String,
    @SerialName("accepted_revision") val acceptedRevision: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("current_revision") val currentRevision: Long? = null,
)

@Serializable
data class RemoteProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("currency_code") val currencyCode: String = "PEN",
    @SerialName("time_zone") val timeZone: String? = null,
    @SerialName("month_start") val monthStart: Int = 1,
    @SerialName("hide_balances") val hideBalances: Boolean = false,
    @SerialName("theme_mode") val themeMode: String = "LIGHT",
    val revision: Long = 1L,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Singleton
class OfflineFirstProfilePreferencesRepository @Inject constructor(
    private val profileDao: ProfilePreferencesDao,
    private val supabaseClient: SupabaseClient,
    private val sessionProvider: AuthenticatedSessionProvider,
    private val syncScheduler: com.kipu.app.feature.settings.data.sync.ProfileSyncScheduler? = null,
) : ProfilePreferencesRepository {

    override suspend fun retryFailed(userId: UUID, commandId: UUID): Boolean = recoverCommand(userId,commandId,false)
    override suspend fun discardLocal(userId: UUID, commandId: UUID): Boolean = recoverCommand(userId,commandId,true)
    private suspend fun recoverCommand(userId: UUID, id: UUID, discard: Boolean): Boolean {
        if(sessionProvider.currentSession()?.userId!=userId) return false
        val item=profileDao.findOutbox(id)?.takeIf { it.userId==userId } ?: return false
        if(!discard && !OutboxPolicy.canRetry(item.status)) return false
        if(discard && item.status !in setOf("CONFLICT","FAILED_PERMANENT","ERROR","WAITING_FOR_AUTH","BLOCKED")) return false
        profileDao.updateOutbox(item.copy(status=if(discard) "DISCARDED" else "PENDING",nextAttemptAt=null,updatedAt=Instant.now()))
        if(discard) refreshProfile(userId)
        syncScheduler?.scheduleSync(userId)
        return true
    }


    override fun observeProfile(userId: UUID): Flow<UserProfile?> {
        return profileDao.observeProfile(userId).map { it?.toDomain() }
    }

    override suspend fun getProfile(userId: UUID): UserProfile? {
        return profileDao.findProfile(userId)?.toDomain()
    }

    override suspend fun updatePreferences(userId: UUID, delta: ProfilePreferenceDelta): Result<UserProfile> {
        return remoteCall(onFailure = { Result.failure(it) }) {
            delta.monthStart?.let {
                require(it in 1..28) { "monthStart must be between 1 and 28, got $it" }
            }
            delta.currencyCode?.let {
                require(it.length == 3) { "currencyCode must be a 3-letter ISO code" }
            }

            val current = profileDao.findProfile(userId) ?: UserProfileCacheEntity(
                userId = userId,
                displayName = "",
                currencyCode = "PEN",
                timeZone = null,
                monthStart = 1,
                hideBalances = false,
                themeMode = "LIGHT",
                remoteRevision = 1L,
                syncState = SyncState.SYNCED.name,
                updatedLocallyAt = Instant.now(),
            )

            val updated = current.copy(
                displayName = delta.displayName ?: current.displayName,
                currencyCode = delta.currencyCode ?: current.currencyCode,
                timeZone = delta.timeZone ?: current.timeZone,
                monthStart = delta.monthStart ?: current.monthStart,
                hideBalances = delta.hideBalances ?: current.hideBalances,
                themeMode = delta.themeMode?.name ?: current.themeMode,
                syncState = SyncState.PENDING.name,
                updatedLocallyAt = Instant.now(),
            )

            val payloadJson = buildJsonObject {
                delta.displayName?.let { put("display_name", it) }
                delta.currencyCode?.let { put("currency_code", it) }
                delta.timeZone?.let { put("time_zone", it) }
                delta.monthStart?.let { put("month_start", it) }
                delta.hideBalances?.let { put("hide_balances", it) }
                delta.themeMode?.let { put("theme_mode", it.name) }
            }

            val outbox = ProfilePreferenceOutboxEntity(
                operationId = UUID.randomUUID(),
                userId = userId,
                expectedRevision = current.remoteRevision,
                payload = payloadJson.toString(),
                status = "PENDING",
                attemptCount = 0,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

            profileDao.savePreferencesWithOutbox(updated, outbox)
            Result.success(updated.toDomain())
        }
    }

    override suspend fun setHideBalances(userId: UUID, hideBalances: Boolean): Result<UserProfile> {
        return updatePreferences(userId, ProfilePreferenceDelta(hideBalances = hideBalances))
    }

    override suspend fun syncPendingPreferences(userId: UUID): Result<Unit> {
        if(!importSessionFor(userId)) {
            profileDao.markOutboxWaitingForAuth(userId)
            return Result.failure(IllegalStateException("Active session does not match the outbox owner"))
        }
        // A previous conflict may have survived process death before its refresh.
        if(profileDao.findUnresolvedOutbox(userId).any { it.status=="CONFLICT" }) {
            val refreshed=refreshProfile(userId)
            if(refreshed.isFailure) return Result.failure(refreshed.exceptionOrNull()!!)
        }
        var transientFailure: Throwable? = null
        val attempted=mutableSetOf<UUID>()
        while(true) {
            currentCoroutineContext().ensureActive()
            val item=profileDao.claim(userId,Instant.now()) ?: break
            if(!attempted.add(item.operationId)) break
            if(!importSessionFor(userId)) {
                profileDao.markOutboxWaitingForAuth(userId)
                return Result.failure(IllegalStateException("Active session does not match the outbox owner"))
            }
            var invalidPayload=false
            try {
                val payloadObj=try { Json.parseToJsonElement(item.payload) as JsonObject }
                    catch(e: Exception) { invalidPayload=true; throw e }
                val result=supabaseClient.postgrest.rpc("update_profile_preferences",UpdateProfileRpcParams(
                    item.operationId.toString(),item.expectedRevision,payloadObj)).decodeAs<UpdateProfileRpcResult>()
                currentCoroutineContext().ensureActive()
                val stored=profileDao.findOutbox(item.operationId)
                if(stored?.status!="IN_FLIGHT" || stored.updatedAt!=item.updatedAt) continue
                when(result.result) {

                    "APPLIED","DUPLICATE" -> {
                        profileDao.findProfile(userId)?.let { current -> profileDao.putProfile(current.copy(
                            remoteRevision=result.acceptedRevision ?: item.expectedRevision+1,
                            syncState=if(profileDao.pendingOutboxCount(userId)>1) SyncState.PENDING.name else SyncState.SYNCED.name,
                            remoteUpdatedAt=result.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now())) }
                        profileDao.deleteOutbox(item.operationId)
                    }
                    "CONFLICT" -> {
                        profileDao.updateOutbox(item.copy(status="CONFLICT",updatedAt=Instant.now()))
                        val refreshed=refreshProfile(userId)
                        if(refreshed.isFailure) return Result.failure(refreshed.exceptionOrNull()!!)
                        // A fresh id and revision retain the latest local values (last writer wins).
                        return Result.failure(IllegalStateException("Preferences refreshed; retry latest values"))
                    }
                    else -> {
                        val failure=OutboxPolicy.classify(status=result.result)
                        profileDao.updateOutbox(item.copy(status=if(failure==OutboxPolicy.Failure.PERMANENT) "FAILED_PERMANENT" else "ERROR",
                            attemptCount=item.attemptCount+1,nextAttemptAt=Instant.now().plusSeconds(30),updatedAt=Instant.now()))
                        transientFailure=IllegalStateException("Unexpected preference response")
                        break
                    }
                }
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) {
                val failure=OutboxPolicy.classify(httpStatus=(e as? RestException)?.statusCode,invalidPayload=invalidPayload)
                val state=when(failure) {
                    OutboxPolicy.Failure.RETRYABLE -> "ERROR"
                    OutboxPolicy.Failure.WAITING_FOR_AUTH -> "WAITING_FOR_AUTH"
                    OutboxPolicy.Failure.CONFLICT -> "CONFLICT"
                    OutboxPolicy.Failure.PERMANENT -> "FAILED_PERMANENT"
                }
                profileDao.updateOutbox(item.copy(status=state,attemptCount=item.attemptCount+1,
                    nextAttemptAt=if(failure==OutboxPolicy.Failure.RETRYABLE) Instant.now().plusSeconds(30) else null,updatedAt=Instant.now()))
                if(failure in setOf(OutboxPolicy.Failure.RETRYABLE,OutboxPolicy.Failure.WAITING_FOR_AUTH,OutboxPolicy.Failure.CONFLICT)) transientFailure=e
                break
            }
        }
        return transientFailure?.let { Result.failure(it) }
            ?: if(profileDao.pendingOutboxCount(userId)>0) Result.failure(IllegalStateException("Preferences waiting for retry"))
            else Result.success(Unit)
    }

    /** Imports the token of the active session only when it belongs to [userId]. */
    private suspend fun importSessionFor(userId: UUID): Boolean {
        val session: AuthenticatedSession = sessionProvider.currentSession() ?: return false
        if (session.userId != userId) return false
        return remoteCall(onFailure = { false }) {
            supabaseClient.auth.importAuthToken(session.accessToken)
            true
        }
    }

    override suspend fun refreshProfile(userId: UUID): Result<UserProfile> {
        return remoteCall(onFailure = {
            SecureLog.e("ProfilePreferencesRepo", "Error refreshing profile via ensure_profile", it)
            Result.failure(it)
        }) {
            check(importSessionFor(userId)) { "Active session does not match the profile owner" }
            val remote = supabaseClient.postgrest.rpc("ensure_profile").decodeAs<RemoteProfileDto>()
            check(UUID.fromString(remote.userId) == userId) { "Remote profile owner mismatch" }
            val entity = remote.toCacheEntity()
            Result.success(profileDao.hydrateProfile(entity).toDomain())
        }
    }
}

internal fun RemoteProfileDto.toCacheEntity(): UserProfileCacheEntity = UserProfileCacheEntity(
    userId = UUID.fromString(userId),
    displayName = displayName.orEmpty(),
    currencyCode = currencyCode,
    timeZone = timeZone,
    monthStart = monthStart,
    hideBalances = hideBalances,
    themeMode = themeMode,
    remoteRevision = revision,
    remoteUpdatedAt = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
    syncState = SyncState.SYNCED.name,
    updatedLocallyAt = Instant.now(),
)

private fun UserProfileCacheEntity.toDomain(): UserProfile {
    return UserProfile(
        userId = userId,
        displayName = displayName,
        currencyCode = currencyCode,
        timeZone = timeZone,
        monthStart = monthStart,
        hideBalances = hideBalances,
        themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.LIGHT),
        remoteRevision = remoteRevision,
        remoteUpdatedAt = remoteUpdatedAt,
        syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.SYNCED),
    )
}
