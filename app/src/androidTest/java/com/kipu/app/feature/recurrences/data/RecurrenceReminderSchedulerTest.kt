package com.kipu.app.feature.recurrences.data

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderRequest
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderScheduler
import com.kipu.app.feature.recurrences.data.sync.WorkManagerRecurrenceReminderScheduler
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceReminderSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerRecurrenceReminderScheduler

    @Before
    fun initializeWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        grantNotificationPermission(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerRecurrenceReminderScheduler(context, workManager)
    }

    private fun grantNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                )
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun defaultAndCustomLeadDaysAreScheduledAndZeroLeadCancels() {
        val default = request(leadDays = 2)
        val custom = request(leadDays = 9).copy(recurrenceId = OTHER_RECURRENCE)
        scheduler.schedule(default)
        scheduler.schedule(custom)
        assertEquals(WorkInfo.State.ENQUEUED, work(default).single().state)
        assertEquals(WorkInfo.State.ENQUEUED, work(custom).single().state)

        scheduler.schedule(default.copy(leadDays = 0))
        assertTrue(work(default).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun retryIsIdempotentAndReschedulingDateReplacesPendingWork() {
        val first = request()
        scheduler.schedule(first)
        scheduler.schedule(first)
        assertEquals(1, work(first).count { it.state == WorkInfo.State.ENQUEUED })

        val moved = first.copy(dueDate = first.dueDate.plusMonths(1))
        scheduler.schedule(moved)
        assertEquals(1, work(moved).count { it.state == WorkInfo.State.ENQUEUED })
        assertTrue(work(moved).single { it.state == WorkInfo.State.ENQUEUED }.tags.contains(
            RecurrenceReminderScheduler.sourceTag(OWNER, RECURRENCE),
        ))
    }

    @Test
    fun sameRecurrenceForAnotherOwnerHasIndependentWorkAndCancelIsOwnerScoped() {
        val owner = request()
        val other = owner.copy(userId = OTHER_OWNER)
        scheduler.schedule(owner)
        scheduler.schedule(other)
        scheduler.cancel(OWNER, RECURRENCE)

        assertTrue(work(owner).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(WorkInfo.State.ENQUEUED, work(other).single().state)
    }

    @Test
    fun replanPreservesUnknownOrCurrentMetadataAndCancelsOnlyCanonicalOldDate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hu44-test-notifications"
        manager.createNotificationChannel(
            android.app.NotificationChannel(channelId, "Test", NotificationManager.IMPORTANCE_DEFAULT),
        )
        assertTrue("App notifications must already be enabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
        assertTrue("Test channel must already be enabled", manager.getNotificationChannel(channelId)?.let { it.importance != NotificationManager.IMPORTANCE_NONE } == true)
        val current = request()
        val targetTag = "recurrence-reminder:${current.userId}:${current.recurrenceId}"
        val otherTag = "recurrence-reminder:$OTHER_OWNER:$RECURRENCE"
        val otherSourceTag = "recurrence-reminder:${current.userId}:$OTHER_RECURRENCE"
        val cases = listOf(current.dueDate.toString(), null, "not-a-date", "${current.dueDate.year}-2-03")
        try {
            cases.forEach { metadata ->
                postNotice(context, channelId, targetTag, metadata)
                awaitActive(manager, targetTag)
                scheduler.schedule(current)
                assertTrue("Notice with metadata '$metadata' must remain active", manager.activeNotifications.any { it.tag == targetTag && it.id == 0 })
                NotificationManagerCompat.from(context).cancel(targetTag, 0)
            }

            postNotice(context, channelId, targetTag, current.dueDate.minusDays(1).toString())
            postNotice(context, channelId, otherTag, current.dueDate.minusDays(1).toString())
            postNotice(context, channelId, otherSourceTag, current.dueDate.minusDays(1).toString())
            awaitActive(manager, targetTag)
            awaitActive(manager, otherTag)
            awaitActive(manager, otherSourceTag)
            scheduler.schedule(current)
            val activeTags = manager.activeNotifications.map { it.tag to it.id }.toSet()
            assertTrue("Canonical stale date must be cancelled", targetTag to 0 !in activeTags)
            assertTrue("Other owner's notice must remain", otherTag to 0 in activeTags)
            assertTrue("Other recurrence source must remain", otherSourceTag to 0 in activeTags)
        } finally {
            NotificationManagerCompat.from(context).cancel(targetTag, 0)
            NotificationManagerCompat.from(context).cancel(otherTag, 0)
            NotificationManagerCompat.from(context).cancel(otherSourceTag, 0)
        }
    }

    private fun postNotice(context: Context, channelId: String, tag: String, metadata: String?) {
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reminder")
            .setContentText("Metadata regression")
        if (metadata != null) {
            builder.addExtras(android.os.Bundle().apply {
                putString(com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderWorker.KEY_DUE_DATE, metadata)
            })
        }
        NotificationManagerCompat.from(context).notify(tag, 0, builder.build())
    }

    private fun awaitActive(manager: NotificationManager, tag: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
        while (manager.activeNotifications.none { it.tag == tag && it.id == 0 } && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50)
        }
        assertTrue("Expected real active notification $tag/0", manager.activeNotifications.any { it.tag == tag && it.id == 0 })
    }

    private fun work(request: RecurrenceReminderRequest) =
        workManager.getWorkInfosForUniqueWork(RecurrenceReminderScheduler.workName(request)).get()

    private fun request(leadDays: Int = 3) = RecurrenceReminderRequest(
        userId = OWNER,
        recurrenceId = RECURRENCE,
        dueDate = LocalDate.now().plusMonths(2),
        leadDays = leadDays,
    )

    private companion object {
        const val OWNER = "88000000-0000-4000-8000-000000000001"
        const val OTHER_OWNER = "88000000-0000-4000-8000-000000000004"
        const val RECURRENCE = "88000000-0000-4000-8000-000000000002"
        const val OTHER_RECURRENCE = "88000000-0000-4000-8000-000000000005"
    }
}
