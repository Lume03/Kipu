package com.kipu.app.feature.plans.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureAccessCacheDaoTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: FeatureAccessCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java,
        ).build()
        dao = database.featureAccessCacheDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun commercialBillingWritesCannotReplaceQaPremiumOrFreeButManualRefreshCan() = runBlocking {
        val userId = UUID.randomUUID()
        val qaPremium = FeatureAccessCacheEntity(
            userId = userId,
            policyVersion = 1,
            effectiveTier = "PREMIUM",
            source = "SIGNED_TEST_GRANT",
            offlineGrantPayload = "signed-payload",
            offlineGrantSignature = "signature",
            offlineGrantKeyId = "qa-key",
        )
        dao.putVerified(qaPremium)

        assertFalse(dao.putCommercialVerified(FeatureAccessCacheEntity.verifiedFree(userId)))
        assertEquals("signed-payload", dao.get(userId)?.offlineGrantPayload)
        assertEquals("PREMIUM", dao.get(userId)?.effectiveTier)

        val qaFree = FeatureAccessCacheEntity.verifiedFree(userId).copy(source = "TEST_OVERRIDE")
        dao.putVerified(qaFree)
        assertFalse(dao.putCommercialVerified(qaPremium.copy(source = "SIGNED_SERVER_GRANT")))
        assertEquals("FREE", dao.get(userId)?.effectiveTier)
        assertEquals("TEST_OVERRIDE", dao.get(userId)?.source)

        assertTrue(dao.putCommercialVerified(FeatureAccessCacheEntity.verifiedFree(userId), preserveTestOverrides = false))
        assertEquals("VERIFIED_SERVER", dao.get(userId)?.source)

        assertTrue(dao.putCommercialVerified(FeatureAccessCacheEntity.verifiedFree(UUID.randomUUID())))
    }
}
