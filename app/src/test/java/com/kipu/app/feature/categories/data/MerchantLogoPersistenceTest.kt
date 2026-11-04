package com.kipu.app.feature.categories.data

import android.app.Application
import androidx.room.Room
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_38_39
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.categories.domain.MerchantSearchCandidate
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MerchantLogoPersistenceTest {
    @Test fun `provider selection persists logo metadata and purchase relation after database reopen`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = java.io.File(System.getProperty("java.io.tmpdir"), "merchant-block4-${UUID.randomUUID()}.db").absolutePath
        val owner = UUID.fromString("f4000000-0000-0000-0000-000000000001")
        fun open() = Room.databaseBuilder(context, KipuDatabase::class.java, file).allowMainThreadQueries().build()
        var db = open()
        val client = HttpClient(MockEngine {
            respond("""{"merchant":{"id":"miniso","name":"MINISO","normalized_name":"miniso","version":543,"domain":"miniso.test","logo_status":"READY","catalog_origin":"USER_ADDED","logo_source":"LOGO_DEV","logo_path":"logo-dev/miniso.test/v1.webp","logo_dark_path":"logo-dev/miniso.test/v1-dark.webp"}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
        }
        try {
            val sessions = object : AuthenticatedSessionProvider {
                override suspend fun currentSession() = AuthenticatedSession(owner, "test-token")
                override suspend fun refreshSession() = currentSession()
            }
            val added = MerchantDiscoveryRepositoryImpl(client, sessions, db.merchantCatalogDao())
                .add(MerchantSearchCandidate("MINISO", "miniso.test", null, false, null, "miniso"))
            db.accountDao().insert(com.kipu.app.feature.accounts.data.local.AccountEntity(
                id="account", userId=owner.toString(), creationOperationId="opening", alias="Cash",
                type="CASH", currency="PEN", presetId=null, color=null, icon=null,
                initialBalanceMinorUnits=5000, openedAt=0, createdAt=0, updatedAt=0))
            db.categoryDao().insertCategory(com.kipu.app.feature.categories.data.local.CategoryEntity(
                id="category", userId=null, parentId=null, origin="SYSTEM", createdAt=0, updatedAt=0,
                categoryType="EXPENSE"))
            db.movementDao().insertTransaction(TransactionEntity(id="purchase", userId=owner.toString(),
                type="EXPENSE", amountMinor=1234, currencyCode="PEN", sourceAccountId="account", categoryId="category", occurredAt=1000, merchantId=added.id.value))
            db.close()
            db = open()
            val merchant = db.merchantCatalogDao().searchMerchants("miniso", "other-user").first().single()
            assertEquals("miniso.test", merchant.domain)
            assertEquals("READY", merchant.logoStatus)
            assertEquals("USER_ADDED", merchant.catalogOrigin)
            assertEquals(0L, merchant.version)
            val local = MovementLocalDataSource(db, db.movementDao(), mockk())
            val repo = OfflineFirstMovementRepository(local, mockk(), mockk(), mockk(), mockk(),
                db.accountDao(), db.cardDao(), db.categoryDao(), db.merchantCatalogDao())
            val item = repo.observeTransactions(owner.toString()).first().single()
            assertEquals(added.id.value, item.transaction.merchantId)
            assertEquals("MINISO", item.merchantName)
            // URLs depend on BuildConfig, but the exact persisted storage objects must survive reopening.
            assertEquals("logo-dev/miniso.test/v1.webp", merchant.logoPath)
            assertEquals("logo-dev/miniso.test/v1-dark.webp", merchant.logoDarkPath)
            assertEquals(com.kipu.app.core.storage.data.MerchantLogoUrlResolver.resolve(
                com.kipu.app.BuildConfig.SUPABASE_URL, merchant.logoPath), item.merchantLogoUrl)
        } finally {
            client.close()
            db.close()
            context.deleteDatabase(file)
        }
    }
    @Test fun `upgrade preserves prior logo and manually selected merchant references`() {
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null).callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(38) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE merchant_catalog_cache(id TEXT PRIMARY KEY,logo_path TEXT,user_id TEXT)")
                        db.execSQL("CREATE TABLE transactions(id TEXT PRIMARY KEY,merchant_id TEXT)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        helper.use {
            val db = it.writableDatabase
            db.execSQL("INSERT INTO merchant_catalog_cache VALUES('private','manual/shop/logo.webp','owner')")
            db.execSQL("INSERT INTO transactions VALUES('buy','private')")
            MIGRATION_38_39.migrate(db)
            db.query("SELECT id,logo_path,user_id,domain,catalog_origin FROM merchant_catalog_cache").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("private", c.getString(0))
                assertEquals("manual/shop/logo.webp", c.getString(1))
                assertEquals("owner", c.getString(2))
                assertTrue(c.isNull(3))
                assertEquals("CURATED", c.getString(4))
            }
            db.query("SELECT merchant_id FROM transactions").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("private", c.getString(0))
            }
        }
    }
}
