package com.kipu.app.feature.plans.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FreeDebtQuotaConsistencyTest {
    @Test
    fun debtWritesEnforceTheAdvertisedSharedQuota() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val source = DebtLocalDataSource(
                database, database.debtDao(), database.debtOutboxDao(), database.movementDao(),
                database.accountDao(), database.financialMovementDao(), database.categoryDao(),
            )
            val owner = UUID.randomUUID().toString()
            val limit = FreePlanLimits().debts
            repeat(limit) { index ->
                val result = source.openDebt(owner, command(DebtObligationType.entries[index % 2]), false)
                assertTrue("Unexpected result: $result", result is DebtCommandResult.Applied)
            }
            assertEquals(
                DebtCommandResult.Rejected("FREE_DEBT_QUOTA_EXCEEDED"),
                source.openDebt(owner, command(DebtObligationType.PAYABLE), false),
            )
            assertEquals(limit, database.debtDao().countActiveDebts(owner))
            assertTrue(source.openDebt(owner, command(DebtObligationType.RECEIVABLE), true) is DebtCommandResult.Applied)
        } finally {
            database.close()
        }
    }

    private fun command(type: DebtObligationType) = OpenDebtCommand(
        identity = DebtCommandIdentity(UUID.randomUUID().toString(), "a".repeat(64)),
        debtId = UUID.randomUUID().toString(),
        obligationType = type,
        counterpartyName = "Persona",
        principalMinor = 10_000,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        openingMode = DebtOpeningMode.HISTORICAL,
    )
}
