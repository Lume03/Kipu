package com.kipu.app.feature.debts.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtInstallmentEntity
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.debts.data.sync.DebtReminderWorker
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.ReminderScheduleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val debtDao = mockk<DebtDao>(relaxed = true)
    private val sessionCoordinator = mockk<SessionCoordinator>()
    private val reminderScheduleDao = mockk<ReminderScheduleDao>(relaxed = true)
    private val localReminderEventDao = mockk<LocalReminderEventDao>(relaxed = true)

    @Test
    fun deniedPermissionPersistsLocalReminderWithoutFailingWorker() = runTest {
        every { sessionCoordinator.currentOwner } returns LocalOwner(OWNER_ID)
        coEvery { debtDao.getDebt(OWNER_ID, DEBT_ID) } returns debt()
        coEvery { debtDao.getInstallment(OWNER_ID, DEBT_ID, INSTALLMENT_ID) } returns installment()
        coEvery { debtDao.getInstallmentRemaining(OWNER_ID, DEBT_ID, INSTALLMENT_ID) } returns 10_000L
        val worker = worker(permissionChecker = PermissionChecker(false))

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { localReminderEventDao.upsert(any()) }
    }

    @Test
    fun permissionRevokedBetweenChecksIsHandledAsOptionalNotification() = runTest {
        every { sessionCoordinator.currentOwner } returns LocalOwner(OWNER_ID)
        coEvery { debtDao.getDebt(OWNER_ID, DEBT_ID) } returns debt()
        coEvery { debtDao.getInstallment(OWNER_ID, DEBT_ID, INSTALLMENT_ID) } returns installment()
        coEvery { debtDao.getInstallmentRemaining(OWNER_ID, DEBT_ID, INSTALLMENT_ID) } returns 10_000L
        val permissionChecker = SequencePermissionChecker(true, false)
        val worker = worker(permissionChecker = permissionChecker)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(permissionChecker.calls == 1)
        coVerify(exactly = 1) { debtDao.getDebt(OWNER_ID, DEBT_ID) }
        coVerify(exactly = 1) { debtDao.getInstallment(OWNER_ID, DEBT_ID, INSTALLMENT_ID) }
        coVerify(exactly = 1) { localReminderEventDao.upsert(any()) }
    }

    @Test
    fun changedActiveOwnerDoesNotReadAnotherAccountsDebt() = runTest {
        every { sessionCoordinator.currentOwner } returns LocalOwner(OTHER_OWNER_ID)
        val worker = worker(permissionChecker = PermissionChecker(true))

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { debtDao.getDebt(any(), any()) }
    }

    private fun worker(permissionChecker: DebtNotificationPermissionChecker): DebtReminderWorker =
        TestListenableWorkerBuilder<DebtReminderWorker>(context)
            .setInputData(
                androidx.work.workDataOf(
                    DebtReminderWorker.KEY_USER_ID to OWNER_ID,
                    DebtReminderWorker.KEY_DEBT_ID to DEBT_ID,
                    DebtReminderWorker.KEY_INSTALLMENT_ID to INSTALLMENT_ID,
                    DebtReminderWorker.KEY_DUE_DATE to DUE_DATE,
                    DebtReminderWorker.KEY_LEAD_DAYS to LEAD_DAYS,
                ),
            )
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = DebtReminderWorker(
                    appContext,
                    workerParameters,
                    debtDao,
                    sessionCoordinator,
                    permissionChecker,
                    reminderScheduleDao,
                    localReminderEventDao,
                    com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator(),
                )
            })
            .build()

    private fun debt() = DebtEntity(
        id = DEBT_ID,
        userId = OWNER_ID,
        obligationType = "PAYABLE",
        counterpartyName = "Proveedor",
        totalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = "2026-01-01",
        reminderLeadDays = LEAD_DAYS,
    )

    private fun installment() = DebtInstallmentEntity(
        id = INSTALLMENT_ID,
        userId = OWNER_ID,
        debtId = DEBT_ID,
        installmentNumber = 1,
        dueDate = DUE_DATE,
        amountMinor = 10_000L,
        status = "PENDING",
    )

    private class PermissionChecker(private val allowed: Boolean) : DebtNotificationPermissionChecker {
        override fun canPostDebtReminder(): Boolean = allowed
    }

    private class SequencePermissionChecker(private vararg val values: Boolean) : DebtNotificationPermissionChecker {
        private var index = 0
        val calls: Int get() = index
        override fun canPostDebtReminder(): Boolean = values.getOrElse(index++) { values.last() }
    }

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val OTHER_OWNER_ID = "88000000-0000-4000-8000-000000000004"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val INSTALLMENT_ID = "88000000-0000-4000-8000-000000000003"
        const val DUE_DATE = "2026-12-10"
        const val LEAD_DAYS = 2
    }
}
