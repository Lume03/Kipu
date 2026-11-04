package com.kipu.app.feature.categories.data.sync

import android.content.Context
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import com.kipu.app.feature.categories.data.remote.CategoryApiResponse
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException

class CategoryHydration @javax.inject.Inject constructor(
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val applicationContext: Context,
    private val api: CategoriesApi,
) {
    private fun parseRemoteInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()

    private fun isGeneratedPlaceholder(presentation: CategoryPresentationEntity): Boolean {
        val defaults = InitialCategoryCatalog.find(presentation.categoryId) ?: return false
        return presentation.name == defaults.name && presentation.color.equals("#757575", ignoreCase = true) &&
            (presentation.icon == "category" || presentation.icon == defaults.iconKey)
    }
    suspend fun hydrateFully(userId: String): Boolean {
        hydrateCategories(userId)
        hydrateMerchantCatalog(full = true)
        return true
    }
    suspend fun hydrateCategories(userId: String) {
        try {
            val pendingCommands = categoryDao.getPendingOutboxCommands(userId)
            val pendingCategoryIds = pendingCommands.map { it.aggregateId }.toSet()

            val categories = when (val response = api.fetchCategories()) {
                is CategoryApiResponse.Success -> {
                    val visible = response.data.filter { it.userId == null || it.userId == userId }
                    for (dto in visible) {
                        val existing = categoryDao.getCategoryById(dto.id)
                        val entity = CategoryEntity(
                            id = dto.id,
                            userId = dto.userId,
                            parentId = dto.parentId,
                            origin = dto.origin,
                            categoryType = InitialCategoryCatalog.resolvedType(
                                dto.id, dto.parentId, dto.origin, dto.categoryType),
                            isActive = dto.isActive,
                            remoteRevision = dto.remoteRevision,
                            createdAt = parseRemoteInstant(dto.createdAt).toEpochMilli(),
                            updatedAt = parseRemoteInstant(dto.updatedAt).toEpochMilli(),
                        )
                        if (existing == null) {
                            categoryDao.insertCategory(entity)
                        } else if (dto.id !in pendingCategoryIds && dto.remoteRevision >= existing.remoteRevision) {
                            categoryDao.updateCategory(entity)
                        }
                    }
                    visible
                }
                else -> {
                    SecureLog.w("SyncCategoryCommandsWorker", "Failed to load categories")
                    throw java.io.IOException("CATEGORY_FETCH_FAILED")
                }
            }

            val remotePresentationIds = mutableSetOf<String>()
            when (val response = api.fetchCategoryPresentations()) {
                is CategoryApiResponse.Success -> {
                    val userPresentations = response.data.filter { it.userId == userId }
                    remotePresentationIds += userPresentations.map { it.categoryId }
                    for (dto in userPresentations) {
                        val existing = categoryDao.getPresentation(userId, dto.categoryId)
                        val entity = CategoryPresentationEntity(
                            categoryId = dto.categoryId,
                            userId = dto.userId,
                            name = dto.name,
                            icon = dto.icon,
                            color = dto.color,
                            remoteRevision = dto.remoteRevision,
                            updatedAt = parseRemoteInstant(dto.updatedAt).toEpochMilli(),
                        )
                        if (existing == null) {
                            categoryDao.insertPresentation(entity)
                        } else if (dto.categoryId !in pendingCategoryIds &&
                            (dto.remoteRevision >= existing.remoteRevision || isGeneratedPlaceholder(existing))) {
                            categoryDao.insertPresentation(entity)
                        }
                    }
                }
                else -> throw java.io.IOException("CATEGORY_PRESENTATIONS_FETCH_FAILED")
            }

            // The server returns personal presentations separately. Repair generated cache
            // placeholders only when no remote customization or pending local command exists.
            for (dto in categories.filter { it.origin == "SYSTEM" }) {
                val defaults = InitialCategoryCatalog.find(dto.id)
                val existing = categoryDao.getPresentation(userId, dto.id)
                val generatedPlaceholder = existing?.let(::isGeneratedPlaceholder) == true
                if (dto.id !in remotePresentationIds && dto.id !in pendingCategoryIds &&
                    (existing == null || generatedPlaceholder)) {
                    categoryDao.insertPresentation(CategoryPresentationEntity(
                        categoryId = dto.id, userId = userId, name = dto.name,
                        icon = dto.iconKey ?: defaults?.iconKey ?: "category",
                        color = defaults?.color ?: "#757575",
                        remoteRevision = dto.remoteRevision,
                        updatedAt = parseRemoteInstant(dto.updatedAt).toEpochMilli(),
                    ))
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun hydrateMerchantCatalog(full: Boolean = false) {
        try {
            val latestVersion = if (full) 0L else merchantDao.getLatestVersion() ?: 0L
            when (val response = api.fetchMerchantCatalog(sinceVersion = latestVersion)) {
                is CategoryApiResponse.Success -> {
                    val now = System.currentTimeMillis()
                    val entities = response.data.map { dto ->
                        MerchantCatalogEntity(
                            id = dto.id,
                            userId = dto.userId,
                            name = dto.name,
                            normalizedName = com.kipu.app.feature.categories.domain.CategoryRules.normalizeText(dto.normalizedName),
                            isActive = dto.isActive,
                            version = dto.version,
                            lastSyncedAt = now,
                            defaultCategoryId = dto.defaultCategoryId,
                            priority = dto.priority,
                            logoKey = dto.logoKey,
                            brandColor = dto.brandColor,
                            logoPath = dto.logoPath,
                            logoDarkPath = dto.logoDarkPath,
                            logoSource = dto.logoSource, domain = dto.domain, logoStatus = dto.logoStatus, catalogOrigin = dto.catalogOrigin,
                        )
                    }
                    if (entities.isNotEmpty()) {
                        merchantDao.insertMerchants(entities)
                        if (MerchantLogoPrefetchPlan.hasRemoteLogos(entities)) {
                            MerchantLogoPrefetchWorker.enqueue(applicationContext)
                        }
                    }
                }
                else -> {
                    throw java.io.IOException("MERCHANT_FETCH_FAILED")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

}
