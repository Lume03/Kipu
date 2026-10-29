package com.kipu.app.feature.capture.data

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
import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

interface CaptureReviewSystemNotifier {
    fun publish(candidate: CaptureCandidateRecord, notice: AppNotificationEntity)
    fun cancel(ownerId: String, candidateId: String)
}

class CaptureReviewNotifier @Inject constructor(
    private val dao: AppNotificationDao,
    private val candidates: CaptureCandidateStore,
    private val gate: CaptureAccessGate,
    private val system: CaptureReviewSystemNotifier,
    private val clock: Clock,
    private val transactions: com.kipu.app.core.database.DatabaseTransactionRunner,
) : CaptureCandidatePostProcessor, CaptureCandidateResolvedListener, CaptureCandidateDiscardedListener {
    override val followupFlag = CaptureFollowup.REVIEW_NOTICE
    override val order = 100
    override suspend fun process(candidate: CaptureCandidateRecord) {
        transactions { publishPending(candidate) }
    }
    private suspend fun publishPending(candidate: CaptureCandidateRecord) {
        if (candidate.status != CaptureCandidateStatus.PENDING || gate.evaluate(candidate.ownerId) !is CaptureAccessDecision.Allowed) return
        if (candidates.get(candidate.ownerId, candidate.id)?.status != CaptureCandidateStatus.PENDING) return
        val notice = AppNotificationEntity(
            id = key(candidate.ownerId, candidate.id), userId = candidate.ownerId,
            title = "Captura por revisar", body = "Tienes un movimiento detectado por revisar (${candidate.provider.name}).",
            notificationType = "CAPTURE_REVIEW", referenceEntityType = "CAPTURE_CANDIDATE",
            referenceEntityId = candidate.id, isRead = false, createdAt = Instant.ofEpochMilli(clock.millis()),
            deletedAt = null, eventPayloadJson = "{}",
        )
        val inserted = dao.insertIfAbsent(notice) != -1L
        val current = candidates.get(candidate.ownerId, candidate.id) ?: return
        if (inserted || current.followupsPending and CaptureFollowup.PUBLISH_REVIEW != 0) {
            if (current.status == CaptureCandidateStatus.PENDING) {
                // A retry replaces the SAME Android notification; it never creates a second notice.
                val existing = if (inserted) notice else dao.getById(candidate.ownerId, notice.id) ?: notice
                if (!existing.isRead && existing.deletedAt == null) system.publish(candidate, existing)
                candidates.completeFollowup(candidate.ownerId, candidate.id, CaptureFollowup.PUBLISH_REVIEW)
            } else onResolved(candidate, "")
        }
    }
    override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) {
        dao.markRead(candidate.ownerId, key(candidate.ownerId, candidate.id))
        system.cancel(candidate.ownerId, candidate.id)
    }
    override suspend fun onDiscarded(candidate: CaptureCandidateRecord) = onResolved(candidate, "")
    companion object { fun key(ownerId: String, candidateId: String) = "CAPTURE_REVIEW:$ownerId:$candidateId" }
}

class AndroidCaptureReviewSystemNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: CaptureAccessGate,
) : CaptureReviewSystemNotifier {
    override fun publish(candidate: CaptureCandidateRecord, notice: AppNotificationEntity) {
        if (gate.currentOwnerId() != candidate.ownerId || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Capturas por revisar", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("kipu://capture/candidate/${candidate.id}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, notice.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Kipu").setContentText("Tienes un aviso pendiente").build()
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notice.title).setContentText(notice.body).setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
        try { NotificationManagerCompat.from(context).notify(notice.id, 0, notification) } catch (_: SecurityException) { }
    }
    override fun cancel(ownerId: String, candidateId: String) {
        NotificationManagerCompat.from(context).cancel(CaptureReviewNotifier.key(ownerId, candidateId), 0)
    }
    private companion object { const val CHANNEL = "capture-review" }
}
