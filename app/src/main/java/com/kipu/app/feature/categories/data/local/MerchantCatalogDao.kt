package com.kipu.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class MerchantCategoryFilterRow(
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "name")
    val name: String,
)

@Dao
interface MerchantCatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchants(merchants: List<MerchantCatalogEntity>)

    @Query("""
        SELECT * FROM merchant_catalog_cache
        WHERE (user_id IS NULL OR user_id = :userId) AND is_active = 1 AND normalized_name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 50
    """)
    fun searchMerchants(query: String, userId: String? = null): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE (user_id IS NULL OR user_id = :userId) AND is_active = 1 ORDER BY name ASC")
    fun getAllActiveMerchants(userId: String? = null): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE (user_id IS NULL OR user_id = :userId) AND is_active = 1 AND default_category_id = :categoryId ORDER BY name ASC")
    fun getActiveMerchantsByCategory(categoryId: String, userId: String? = null): Flow<List<MerchantCatalogEntity>>

    @Query("""
        SELECT * FROM merchant_catalog_cache
        WHERE (user_id IS NULL OR user_id = :userId) AND is_active = 1
          AND default_category_id = :categoryId
          AND normalized_name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 50
    """)
    fun searchMerchantsByCategory(query: String, categoryId: String, userId: String? = null): Flow<List<MerchantCatalogEntity>>

    @Query("""
        SELECT DISTINCT presentation.category_id AS category_id, presentation.name AS name
        FROM merchant_catalog_cache AS merchant
        JOIN categories AS subcategory
          ON subcategory.id = merchant.default_category_id AND subcategory.is_active = 1
        JOIN categories AS root
          ON root.id = subcategory.parent_id AND root.is_active = 1
        JOIN category_presentations AS presentation
          ON presentation.category_id = subcategory.id AND presentation.user_id = :userId
        WHERE (merchant.user_id IS NULL OR merchant.user_id = :userId) AND merchant.is_active = 1 AND merchant.default_category_id IS NOT NULL
        ORDER BY presentation.name COLLATE NOCASE ASC
    """)
    fun observeMerchantCategoryFilters(userId: String): Flow<List<MerchantCategoryFilterRow>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE user_id IS NULL OR user_id = :userId")
    fun observeMerchants(userId: String? = null): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE id = :id AND (user_id IS NULL OR user_id = :userId)")
    suspend fun getMerchantById(id: String, userId: String? = null): MerchantCatalogEntity?

    @Query("SELECT COUNT(*) FROM merchant_catalog_cache WHERE (user_id IS NULL OR user_id = :userId) AND is_active = 1")
    suspend fun countActiveMerchants(userId: String? = null): Int

    @Query("SELECT * FROM merchant_catalog_cache WHERE (user_id IS NULL OR user_id = :userId) AND is_active = 1 AND (logo_path IS NOT NULL OR logo_dark_path IS NOT NULL)")
    suspend fun getMerchantsWithRemoteLogos(userId: String? = null): List<MerchantCatalogEntity>

    @Query("SELECT MAX(version) FROM merchant_catalog_cache WHERE user_id IS NULL")
    suspend fun getLatestVersion(): Long?
}
