package com.kipu.app.feature.debts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtInstallmentEntity
import com.kipu.app.feature.debts.data.sync.DebtReminderRequest
import com.kipu.app.feature.debts.data.sync.DebtReminderScheduler
import com.kipu.app.feature.debts.data.sync.LocalDebtReminderReconciler
import com.kipu.app.feature.notifications.data.local.LocalReminderEventEntity
import com.kipu.app.feature.notifications.data.local.ReminderEventReconciler
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtReminderReconciliationTest {
    private lateinit var database: KipuDatabase
    private val scheduled = mutableListOf<DebtReminderRequest>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KipuDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun leadZeroWithdrawsOnlyThatDebtInstallmentAndKeepsActiveSiblingAndOtherOwner() = runBlocking {
        val debtDao = database.debtDao()
        debtDao.insertDebt(debt(DEBT_A, OWNER_A, leadDays = 0))
        debtDao.insertInstallments(listOf(installment(INSTALLMENT_A, OWNER_A, DEBT_A, 1)))
        debtDao.insertDebt(debt(DEBT_SISTER, OWNER_A, leadDays = 2))
        debtDao.insertInstallments(listOf(installment(INSTALLMENT_SISTER, OWNER_A, DEBT_SISTER, 1)))
        debtDao.insertDebt(debt(DEBT_B, OWNER_B, leadDays = 2))
        debtDao.insertInstallments(listOf(installment(INSTALLMENT_B, OWNER_B, DEBT_B, 1)))
        val eventDao = database.localReminderEventDao()
        eventDao.upsert(event("event-a", OWNER_A, INSTALLMENT_A))
        eventDao.upsert(event("event-sister", OWNER_A, INSTALLMENT_SISTER))
        eventDao.upsert(event("event-b", OWNER_B, INSTALLMENT_A))
        val scheduler = RecordingScheduler(scheduled)

        LocalDebtReminderReconciler(debtDao, scheduler, ReminderEventReconciler(eventDao), com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator()).reconcile(OWNER_A, DEBT_A)

        assertEquals(listOf("event-sister"), eventDao.observeActive(OWNER_A).first().map { it.id })
        assertEquals(listOf("event-b"), eventDao.observeActive(OWNER_B).first().map { it.id })
        assertEquals(emptyList<DebtReminderRequest>(), scheduled)
    }

    @Test
    fun activeDebtReconciliationTombstonesReplacedDateAndSchedulesCurrentInstallment() = runBlocking {
        val debtDao = database.debtDao()
        debtDao.insertDebt(debt(DEBT_A, OWNER_A, leadDays = 3))
        debtDao.insertInstallments(listOf(installment(INSTALLMENT_A, OWNER_A, DEBT_A, 1, dueDate = "2026-11-20")))
        val eventDao = database.localReminderEventDao()
        eventDao.upsert(event("old-date", OWNER_A, INSTALLMENT_A, dueDate = "2026-10-20"))
        eventDao.upsert(event("new-date", OWNER_A, INSTALLMENT_A, dueDate = "2026-11-20"))
        val scheduler = RecordingScheduler(scheduled)

        LocalDebtReminderReconciler(debtDao, scheduler, ReminderEventReconciler(eventDao), com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator()).reconcile(OWNER_A, DEBT_A)

        assertEquals(listOf("new-date"), eventDao.observeActive(OWNER_A).first().map { it.id })
        assertEquals(listOf(INSTALLMENT_A), scheduled.map { it.installmentId })
        assertEquals("2026-11-20", scheduled.single().dueDate.toString())
    }

    @Test
    fun activeDebtLeadDays31And365KeepEventsAndScheduleCurrentDueDates() = runBlocking {
        val debtDao = database.debtDao()
        debtDao.insertDebt(debt(DEBT_A, OWNER_A, leadDays = 31))
        debtDao.insertInstallments(listOf(installment(INSTALLMENT_A, OWNER_A, DEBT_A, 1, dueDate = "2026-11-20")))
        debtDao.insertDebt(debt(DEBT_B, OWNER_A, leadDays = 365))
        debtDao.insertInstallments(listOf(installment(INSTALLMENT_B, OWNER_A, DEBT_B, 1, dueDate = "2026-12-15")))
        val eventDao = database.localReminderEventDao()
        eventDao.upsert(event("event-a", OWNER_A, INSTALLMENT_A))
        eventDao.upsert(event("event-b", OWNER_A, INSTALLMENT_B, dueDate = "2026-12-15"))
        val scheduler = RecordingScheduler(scheduled)
        val reconciler = LocalDebtReminderReconciler(debtDao, scheduler, ReminderEventReconciler(eventDao), com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator())

        reconciler.reconcile(OWNER_A, DEBT_A)
        reconciler.reconcile(OWNER_A, DEBT_B)

        assertEquals(listOf("event-a", "event-b"), eventDao.observeActive(OWNER_A).first().map { it.id }.sorted())
        assertEquals(listOf(INSTALLMENT_A, INSTALLMENT_B), scheduled.map { it.installmentId }.sorted())
        assertEquals(listOf(31, 365), scheduled.sortedBy { it.leadDays }.map { it.leadDays })
        assertEquals(
            listOf("2026-11-20", "2026-12-15"),
            scheduled.sortedBy { it.installmentId }.map { it.dueDate.toString() },
        )
    }

    private fun debt(id: String, owner: String, leadDays: Int) = DebtEntity(
        id = id,
        userId = owner,
        obligationType = "PAYABLE",
        counterpartyName = "Proveedor",
        totalMinor = 1000L,
        currencyCode = "PEN",
        openedOn = "2026-10-01",
        reminderLeadDays = leadDays,
    )

    private fun installment(id: String, owner: String, debtId: String, number: Int, dueDate: String = "2026-11-20") =
        DebtInstallmentEntity(
            id = id,
            userId = owner,
            debtId = debtId,
            installmentNumber = number,
            dueDate = dueDate,
            amountMinor = 1000L,
            status = "PENDING",
        )

    private fun event(id: String, owner: String, source: String, dueDate: String = "2026-11-20") =
        LocalReminderEventEntity(
            id = id,
            userId = owner,
            sourceId = source,
            dueDate = dueDate,
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        )

    private class RecordingScheduler(private val requests: MutableList<DebtReminderRequest>) : DebtReminderScheduler {
        override fun schedule(request: DebtReminderRequest) { requests += request }
        override fun cancel(userId: String, debtId: String, installmentId: String) = Unit
        override fun cancelDebt(userId: String, debtId: String) = Unit
        override fun cancelUser(userId: String) = Unit
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val DEBT_A = "debt-a"
        const val DEBT_B = "debt-b"
        const val DEBT_SISTER = "debt-sister"
        const val INSTALLMENT_A = "installment-a"
        const val INSTALLMENT_SISTER = "installment-sister"
        const val INSTALLMENT_B = "installment-b"
    }
}
