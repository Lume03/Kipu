package com.kipu.app.feature.plans.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import com.kipu.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FeatureAccessCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVerified(entity: FeatureAccessCacheEntity)

    /** Commercial billing refreshes must not erase an explicit QA override for this owner. */
    @Transaction
    suspend fun putCommercialVerified(
        entity: FeatureAccessCacheEntity,
        preserveTestOverrides: Boolean = BuildConfig.TEST_PLAN_OVERRIDES_ENABLED,
    ): Boolean {
        val current = get(entity.userId)
        if (preserveTestOverrides && (current?.source == "SIGNED_TEST_GRANT" || current?.source == "TEST_OVERRIDE")) return false
        putVerified(entity)
        return true
    }

    @Query("SELECT * FROM feature_access_cache WHERE user_id = :userId")
    suspend fun get(userId: UUID): FeatureAccessCacheEntity?

    @Query("SELECT * FROM feature_access_cache WHERE user_id = :userId")
    fun observe(userId: UUID): Flow<FeatureAccessCacheEntity?>
}
