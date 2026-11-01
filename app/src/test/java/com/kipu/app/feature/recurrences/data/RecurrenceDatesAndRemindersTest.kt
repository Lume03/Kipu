package com.kipu.app.feature.recurrences.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.notifications.data.local.*
import com.kipu.app.feature.recurrences.data.local.*
import com.kipu.app.feature.recurrences.data.sync.*
import com.kipu.app.feature.recurrences.domain.*
import com.kipu.app.feature.recurrences.domain.model.*
import io.mockk.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class RecurrenceDatesAndRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-01T02:00:00Z"), ZoneOffset.UTC)
    private val store = mockk<ExpectedPaymentStore>(relaxed = true) {
        every { observeByRecurrence(any(), any()) } returns flowOf(emptyList())
    }
    private val dao = mockk<RecurrenceDao>()
    private val events = mockk<LocalReminderEventDao>(relaxed = true)
    private val schedules = mockk<ReminderScheduleDao>(relaxed = true)
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner("owner") }

    @Test fun expectedPaymentKeepsLimasCurrentOccurrenceAfterUtcChangesDay() = runTest {
        val payment = slot<ExpectedPayment>()
        coEvery { store.insertIfAbsent(capture(payment)) } returns true
        val rule = rule().copy(startDate = LocalDate.parse("2026-10-31"), nextDueDate = LocalDate.parse("2026-10-31"))
        GenerateExpectedPayments(store, clock).invoke(rule)
        assertEquals(LocalDate.parse("2026-10-31"), payment.captured.dueDate)
        assertEquals(1200L, payment.captured.expectedAmountMinor)
    }

    @Test fun previewExpectedAndReminderUseSameNextDueDateAtUtcBoundary() = runTest {
        val payment = slot<ExpectedPayment>()
        coEvery { store.insertIfAbsent(capture(payment)) } returns true
        val preview = RecurrenceLifecycleUseCase(mockk(), clock).previewResume(rule().copy(status = RecurrenceStatus.PAUSED))
        GenerateExpectedPayments(store, clock).invoke(rule())
        val manager = mockk<WorkManager>(relaxed = true)
        val request = slot<OneTimeWorkRequest>()
        every { manager.enqueueUniqueWork(any(), any(), capture(request)) } returns mockk(relaxed = true)
        WorkManagerRecurrenceReminderScheduler(context, manager, clock).schedule(RecurrenceReminderRequest("owner", "rule", preview.firstFutureDueDate, 1))
        assertEquals(LocalDate.parse("2026-11-01"), preview.firstFutureDueDate)
        assertEquals(preview.firstFutureDueDate, payment.captured.dueDate)
        assertEquals("2026-11-01", request.captured.workSpec.input.getString(RecurrenceReminderWorker.KEY_DUE_DATE))
    }

    @Test fun reminderDelayUsesLimaMidnightAndInjectedClock() {
        val manager = mockk<WorkManager>(relaxed = true)
        val request = slot<OneTimeWorkRequest>()
        every { manager.enqueueUniqueWork(any(), any(), capture(request)) } returns mockk(relaxed = true)
        WorkManagerRecurrenceReminderScheduler(context, manager, clock).schedule(RecurrenceReminderRequest("owner", "rule", LocalDate.parse("2026-11-02"), 1))
        assertEquals(10_800_000L, request.captured.workSpec.initialDelay)
    }

    @Test fun pendingConflictedAndFailedActiveRulesNeverCreateReminderEvents() = runTest {
        for (state in listOf("PENDING", "CONFLICT", "FAILED")) {
            coEvery { dao.get("owner", "rule") } returns entity().copy(syncState = state)
            assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        }
        coVerify(exactly = 0) { events.upsert(any()) }
        coVerify(exactly = 0) { schedules.upsert(any()) }
    }

    @Test fun serverConfirmedActiveRuleCreatesReminderEvent() = runTest {
        coEvery { dao.get("owner", "rule") } returns entity()
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { events.upsert(any()) }
    }

    private fun worker() = RecurrenceReminderWorker(
        context, mockk<WorkerParameters> {
            every { inputData } returns workDataOf(
                RecurrenceReminderWorker.KEY_USER_ID to "owner", RecurrenceReminderWorker.KEY_RECURRENCE_ID to "rule",
                RecurrenceReminderWorker.KEY_DUE_DATE to "2026-11-01", RecurrenceReminderWorker.KEY_LEAD_DAYS to 1,
            )
        }, dao, session, object : DebtNotificationPermissionChecker { override fun canPostDebtReminder() = false },
        schedules, events, ReminderReconciliationCoordinator(), store,
    )
    private fun entity() = RecurrenceEntity(
        "rule", "owner", "{}", "FIXED", "MONTHLY", 1, null, "2026-10-01", null, "2026-11-01", 1,
        "ACTIVE", true, 1, syncState = "SYNCED", createdAt = 1, updatedAt = 1,
    )
    private fun rule() = RecurrenceRule(
        "rule", "owner", buildJsonObject { put("amount_minor", 1200L); put("currency_code", "PEN") },
        "FIXED", "MONTHLY", 1, null, LocalDate.parse("2026-10-01"), null, LocalDate.parse("2026-11-01"), 1,
        RecurrenceStatus.ACTIVE, 1, RecurrenceSyncState.SYNCED, Instant.EPOCH, Instant.EPOCH,
    )
}
