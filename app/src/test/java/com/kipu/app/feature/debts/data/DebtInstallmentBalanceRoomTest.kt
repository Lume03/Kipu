package com.kipu.app.feature.debts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtEventEntity
import com.kipu.app.feature.debts.data.local.DebtInstallmentEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebtInstallmentBalanceRoomTest {
    @Test fun `remaining excludes voided payments`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.debtDao()
            dao.insertDebt(DebtEntity("debt", "owner", "PAYABLE", "Vendor", 1000, "PEN", "2026-01-01"))
            dao.insertInstallments(listOf(DebtInstallmentEntity("installment", "owner", "debt", 1, "2026-02-01", 1000)))
            db.movementDao().insertTransactions(listOf(
                TransactionEntity("active", "owner", "EXPENSE", 300, "PEN", occurredAt = 1),
                TransactionEntity("void", "owner", "EXPENSE", 200, "PEN", occurredAt = 2, status = "VOIDED"),
            ))
            dao.insertEvents(listOf(
                DebtEventEntity("a", "owner", "debt", transactionId = "active", installmentId = "installment", eventType = "PAYMENT", amountMinor = 300, occurredAt = 1),
                DebtEventEntity("b", "owner", "debt", transactionId = "void", installmentId = "installment", eventType = "PAYMENT", amountMinor = 200, occurredAt = 2),
            ))
            assertEquals(700L, dao.getInstallmentRemaining("owner", "debt", "installment"))
        } finally { db.close() }
    }
}
