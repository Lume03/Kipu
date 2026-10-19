package com.kipu.app.feature.recurrences.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.ReminderScheduleDao
import com.kipu.app.feature.recurrences.data.local.RecurrenceDao
import com.kipu.app.feature.recurrences.data.local.RecurrenceEntity
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recurrenceDao = mockk<RecurrenceDao>(relaxed = true)
    private val sessionCoordinator = mockk<SessionCoordinator>()
    private val scheduleDao = mockk<ReminderScheduleDao>(relaxed = true)
    private val eventDao = mockk<LocalReminderEventDao>(relaxed = true)

    @Test
    fun deniedPostNotificationsPersistsLocalEventWithoutRemoteMutation() = runTest {
        every { sessionCoordinator.currentOwner } returns LocalOwner(OWNER)
        coEvery { recurrenceDao.get(OWNER, RECURRENCE) } returns recurrence()
        val checker = PermissionChecker(false)

        val result = worker(checker).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { eventDao.upsert(any()) }
        coVerify(exactly = 1) { scheduleDao.upsert(any()) }
        coVerify(exactly = 0) { recurrenceDao.upsert(any()) }
        coVerify(exactly = 0) { recurrenceDao.updateIfRevisionMatches(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertTrue(checker.calls == 1)
    }

    @Test
    fun staleDateAndPausedOrCancelledRecurrencesAreRejected() = runTest {
        every { sessionCoordinator.currentOwner } returns LocalOwner(OWNER)
        coEvery { recurrenceDao.get(OWNER, RECURRENCE) } returnsMany listOf(
            recurrence().copy(nextDueDate = "2027-01-10"),
            recurrence().copy(status = "PAUSED", isActive = false),
            recurrence().copy(status = "CANCELLED", isActive = false),
        )

        repeat(3) { assertTrue(worker(PermissionChecker(true)).doWork() is ListenableWorker.Result.Success) }

        coVerify(exactly = 0) { eventDao.upsert(any()) }
        coVerify(exactly = 0) { scheduleDao.upsert(any()) }
    }

    @Test
    fun ownerChangeDoesNotReadRecurrenceOrPersistEvent() = runTest {
        every { sessionCoordinator.currentOwner } returns LocalOwner(OTHER_OWNER)

        assertTrue(worker(PermissionChecker(true)).doWork() is ListenableWorker.Result.Success)

        coVerify(exactly = 0) { recurrenceDao.get(any(), any()) }
        coVerify(exactly = 0) { eventDao.upsert(any()) }
    }

    private fun worker(checker: DebtNotificationPermissionChecker): RecurrenceReminderWorker =
        TestListenableWorkerBuilder<RecurrenceReminderWorker>(context)
            .setInputData(workDataOf(
                RecurrenceReminderWorker.KEY_USER_ID to OWNER,
                RecurrenceReminderWorker.KEY_RECURRENCE_ID to RECURRENCE,
                RecurrenceReminderWorker.KEY_DUE_DATE to DUE_DATE,
                RecurrenceReminderWorker.KEY_LEAD_DAYS to LEAD_DAYS,
            ))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    RecurrenceReminderWorker(appContext, workerParameters, recurrenceDao, sessionCoordinator, checker, scheduleDao, eventDao, com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator())
            })
            .build()

    private fun recurrence(status: String = "ACTIVE", isActive: Boolean = true) = RecurrenceEntity(
        id = RECURRENCE,
        userId = OWNER,
        templateTransaction = "{}",
        amountMode = "FIXED",
        frequency = "MONTHLY",
        intervalCount = 1,
        customIntervalUnit = null,
        startDate = DUE_DATE,
        endDate = null,
        nextDueDate = DUE_DATE,
        reminderLeadDays = LEAD_DAYS,
        status = status,
        isActive = isActive,
        revision = 1,
        createdAt = 1,
        updatedAt = 1,
    )

    private class PermissionChecker(private val allowed: Boolean) : DebtNotificationPermissionChecker {
        var calls = 0
        override fun canPostDebtReminder(): Boolean { calls++; return allowed }
    }

    private companion object {
        const val OWNER = "88000000-0000-4000-8000-000000000001"
        const val OTHER_OWNER = "88000000-0000-4000-8000-000000000004"
        const val RECURRENCE = "88000000-0000-4000-8000-000000000002"
        const val DUE_DATE = "2026-12-10"
        const val LEAD_DAYS = 3
    }
}
