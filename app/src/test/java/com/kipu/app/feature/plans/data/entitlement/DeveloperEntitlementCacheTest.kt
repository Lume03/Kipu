package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DeveloperEntitlementCacheTest {
    @Test
    fun `commercial writes preserve developer grants even when QA is disabled`() = runTest {
        val userId = UUID.randomUUID()
        var entry = FeatureAccessCacheEntity(userId, 1, "PREMIUM", source = "SIGNED_DEVELOPER_GRANT")
        val dao = object : FeatureAccessCacheDao {
            override suspend fun get(userId: UUID) = entry
            override suspend fun putVerified(entity: FeatureAccessCacheEntity) { entry = entity }
            override fun observe(userId: UUID) = flowOf(entry)
        }
        assertFalse(dao.putCommercialVerified(FeatureAccessCacheEntity.verifiedFree(userId), preserveTestOverrides = false))
        assertEquals("SIGNED_DEVELOPER_GRANT", entry.source)
        // The authenticated server refresh is authoritative for revocation.
        dao.putVerified(FeatureAccessCacheEntity.verifiedFree(userId))
        assertEquals("FREE", entry.effectiveTier)
        assertTrue(dao.putCommercialVerified(FeatureAccessCacheEntity.verifiedFree(userId), preserveTestOverrides = false))
    }
}
