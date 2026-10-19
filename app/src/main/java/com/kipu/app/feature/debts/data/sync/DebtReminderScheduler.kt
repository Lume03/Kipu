package com.kipu.app.feature.debts.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.notifications.data.local.ReminderEventReconciler
import com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class DebtReminderRequest(
    val userId: String,
    val debtId: String,
    val installmentId: String,
    val dueDate: LocalDate,
    val leadDays: Int,
    val counterpartyName: String,
    val installmentNumber: Int,
    val remainingPrincipalMinor: Long,
    val currencyCode: String,
    val scheduledAt: Instant = Instant.now(),
)

fun interface DebtNotificationPermissionChecker {
    fun canPostDebtReminder(): Boolean
}

interface DebtReminderScheduler {
    fun schedule(request: DebtReminderRequest)
    fun cancel(userId: String, debtId: String, installmentId: String)
    fun cancelDebt(userId: String, debtId: String)
    fun cancelUser(userId: String)

    companion object {
        fun uniqueWorkName(request: DebtReminderRequest): String =
            "debt-reminder:${request.userId}:${request.debtId}:${request.installmentId}"

        fun notificationTag(request: DebtReminderRequest): String =
            "debt-reminder:${request.userId}:${request.debtId}:${request.installmentId}"

        fun dueDateTag(request: DebtReminderRequest): String =
            "debt-reminder-due:${request.userId}:${request.debtId}:${request.installmentId}:${request.dueDate}"

        fun debtTag(userId: String, debtId: String): String = "debt-reminder-debt:$userId:$debtId"
        fun userTag(userId: String): String = "debt-reminder-user:$userId"
    }
}

object NoOpDebtReminderScheduler : DebtReminderScheduler {
    override fun schedule(request: DebtReminderRequest) = Unit
    override fun cancel(userId: String, debtId: String, installmentId: String) = Unit
    override fun cancelDebt(userId: String, debtId: String) = Unit
    override fun cancelUser(userId: String) = Unit
}

@Singleton
class AndroidDebtNotificationPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : DebtNotificationPermissionChecker {
    override fun canPostDebtReminder(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

@Singleton
class WorkManagerDebtReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val permissionChecker: DebtNotificationPermissionChecker,
) : DebtReminderScheduler {
    override fun schedule(request: DebtReminderRequest) {
        if (request.userId.isBlank() || request.debtId.isBlank() || request.installmentId.isBlank() ||
            request.leadDays !in 0..365 || request.remainingPrincipalMinor <= 0L || request.leadDays == 0
        ) {
            cancel(request.userId, request.debtId, request.installmentId)
            return
        }

        cancelPostedNotificationIfObsolete(DebtReminderScheduler.notificationTag(request), request.dueDate.toString())

        val notificationAt = request.dueDate.minusDays(request.leadDays.toLong())
            .atStartOfDay(LIMA_ZONE).toInstant().toEpochMilli()
        val delayMillis = (notificationAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val work = OneTimeWorkRequestBuilder<DebtReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    DebtReminderWorker.KEY_USER_ID to request.userId,
                    DebtReminderWorker.KEY_DEBT_ID to request.debtId,
                    DebtReminderWorker.KEY_INSTALLMENT_ID to request.installmentId,
                    DebtReminderWorker.KEY_DUE_DATE to request.dueDate.toString(),
                    DebtReminderWorker.KEY_LEAD_DAYS to request.leadDays,
                    DebtReminderWorker.KEY_INSTALLMENT_NUMBER to request.installmentNumber,
                    DebtReminderWorker.KEY_COUNTERPARTY_NAME to request.counterpartyName,
                    DebtReminderWorker.KEY_REMAINING_MINOR to request.remainingPrincipalMinor,
                    DebtReminderWorker.KEY_CURRENCY_CODE to request.currencyCode,
                ),
            )
            .addTag(DebtReminderScheduler.userTag(request.userId))
            .addTag(DebtReminderScheduler.debtTag(request.userId, request.debtId))
            .addTag(DebtReminderScheduler.dueDateTag(request))
            .build()
        workManager.enqueueUniqueWork(
            DebtReminderScheduler.uniqueWorkName(request),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    override fun cancel(userId: String, debtId: String, installmentId: String) {
        workManager.cancelUniqueWork("debt-reminder:$userId:$debtId:$installmentId")
        cancelPostedNotification("debt-reminder:$userId:$debtId:$installmentId")
    }

    override fun cancelDebt(userId: String, debtId: String) {
        workManager.cancelAllWorkByTag(DebtReminderScheduler.debtTag(userId, debtId))
        cancelPostedNotifications("debt-reminder:$userId:$debtId:")
    }

    override fun cancelUser(userId: String) {
        workManager.cancelAllWorkByTag(DebtReminderScheduler.userTag(userId))
        cancelPostedNotifications("debt-reminder:$userId:")
    }

    private fun cancelPostedNotifications(tagPrefix: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        manager.activeNotifications.filter { it.tag?.startsWith(tagPrefix) == true }.forEach {
            NotificationManagerCompat.from(context).cancel(it.tag, it.id)
        }
    }

    private fun cancelPostedNotification(tag: String) {
        NotificationManagerCompat.from(context).cancel(tag, 0)
    }

    private fun cancelPostedNotificationIfObsolete(tag: String, dueDate: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        val active = manager.activeNotifications.firstOrNull { it.tag == tag && it.id == 0 } ?: return
        val postedDueDate = active.notification.extras?.getString(DebtReminderWorker.KEY_DUE_DATE)
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

interface DebtReminderReconciler {
    suspend fun reconcile(userId: String, debtId: String? = null)
}

object NoOpDebtReminderReconciler : DebtReminderReconciler {
    override suspend fun reconcile(userId: String, debtId: String?) = Unit
}

@Singleton
class LocalDebtReminderReconciler @Inject constructor(
    private val debtDao: DebtDao,
    private val scheduler: DebtReminderScheduler,
    private val reminderEventReconciler: ReminderEventReconciler,
    private val coordinator: ReminderReconciliationCoordinator,
) : DebtReminderReconciler {
    override suspend fun reconcile(userId: String, debtId: String?) {
        if (userId.isBlank()) return
        coordinator.exclusive {
            val summaries = debtDao.observeSummaries(userId).first()
            summaries.asSequence()
                .filter { debtId == null || it.debtId == debtId }
                .forEach { debt ->
                    val installments = debtDao.getInstallments(userId, debt.debtId)
                    val leadDays = debt.reminderLeadDays ?: 0
                    if (debt.status != DebtLifecycleStatus.ACTIVE.name || leadDays !in 1..365) {
                        installments.forEach {
                            scheduler.cancel(userId, debt.debtId, it.id)
                            reminderEventReconciler.invalidateSource(userId, it.id, java.time.Instant.now())
                        }
                    } else {
                        installments.forEach { installment ->
                            val remaining = if (installment.status == "PENDING" || installment.status == "PARTIAL") {
                                debtDao.getInstallmentRemaining(userId, debt.debtId, installment.id)
                            } else 0L
                            if (remaining > 0L) {
                                reminderEventReconciler.retainCurrentDate(userId, installment.id, installment.dueDate, java.time.Instant.now())
                                scheduler.schedule(
                                    DebtReminderRequest(
                                        userId = userId,
                                        debtId = debt.debtId,
                                        installmentId = installment.id,
                                        dueDate = LocalDate.parse(installment.dueDate),
                                        leadDays = leadDays,
                                        counterpartyName = debt.counterpartyName,
                                        installmentNumber = installment.installmentNumber,
                                        remainingPrincipalMinor = remaining,
                                        currencyCode = debt.currencyCode,
                                    ),
                                )
                            } else {
                                scheduler.cancel(userId, debt.debtId, installment.id)
                                reminderEventReconciler.invalidateSource(userId, installment.id, java.time.Instant.now())
                            }
                        }
                    }
                }
        }
    }
}
