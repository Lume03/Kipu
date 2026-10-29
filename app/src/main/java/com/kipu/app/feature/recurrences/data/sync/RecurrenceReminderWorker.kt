package com.kipu.app.feature.recurrences.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.LocalReminderEventEntity
import com.kipu.app.feature.notifications.data.local.ReminderScheduleDao
import com.kipu.app.feature.notifications.data.local.ReminderScheduleEntity
import com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator
import com.kipu.app.feature.capture.domain.ExpectedPaymentStore
import com.kipu.app.feature.capture.domain.ExpectedPaymentStatus
import kotlinx.coroutines.flow.first
import com.kipu.app.feature.recurrences.data.local.RecurrenceDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@HiltWorker
class RecurrenceReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurrenceDao: RecurrenceDao,
    private val sessionCoordinator: SessionCoordinator,
    private val permissionChecker: DebtNotificationPermissionChecker,
    private val reminderScheduleDao: ReminderScheduleDao,
    private val localReminderEventDao: LocalReminderEventDao,
    private val coordinator: ReminderReconciliationCoordinator,
    private val expectedPayments: ExpectedPaymentStore,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = coordinator.exclusive { runReminder() }

    private suspend fun runReminder(): Result {
        val userId = inputData.getString(KEY_USER_ID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        val recurrenceId = inputData.getString(KEY_RECURRENCE_ID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        val dueDate = inputData.getString(KEY_DUE_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        val leadDays = inputData.getInt(KEY_LEAD_DAYS, -1)
        if (sessionCoordinator.currentOwner?.verifiedUserId != userId) return Result.success()

        val recurrence = recurrenceDao.get(userId, recurrenceId) ?: return Result.success()
        if (recurrence.status != "ACTIVE" || !recurrence.isActive || recurrence.deletedAt != null ||
            recurrence.nextDueDate != dueDate.toString() || recurrence.reminderLeadDays != leadDays ||
            leadDays !in 1..30 || recurrence.endDate?.let { dueDate > LocalDate.parse(it) } == true
        ) return Result.success()

        if (expectedPayments.observeByRecurrence(userId, recurrenceId).first().any {
                it.dueDate == dueDate && it.status == ExpectedPaymentStatus.ATTENDED
            }) return Result.success()

        val now = Instant.now()
        reminderScheduleDao.cancelReplacedSchedules(userId, recurrenceId, dueDate.toString(), now)
        reminderScheduleDao.upsert(
            ReminderScheduleEntity(
                userId = userId,
                sourceId = recurrenceId,
                dueDate = dueDate.toString(),
                leadDays = leadDays,
                updatedAt = now,
            ),
        )
        reminderScheduleDao.markDelivered(
            userId, recurrenceId, ReminderScheduleEntity.BILLING_DUE, dueDate.toString(), now,
        )
        localReminderEventDao.tombstoneReplacedEvents(userId, recurrenceId, dueDate.toString(), now)
        localReminderEventDao.upsert(
            LocalReminderEventEntity(
                id = localReminderId(userId, recurrenceId, dueDate.toString()),
                userId = userId,
                sourceId = recurrenceId,
                dueDate = dueDate.toString(),
                createdAt = now,
            ),
        )
        if (sessionCoordinator.currentOwner?.verifiedUserId != userId || !permissionChecker.canPostDebtReminder()) {
            return Result.success()
        }

        createChannel()
        try {
            NotificationManagerCompat.from(applicationContext).notify(
                "recurrence-reminder:$userId:$recurrenceId",
                0,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle("Recordatorio de recurrencia")
                    .setContentText("Tienes un movimiento recurrente planificado para el $dueDate.")
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .addExtras(android.os.Bundle().apply { putString(KEY_DUE_DATE, dueDate.toString()) })
                    .build(),
            )
        } catch (_: SecurityException) {
            // Permission can be revoked after the check; local persistence is already complete.
        }
        return Result.success()
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recordatorios de recurrencias", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun localReminderId(userId: String, sourceId: String, dueDate: String): String {
        fun encode(value: String): ByteArray {
            val bytes = value.toByteArray(Charsets.UTF_8)
            return ByteBuffer.allocate(Int.SIZE_BYTES + bytes.size).putInt(bytes.size).put(bytes).array()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(userId, sourceId, ReminderScheduleEntity.BILLING_DUE, dueDate)
            .map(::canonicalTuplePart)
            .forEach { digest.update(encode(it)) }
        return "local-reminder:v1:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun canonicalTuplePart(value: String): String = runCatching { UUID.fromString(value).toString() }.getOrDefault(value)

    companion object {
        const val KEY_USER_ID = "recurrence_reminder_user_id"
        const val KEY_RECURRENCE_ID = "recurrence_reminder_id"
        const val KEY_DUE_DATE = "recurrence_reminder_due_date"
        const val KEY_LEAD_DAYS = "recurrence_reminder_lead_days"
        const val CHANNEL_ID = "recurrence-reminders"
    }
}
