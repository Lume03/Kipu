package com.kipu.app.feature.categories.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MerchantDiscoveryRoomTest {
    @Test fun `catalog queries isolate private owners and keep cursor global`() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.merchantCatalogDao()
            dao.insertMerchants(listOf(
                MerchantCatalogEntity("global","Global","global",version=5,lastSyncedAt=0),
                MerchantCatalogEntity("a","Private A","private a",version=100,lastSyncedAt=0,userId="a"),
                MerchantCatalogEntity("b","Private B","private b",version=101,lastSyncedAt=0,userId="b"),
            ))
            assertEquals(setOf("global","a"),dao.getAllActiveMerchants("a").first().map { it.id }.toSet())
            assertEquals(listOf("global"),dao.getAllActiveMerchants().first().map { it.id })
            assertEquals(listOf("b"),dao.searchMerchants("private","b").first().map { it.id })
            assertNull(dao.getMerchantById("a","b"))
            assertEquals(5L,dao.getLatestVersion())
        } finally { db.close() }
    }
}
