package com.kipu.app.feature.notifications.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kipu.app.MainActivity
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presents a financial alert already stored in the center (budget threshold or card utilization) as an
 * Android notification. The center row stays the source of truth: without POST_NOTIFICATIONS the alert
 * is still listed in-app, and the Android notification is tagged by the row id so a repeat never duplicates.
 */
interface FinancialAlertSystemNotifier {
    fun publish(notice: AppNotificationEntity)

    object None : FinancialAlertSystemNotifier {
        override fun publish(notice: AppNotificationEntity) = Unit
    }

    companion object {
        const val CENTER_DEEP_LINK = "kipu://notifications/center"
        private val PUBLISHED_TYPES = setOf(
            "BUDGET_ALERT",
            "CREDIT_THRESHOLD",
            "CREDIT_UTILIZATION_THRESHOLD_CROSSED",
        )

        fun isPublishable(notice: AppNotificationEntity): Boolean =
            !notice.isRead && notice.deletedAt == null &&
                notice.notificationType.trim().uppercase(Locale.ROOT) in PUBLISHED_TYPES
    }
}

@Singleton
class AndroidFinancialAlertSystemNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionCoordinator: SessionCoordinator,
) : FinancialAlertSystemNotifier {
    override fun publish(notice: AppNotificationEntity) {
        if (!FinancialAlertSystemNotifier.isPublishable(notice)) return
        if (sessionCoordinator.currentOwner?.verifiedUserId != notice.userId || !canPost()) return
        createChannel()
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(FinancialAlertSystemNotifier.CENTER_DEEP_LINK)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            "${notice.userId}:${notice.id}".hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.kipu.app.R.drawable.ic_stat_kipu).setColor(KIPU_NOTIFICATION_COLOR)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify("financial-alert:${notice.userId}:${notice.id}", 0, notification)
        } catch (_: SecurityException) {
            // The permission can be revoked after the check; the alert remains in the center.
        }
    }

    private fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alertas financieras", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos de presupuestos y uso de tarjetas de crédito."
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "financial-alerts"
    }
}
