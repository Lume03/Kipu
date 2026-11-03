package com.kipu.app.feature.settings.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Dao
abstract class ProfilePreferencesDao {

    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    abstract suspend fun findProfile(userId: UUID): UserProfileCacheEntity?

    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    abstract fun observeProfile(userId: UUID): Flow<UserProfileCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putProfile(profile: UserProfileCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOutbox(outbox: ProfilePreferenceOutboxEntity)

    @Update
    abstract suspend fun updateOutbox(outbox: ProfilePreferenceOutboxEntity)

    @Query("SELECT * FROM profile_preference_outbox WHERE operation_id = :operationId")
    abstract suspend fun findOutbox(operationId: UUID): ProfilePreferenceOutboxEntity?

    @Query("SELECT COUNT(*) FROM profile_preference_outbox WHERE user_id = :userId AND status IN ('PENDING', 'IN_FLIGHT', 'WAITING_FOR_AUTH')")
    abstract suspend fun pendingOutboxCount(userId: UUID): Int

    @Query("SELECT * FROM profile_preference_outbox WHERE user_id = :userId AND status IN ('PENDING', 'IN_FLIGHT', 'WAITING_FOR_AUTH') ORDER BY created_at ASC")
    abstract suspend fun findPendingOutbox(userId: UUID): List<ProfilePreferenceOutboxEntity>

    @Query("SELECT * FROM profile_preference_outbox WHERE status = 'PENDING' ORDER BY created_at ASC")
    abstract suspend fun findAllPendingOutbox(): List<ProfilePreferenceOutboxEntity>

    @Query("SELECT * FROM profile_preference_outbox WHERE user_id = :userId AND status = 'PENDING' ORDER BY created_at ASC LIMIT 1")
    abstract suspend fun findOldestPendingOutbox(userId: UUID): ProfilePreferenceOutboxEntity?

    @Query("DELETE FROM profile_preference_outbox WHERE operation_id = :operationId")
    abstract suspend fun deleteOutbox(operationId: UUID)

    @Query("UPDATE profile_preference_outbox SET status = 'WAITING_FOR_AUTH' WHERE user_id = :userId AND status IN ('PENDING', 'IN_FLIGHT')")
    abstract suspend fun markOutboxWaitingForAuth(userId: UUID)

    @Query("SELECT * FROM profile_preference_outbox WHERE user_id = :userId AND status NOT IN ('APPLIED','DUPLICATE','COMPLETED','SYNCED','SUCCEEDED') ORDER BY created_at ASC")
    abstract suspend fun findUnresolvedOutbox(userId: UUID): List<ProfilePreferenceOutboxEntity>

    /** Fetching remote data is independent of uploading local edits. Keep their revision and payload. */
    @Transaction
    open suspend fun hydrateProfile(remote: UserProfileCacheEntity): UserProfileCacheEntity {
        val unresolved = findUnresolvedOutbox(remote.userId)
        val local = findProfile(remote.userId)
        val merged = if (unresolved.isEmpty()) remote else {
            checkNotNull(local) { "Pending preferences have no local profile" }
            val keys = unresolved.flatMap { (Json.parseToJsonElement(it.payload) as JsonObject).keys }.toSet()
            remote.copy(
                displayName = if ("display_name" in keys) local.displayName else remote.displayName,
                currencyCode = if ("currency_code" in keys) local.currencyCode else remote.currencyCode,
                timeZone = if ("time_zone" in keys) local.timeZone else remote.timeZone,
                monthStart = if ("month_start" in keys) local.monthStart else remote.monthStart,
                hideBalances = if ("hide_balances" in keys) local.hideBalances else remote.hideBalances,
                themeMode = if ("theme_mode" in keys) local.themeMode else remote.themeMode,
                remoteRevision = local.remoteRevision,
                syncState = local.syncState,
                updatedLocallyAt = local.updatedLocallyAt,
            )
        }
        putProfile(merged)
        return merged
    }

    @Transaction
    open suspend fun savePreferencesWithOutbox(
        profile: UserProfileCacheEntity,
        outbox: ProfilePreferenceOutboxEntity,
    ) {
        putProfile(profile)
        // Offline edits share one expectedRevision, so a second row would end up as a permanent CONFLICT:
        // merge into the still-pending row instead (last value wins per field).
        val pending = findOldestPendingOutbox(outbox.userId)
        if (pending == null) {
            insertOutbox(outbox)
            return
        }
        val merged = JsonObject(
            (Json.parseToJsonElement(pending.payload) as JsonObject) +
                (Json.parseToJsonElement(outbox.payload) as JsonObject),
        )
        updateOutbox(pending.copy(payload = merged.toString(), updatedAt = Instant.now()))
    }
}
