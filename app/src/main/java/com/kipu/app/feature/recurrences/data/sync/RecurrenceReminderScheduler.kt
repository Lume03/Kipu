package com.kipu.app.feature.recurrences.data.sync

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RecurrenceReminderRequest(
    val userId: String,
    val recurrenceId: String,
    val dueDate: LocalDate,
    val leadDays: Int,
)

interface RecurrenceReminderScheduler {
    fun schedule(request: RecurrenceReminderRequest)
    fun cancel(userId: String, recurrenceId: String)

    companion object {
        fun workName(request: RecurrenceReminderRequest): String =
            "recurrence-reminder:${request.userId}:${request.recurrenceId}"

        fun sourceTag(userId: String, recurrenceId: String): String =
            "recurrence-reminder-source:$userId:$recurrenceId"

        fun ownerTag(userId: String): String = "recurrence-reminder-owner:$userId"
    }
}

@Singleton
class WorkManagerRecurrenceReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
) : RecurrenceReminderScheduler {
    override fun schedule(request: RecurrenceReminderRequest) {
        if (request.userId.isBlank() || request.recurrenceId.isBlank() || request.leadDays !in 0..30 || request.leadDays == 0) {
            cancel(request.userId, request.recurrenceId)
            return
        }
        cancelPostedNotificationIfObsolete(request.userId, request.recurrenceId, request.dueDate.toString())
        val fireAt = request.dueDate.minusDays(request.leadDays.toLong())
            .atStartOfDay(LIMA_ZONE).toInstant().toEpochMilli()
        val work = OneTimeWorkRequestBuilder<RecurrenceReminderWorker>()
            .setInitialDelay((fireAt - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    RecurrenceReminderWorker.KEY_USER_ID to request.userId,
                    RecurrenceReminderWorker.KEY_RECURRENCE_ID to request.recurrenceId,
                    RecurrenceReminderWorker.KEY_DUE_DATE to request.dueDate.toString(),
                    RecurrenceReminderWorker.KEY_LEAD_DAYS to request.leadDays,
                ),
            )
            .addTag(RecurrenceReminderScheduler.sourceTag(request.userId, request.recurrenceId))
            .addTag(RecurrenceReminderScheduler.ownerTag(request.userId))
            .build()
        workManager.enqueueUniqueWork(RecurrenceReminderScheduler.workName(request), ExistingWorkPolicy.REPLACE, work)
    }

    override fun cancel(userId: String, recurrenceId: String) {
        workManager.cancelAllWorkByTag(RecurrenceReminderScheduler.sourceTag(userId, recurrenceId))
        cancelPostedNotification(userId, recurrenceId)
    }

    private fun cancelPostedNotification(userId: String, recurrenceId: String) {
        NotificationManagerCompat.from(context).cancel("recurrence-reminder:$userId:$recurrenceId", 0)
    }

    private fun cancelPostedNotificationIfObsolete(userId: String, recurrenceId: String, dueDate: String) {
        val tag = "recurrence-reminder:$userId:$recurrenceId"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        val active = manager.activeNotifications.firstOrNull { it.tag == tag && it.id == 0 } ?: return
        val postedDueDate = active.notification.extras?.getString(RecurrenceReminderWorker.KEY_DUE_DATE)
        val canonicalPostedDate = postedDueDate?.let { value ->
            runCatching { LocalDate.parse(value).takeIf { it.toString() == value } }.getOrNull()
        }
        if (canonicalPostedDate != null && canonicalPostedDate.toString() != dueDate) {
            NotificationManagerCompat.from(context).cancel(tag, 0)
        }
    }

    private companion object {
        val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
    }
}
