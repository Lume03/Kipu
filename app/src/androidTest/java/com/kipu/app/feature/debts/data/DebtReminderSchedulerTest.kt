package com.kipu.app.feature.debts.data

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
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.debts.data.sync.DebtReminderRequest
import com.kipu.app.feature.debts.data.sync.DebtReminderScheduler
import com.kipu.app.feature.debts.data.sync.DebtReminderWorker
import com.kipu.app.feature.debts.data.sync.WorkManagerDebtReminderScheduler
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun initializeWorkManager() {
        context = ApplicationProvider.getApplicationContext()
        grantNotificationPermission()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun grantNotificationPermission() {
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
    fun replanReplacesUniqueWorkForTheSameInstallmentAndDueDateChange() {
        val scheduler = scheduler(allowed = true)
        val first = request("2026-12-10")
        val replacement = request("2027-01-10")

        scheduler.schedule(first)
        scheduler.schedule(replacement)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(first)).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertTrue(work.single().tags.contains(DebtReminderScheduler.dueDateTag(replacement)))
        assertTrue(!work.single().tags.contains(DebtReminderScheduler.dueDateTag(first)))
        assertEquals(DebtReminderScheduler.notificationTag(first), DebtReminderScheduler.notificationTag(replacement))
    }

    @Test
    fun retryWithTheSameReminderIdentityKeepsOnlyOnePendingWork() {
        val scheduler = scheduler(allowed = true)
        val reminder = request("2026-12-10")

        scheduler.schedule(reminder)
        scheduler.schedule(reminder)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(reminder)).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(true, work.single().tags.contains(DebtReminderScheduler.dueDateTag(reminder)))
    }

    @Test
    fun deniedNotificationPermissionKeepsReminderScheduledWithoutChangingTheDebt() {
        val allowed = scheduler(allowed = true)
        val denied = scheduler(allowed = false)
        val reminder = request("2026-12-10")

        allowed.schedule(reminder)
        denied.schedule(reminder)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(reminder)).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals("2026-12-10", reminder.dueDate.toString())
    }

    @Test
    fun explicitZeroLeadDaysDoesNotScheduleReminder() {
        val reminder = request("2026-12-10").copy(leadDays = 0)

        scheduler(allowed = true).schedule(reminder)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(reminder)).get()
        assertTrue(work.isEmpty() || work.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun configuredLeadsScheduleIndependentlyPerInstallment() {
        val defaultReminder = request("2026-12-10", leadDays = 2)
        val customReminder = request("2026-12-10", leadDays = 7).copy(installmentId = OTHER_INSTALLMENT_ID)
        val scheduler = scheduler(allowed = true)

        scheduler.schedule(defaultReminder)
        scheduler.schedule(customReminder)

        assertEquals(WorkInfo.State.ENQUEUED, workManager
            .getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(defaultReminder)).get().single().state)
        assertEquals(WorkInfo.State.ENQUEUED, workManager
            .getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(customReminder)).get().single().state)
    }

    @Test
    fun cancellingPreviousOwnerDoesNotCancelCurrentOwnersWork() {
        val scheduler = scheduler(allowed = true)
        val oldOwnerReminder = request("2026-12-10")
        val currentOwnerReminder = oldOwnerReminder.copy(userId = OTHER_OWNER_ID)
        scheduler.schedule(oldOwnerReminder)
        scheduler.schedule(currentOwnerReminder)

        scheduler.cancelUser(OWNER_ID)

        val oldOwnerWork = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(oldOwnerReminder))
            .get().single()
        val currentOwnerWork = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(currentOwnerReminder))
            .get().single()
        assertEquals(WorkInfo.State.CANCELLED, oldOwnerWork.state)
        assertEquals(WorkInfo.State.ENQUEUED, currentOwnerWork.state)
    }

    @Test
    fun reschedulingForANewDueDateWithdrawsPreviouslyPostedSystemNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val original = request("2026-12-10")
        val tag = DebtReminderScheduler.notificationTag(original)
        val channelId = "hu44-test-notifications"
        ensureTestChannel(manager, channelId)
        assertTrue("App notifications must be enabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
        assertTrue("Test channel must be enabled", manager.getNotificationChannel(channelId).importance != NotificationManager.IMPORTANCE_NONE)
        try {
            NotificationManagerCompat.from(context).notify(
                tag,
                0,
                androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Old reminder")
                    .setContentText("Old due date")
                    .addExtras(android.os.Bundle().apply { putString(DebtReminderWorker.KEY_DUE_DATE, original.dueDate.toString()) })
                    .build(),
            )
            awaitActiveNotification(tag, 0, timeoutMillis = 3_000)

            scheduler(allowed = true).schedule(original.copy(dueDate = LocalDate.parse("2027-01-10")))

            assertTrue(
                "Replanning must withdraw the previously posted system notification",
                manager.activeNotifications.none { it.tag == tag && it.id == 0 },
            )
        } finally {
            NotificationManagerCompat.from(context).cancel(tag, 0)
        }
    }

    @Test
    fun replanPreservesNoticeWhenDueDateMetadataIsUnknownOrCurrent() {
        listOf("2026-12-10", null, "not-a-date", "2026-2-03").forEach { metadata ->
            assertNoticePreservedAfterReplan(metadata)
        }
    }

    private fun assertNoticePreservedAfterReplan(dueDateMetadata: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hu44-test-notifications"
        val tag = DebtReminderScheduler.notificationTag(request("2026-12-10"))
        ensureTestChannel(manager, channelId)
        assertTrue("App notifications must already be enabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
        assertTrue("Test channel must already be enabled", manager.getNotificationChannel(channelId)?.let { it.importance != NotificationManager.IMPORTANCE_NONE } == true)
        try {
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reminder")
                .setContentText("Metadata preservation")
            if (dueDateMetadata != null) {
                builder.addExtras(android.os.Bundle().apply { putString(DebtReminderWorker.KEY_DUE_DATE, dueDateMetadata) })
            }
            NotificationManagerCompat.from(context).notify(tag, 0, builder.build())
            awaitActiveNotification(tag, 0, timeoutMillis = 3_000)
            scheduler(allowed = true).schedule(request("2026-12-10"))
            assertTrue("Notice with metadata '$dueDateMetadata' must be preserved", manager.activeNotifications.any { it.tag == tag && it.id == 0 })
        } finally {
            NotificationManagerCompat.from(context).cancel(tag, 0)
        }
    }

    @Test
    fun cancelInstallmentRemovesOnlyItsPostedSystemNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val target = DebtReminderScheduler.notificationTag(request("2026-12-10"))
        val sibling = DebtReminderScheduler.notificationTag(
            request("2026-12-10").copy(installmentId = OTHER_INSTALLMENT_ID),
        )
        val otherSource = DebtReminderScheduler.notificationTag(request("2026-12-10").copy(debtId = OTHER_DEBT_ID))
        val otherOwner = DebtReminderScheduler.notificationTag(
            request("2026-12-10").copy(userId = OTHER_OWNER_ID, installmentId = OTHER_INSTALLMENT_ID),
        )
        val channelId = "hu44-test-notifications"
        ensureTestChannel(manager, channelId)
        assertTrue("App notifications must be enabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
        assertTrue("Test channel must be enabled", manager.getNotificationChannel(channelId).importance != NotificationManager.IMPORTANCE_NONE)
        val tags = listOf(target, sibling, otherSource, otherOwner)
        try {
            tags.forEach {
                NotificationManagerCompat.from(context).notify(
                    it,
                    0,
                    androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Test")
                        .setContentText("Test")
                        .build(),
                )
            }
            tags.forEach { awaitActiveNotification(it, 0, timeoutMillis = 3_000) }
            val posted = manager.activeNotifications.map { it.tag to it.id }.toSet()
            tags.forEach { assertTrue("Expected posted notification $it/0; active=$posted", it to 0 in posted) }

            scheduler(allowed = true).cancel(OWNER_ID, DEBT_ID, INSTALLMENT_ID)

            val active = manager.activeNotifications.map { it.tag to it.id }.toSet()
            assertTrue("Target installment notification should be cancelled", target to 0 !in active)
            assertTrue("Sibling installment notification should remain", sibling to 0 in active)
            assertTrue("Other debt source notification should remain", otherSource to 0 in active)
            assertTrue("Other owner notification should remain", otherOwner to 0 in active)
        } finally {
            tags.forEach { NotificationManagerCompat.from(context).cancel(it, 0) }
        }
    }

    private fun ensureTestChannel(manager: NotificationManager, channelId: String) {
        manager.createNotificationChannel(
            android.app.NotificationChannel(channelId, "Test", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun awaitActiveNotification(tag: String, id: Int, timeoutMillis: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis
        var active = manager.activeNotifications
        while (active.none { it.tag == tag && it.id == id } && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50)
            active = manager.activeNotifications
        }
        val channelImportance = manager.notificationChannels
            .firstOrNull { it.id == "hu44-test-notifications" }?.importance
        val notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        assertTrue(
            "Notification $tag/$id did not become active within ${timeoutMillis}ms; " +
                "package=${context.packageName}, notificationPermissionGranted=$notificationPermissionGranted, " +
                "appNotificationsEnabled=${NotificationManagerCompat.from(context).areNotificationsEnabled()}, " +
                "channelImportance=$channelImportance, " +
                "activeTags=${active.map { it.tag to it.id }}",
            active.any { it.tag == tag && it.id == id },
        )
    }

    private fun scheduler(allowed: Boolean) = WorkManagerDebtReminderScheduler(
        context = context,
        workManager = workManager,
        permissionChecker = object : DebtNotificationPermissionChecker {
            override fun canPostDebtReminder(): Boolean = allowed
        },
    )

    private fun request(dueDate: String, leadDays: Int = 3) = DebtReminderRequest(
        userId = OWNER_ID,
        debtId = DEBT_ID,
        installmentId = INSTALLMENT_ID,
        dueDate = LocalDate.parse(dueDate),
        leadDays = leadDays,
        counterpartyName = "Proveedor",
        installmentNumber = 1,
        remainingPrincipalMinor = 3_334L,
        currencyCode = "PEN",
        scheduledAt = Instant.parse("2026-10-08T10:00:00Z"),
    )

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val OTHER_DEBT_ID = "88000000-0000-4000-8000-000000000006"
        const val INSTALLMENT_ID = "88000000-0000-4000-8000-000000000003"
        const val OTHER_INSTALLMENT_ID = "88000000-0000-4000-8000-000000000005"
        const val OTHER_OWNER_ID = "88000000-0000-4000-8000-000000000004"
    }
}
