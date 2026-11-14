package com.kipu.app.feature.movements.presentation
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.MovementHistoryCursor
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MovementHistoryQueryCostRoomTest {
    @Test
    fun pageQueriesHaveConstantCostAndMeasureLegacyVersusBatchedP95() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selects = java.util.concurrent.atomic.AtomicInteger()
        val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> if (sql.trimStart().startsWith("SELECT", ignoreCase=true)) selects.incrementAndGet() }, java.util.concurrent.Executor { it.run() })
            .build()
        try {
            val dao = db.movementDao()
            val projectionStore = BalanceProjectionStore(dao)
            val source = MovementLocalDataSource(db, dao, projectionStore)
            val syncScheduler = MovementSyncScheduler(context)
            val repository = OfflineFirstMovementRepository(
                localDataSource = source,
                balanceProjectionStore = projectionStore,
                hasher = TransactionRequestHasher(),
                revisionHasher = MovementRevisionRequestHasher(),
                syncScheduler = syncScheduler,
                accountDao = db.accountDao(),
                cardDao = db.cardDao(),
                categoryDao = db.categoryDao(),
                merchantDao = db.merchantCatalogDao(),
            )

            val userId = "perf-user"
            val now = System.currentTimeMillis()
            db.accountDao().insert(
                AccountEntity(
                    id = "acc-main",
                    userId = userId,
                    creationOperationId = "perf-account-create",
                    alias = "Cuenta principal",
                    type = "BANK",
                    currency = "PEN",
                    presetId = null,
                    color = null,
                    icon = null,
                    initialBalanceMinorUnits = 0L,
                    openedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val entities = List(10_000) { i ->
                TransactionEntity(
                    id = "tx-perf-$i",
                    userId = userId,
                    type = "EXPENSE",
                    amountMinor = 1_000L + (i % 100),
                    currencyCode = "PEN",
                    sourceAccountId = "acc-main",
                    destinationAccountId = null,
                    categoryId = "cat-food",
                    merchantId = null,
                    merchantProvisionalText = null,
                    occurredAt = now - i * 60_000L,
                    note = "Performance item $i",
                    status = "ACTIVE",
                    revision = 1L,
                    currentRevisionId = "rev-$i",
                    syncStatus = "CONFIRMED",
                    createdAt = now - i * 60_000L,
                    updatedAt = now - i * 60_000L,
                    legacyKind = null,
                )
            }
            entities.chunked(500).forEach { chunk ->
                dao.insertTransactions(chunk)
            }

            val raw = com.kipu.app.feature.movements.data.local.MovementQuerySqlBuilder.build(userId, MovementHistoryQuery(limit = 50))
            val explain = androidx.sqlite.db.SimpleSQLiteQuery("EXPLAIN QUERY PLAN " + raw.sql, arrayOf<Any>(userId, 51))
            val plans = db.openHelper.readableDatabase.query(explain).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
            }
            println("A16-03 EXPLAIN: $plans")
            assertTrue(plans.any { it.contains("INDEX") })
            val legacyLatencies = mutableListOf<Long>()
            repeat(20) {
                val began = System.nanoTime()
                val rows = dao.queryTransactions(raw)
                for (row in rows) {
                    dao.getAllOutboxForTransaction(userId, row.id)
                    source.getVoidBlockCode(userId, row.id)
                }
                legacyLatencies += (System.nanoTime() - began) / 1_000_000
            }
            // Warm up
            repository.queryHistory(userId, MovementHistoryQuery(limit = 50))

            selects.set(0)
            val checked = repository.queryHistory(userId, MovementHistoryQuery(limit = 50))
            val pageSelects = selects.get()
            assertTrue("A page uses a bounded number of SELECTs: $pageSelects", pageSelects < 20)
            assertEquals(50, checked.items.size)
            println("A16-03 full page SELECT count=$pageSelects (legacy >=154)")
            // Perform 20 paginated queries and measure latencies
            val latencies = mutableListOf<Long>()
            var cursor: MovementHistoryCursor? = null
            val collectedIds = mutableListOf<String>()

            for (p in 0 until 20) {
                val start = System.nanoTime()
                val page = repository.queryHistory(userId, MovementHistoryQuery(limit = 50, cursor = cursor))
                val elapsed = (System.nanoTime() - start) / 1_000_000
                latencies.add(elapsed)
                collectedIds.addAll(page.items.map { it.transaction.id })
                cursor = page.nextCursor
                if (!page.hasMore) break
            }

            latencies.sort()
            val p95Index = (kotlin.math.ceil(latencies.size * 0.95).toInt() - 1).coerceIn(0, latencies.lastIndex)
            val p95 = latencies[p95Index]

            println("A16-03 JVM 10k legacy page enrichment p95=${legacyLatencies.sorted()[p95Index]}ms; full batched repository p95=${p95}ms; samples=$latencies")
            assertEquals(1000, collectedIds.size)
            assertEquals(collectedIds.toSet().size, collectedIds.size)
        } finally {
            db.close()
        }
    }
}
