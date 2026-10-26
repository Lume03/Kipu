package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtOutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Debts created while their RPC was not deployed (HTTP 404) must replay once it exists. */
@RunWith(AndroidJUnit4::class)
class DebtOutboxRecoveryTest {
    private lateinit var database: KipuDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun onlyMissingEndpointFailuresReturnToTheQueueAndTheirDebtsBecomePending() = runBlocking {
        val debtDao = database.debtDao()
        val outbox = database.debtOutboxDao()
        debtDao.insertDebt(debt(MISSING_RPC_DEBT))
        debtDao.insertDebt(debt(REJECTED_DEBT))
        outbox.insert(command("op-404", MISSING_RPC_DEBT, "HTTP_404"))
        outbox.insert(command("op-400", REJECTED_DEBT, "HTTP_400"))

        val requeued = outbox.requeueMissingEndpoint("owner", now = 5_000L)

        assertEquals(1, requeued)
        assertEquals("RETRY", outbox.get("owner", "op-404")?.state)
        assertEquals(5_000L, outbox.get("owner", "op-404")?.nextAttemptAt)
        assertEquals("FAILED_PERMANENT", outbox.get("owner", "op-400")?.state)
        assertEquals("PENDING", debtDao.getDebt("owner", MISSING_RPC_DEBT)?.syncState)
        assertEquals("FAILED", debtDao.getDebt("owner", REJECTED_DEBT)?.syncState)
        assertEquals(listOf("op-404"), outbox.claimPending("owner", now = 5_000L).map { it.operationId })
    }

    @Test
    fun pendingChildWaitsForItsDeferredParent() = runBlocking {
        database.debtDao().insertDebt(debt(MISSING_RPC_DEBT))
        val outbox = database.debtOutboxDao()
        outbox.insert(command("parent", MISSING_RPC_DEBT, "HTTP_404").copy(state = "RETRY", nextAttemptAt = 5000))
        outbox.insert(command("child", MISSING_RPC_DEBT, "").copy(state = "PENDING", createdAt = 2))
        assertEquals(emptyList<String>(), outbox.claimPending("owner", 1000).map { it.operationId })
        assertEquals(1, outbox.requeueMissingEndpoint("owner", 1000))
        assertEquals(listOf("parent"), outbox.claimPending("owner", 1000).map { it.operationId })
        outbox.updateResult("owner", "parent", "SYNCED", 0, null, null, 1001)
        assertEquals(listOf("child"), outbox.claimPending("owner", 1001).map { it.operationId })
    }

    private fun debt(id: String) = DebtEntity(
        id = id,
        userId = "owner",
        obligationType = "PAYABLE",
        counterpartyName = "Prestamo",
        totalMinor = 40_000L,
        currencyCode = "PEN",
        openedOn = "2026-10-25",
        openingMode = "HISTORICAL",
        syncState = "FAILED",
    )

    private fun command(operationId: String, debtId: String, error: String) = DebtOutboxEntity(
        userId = "owner",
        operationId = operationId,
        debtId = debtId,
        commandType = "OPEN_DEBT",
        requestHash = "hash-$operationId",
        payload = "{}",
        state = "FAILED_PERMANENT",
        attemptCount = 1,
        lastErrorCode = error,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private companion object {
        const val MISSING_RPC_DEBT = "9f45ceb6-5c53-41d3-b93b-1fe2bb5bff93"
        const val REJECTED_DEBT = "0727a4f6-d0d8-4b8e-aab2-7037a7f501da"
    }
}
