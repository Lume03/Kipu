package com.kipu.app.feature.notifications.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.LocalReminderEventEntity
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxEntity
import com.kipu.app.feature.notifications.data.remote.NotificationDto
import com.kipu.app.feature.notifications.data.remote.NotificationMutationDto
import com.kipu.app.feature.notifications.data.remote.NotificationsApi
import com.kipu.app.feature.notifications.data.remote.NotificationsApiResponse
import com.kipu.app.feature.notifications.data.sync.NotificationSyncScheduler
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationOrigin
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class OfflineFirstNotificationsRepository @Inject constructor(
    private val notificationDao: AppNotificationDao,
    private val localReminderEventDao: LocalReminderEventDao,
    private val outboxDao: NotificationSyncOutboxDao,
    private val api: NotificationsApi,
    private val transactionRunner: DatabaseTransactionRunner,
    private val syncScheduler: NotificationSyncScheduler,
    private val clock: Clock,
    private val systemNotifier: FinancialAlertSystemNotifier,
) : NotificationsRepository {

    override fun observeActive(userId: String): Flow<List<AppNotification>> = combine(
        notificationDao.observeActive(userId),
        localReminderEventDao.observeActive(userId),
    ) { remoteRows, localRows ->
        (remoteRows.map { it.toDomain() } + localRows.map { it.toDomain() })
            .sortedWith(
                compareByDescending<AppNotification> { it.createdAt }
                    .thenBy { it.origin.ordinal }
                    .thenBy { it.id },
            )
    }

    override fun observeUnreadCount(userId: String): Flow<Int> = combine(
        notificationDao.observeUnreadCount(userId),
        localReminderEventDao.observeUnreadCount(userId),
    ) { remoteCount, localCount -> remoteCount + localCount }

    override suspend fun refresh(userId: String): Result<Unit> = runCatching {
        when (val response = api.fetchForUser(userId)) {
            is NotificationsApiResponse.Success -> {
                val pendingIds = outboxDao.getPending(userId).mapTo(mutableSetOf()) { it.notificationId }
                val remoteRows = response.data
                    .asSequence()
                    .filter { it.userId == userId && it.id !in pendingIds && !it.id.startsWith(CAPTURE_REVIEW_PREFIX) }
                    .map { it.toEntity() }
                    .toList()
                val newRows = transactionRunner {
                    val knownIds = notificationDao.getAllIds(userId).toHashSet()
                    notificationDao.upsertAll(remoteRows)
                    remoteRows.filter { it.id !in knownIds }
                }
                // Only alerts first seen on this device and recently emitted reach the phone, so a
                // reinstall or account switch does not replay the whole history as notifications.
                val recentSince = clock.instant().minus(SYSTEM_NOTIFICATION_WINDOW)
                newRows.filter { it.createdAt.isAfter(recentSince) }.forEach(systemNotifier::publish)
            }
            is NotificationsApiResponse.Error -> error("Notification refresh failed (${response.statusCode}): ${response.message.orEmpty()}")
            is NotificationsApiResponse.NetworkFailure -> throw response.exception
        }
    }

    override suspend fun markRead(userId: String, notificationId: String): Result<Unit> = runCatching {
        if (notificationId.startsWith(LOCAL_REMINDER_ID_PREFIX)) {
            check(localReminderEventDao.markRead(userId, notificationId) == 1) {
                "Local reminder is no longer active"
            }
            return@runCatching
        }
        val changed = transactionRunner {
            val notice = notificationDao.getById(userId, notificationId)
                ?: error("Notification is no longer available")
            if (notice.deletedAt != null) error("Notification is no longer active")
            if (notice.id.startsWith(CAPTURE_REVIEW_PREFIX)) {
                if (!notice.isRead) notificationDao.markRead(userId, notificationId)
                return@transactionRunner false
            }
            val previous = outboxDao.getByNotice(userId, notificationId)
            if (!notice.isRead) {
                val updated = notificationDao.markRead(userId, notificationId)
                if (updated == 0) error("Notification is no longer active")
                outboxDao.upsert(previous.coalesced(userId, notificationId, isRead = true, deletedAt = null))
                true
            } else {
                previous != null
            }
        }
        if (changed) scheduleSafely(userId)
    }

    override suspend fun markAllRead(userId: String): Result<Unit> = runCatching {
        val changed = transactionRunner {
            val unread = notificationDao.getAllActiveUnsorted(userId).filterNot { it.isRead || it.deletedAt != null }
            if (unread.isNotEmpty()) {
                notificationDao.markAllRead(userId)
                unread.filterNot { it.id.startsWith(CAPTURE_REVIEW_PREFIX) }.forEach { notice ->
                    val previous = outboxDao.getByNotice(userId, notice.id)
                    outboxDao.upsert(previous.coalesced(userId, notice.id, isRead = true, deletedAt = null))
                }
            }
            localReminderEventDao.markAllRead(userId)
            unread.any { !it.id.startsWith(CAPTURE_REVIEW_PREFIX) }
        }
        if (changed) scheduleSafely(userId)
    }

    override suspend fun dismiss(userId: String, notificationId: String): Result<Unit> = runCatching {
        if (notificationId.startsWith(LOCAL_REMINDER_ID_PREFIX)) {
            check(localReminderEventDao.dismiss(userId, notificationId, clock.instant()) == 1) {
                "Local reminder is no longer active"
            }
            return@runCatching
        }
        val changed = transactionRunner {
            val notice = notificationDao.getById(userId, notificationId)
                ?: error("Notification is no longer available")
            if (notice.id.startsWith(CAPTURE_REVIEW_PREFIX)) {
                if (notice.deletedAt == null) notificationDao.dismiss(userId, notificationId, clock.instant())
                return@transactionRunner false
            }
            val previous = outboxDao.getByNotice(userId, notificationId)
            if (notice.deletedAt != null) {
                previous != null
            } else {
                val deletedAt = clock.instant()
                val updated = notificationDao.dismiss(userId, notificationId, deletedAt)
                if (updated == 0) error("Notification is no longer active")
                outboxDao.upsert(
                    previous.coalesced(
                        userId = userId,
                        notificationId = notificationId,
                        isRead = previous?.isRead,
                        deletedAt = deletedAt,
                    ),
                )
                true
            }
        }
        if (changed) scheduleSafely(userId)
    }

    override suspend fun syncPending(userId: String): Result<Unit> = runCatching {
        for (command in outboxDao.getPending(userId)) {
            if (command.notificationId.startsWith(CAPTURE_REVIEW_PREFIX)) {
                outboxDao.delete(command.operationId)
                continue
            }
            when (val response = api.patchForUser(
                userId = userId,
                notificationId = command.notificationId,
                mutation = NotificationMutationDto(
                    isRead = command.isRead,
                    deletedAt = command.deletedAt?.toString(),
                ),
            )) {
                is NotificationsApiResponse.Success -> outboxDao.delete(command.operationId)
                is NotificationsApiResponse.Error -> {
                    outboxDao.incrementAttempt(command.operationId)
                    error("Notification sync failed (${response.statusCode}): ${response.message.orEmpty()}")
                }
                is NotificationsApiResponse.NetworkFailure -> {
                    outboxDao.incrementAttempt(command.operationId)
                    throw response.exception
                }
            }
        }
    }

    private fun scheduleSafely(userId: String) {
        runCatching { syncScheduler.scheduleSync(userId) }
    }

    private fun NotificationSyncOutboxEntity?.coalesced(
        userId: String,
        notificationId: String,
        isRead: Boolean?,
        deletedAt: Instant?,
    ): NotificationSyncOutboxEntity = NotificationSyncOutboxEntity(
        operationId = this?.operationId ?: UUID.randomUUID().toString(),
        userId = userId,
        notificationId = notificationId,
        isRead = isRead ?: this?.isRead,
        deletedAt = deletedAt ?: this?.deletedAt,
        attemptCount = 0,
        createdAt = this?.createdAt ?: clock.instant(),
    )

    private fun AppNotificationEntity.toDomain() = AppNotification(
        id = id,
        userId = userId,
        title = title,
        body = body,
        notificationType = notificationType,
        referenceEntityType = referenceEntityType,
        referenceEntityId = referenceEntityId,
        isRead = isRead,
        createdAt = createdAt,
        deletedAt = deletedAt,
        eventPayloadJson = eventPayloadJson,
        origin = NotificationOrigin.REMOTE,
    )

    private fun LocalReminderEventEntity.toDomain() = AppNotification(
        id = id,
        userId = userId,
        title = LOCAL_REMINDER_TITLE,
        body = LOCAL_REMINDER_BODY,
        notificationType = eventType,
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = isRead,
        createdAt = createdAt,
        deletedAt = deletedAt,
        eventPayloadJson = "{\"due_date\":\"$dueDate\"}",
        origin = NotificationOrigin.LOCAL_REMINDER,
    )

    private fun NotificationDto.toEntity() = AppNotificationEntity(
        id = id,
        userId = userId,
        title = title,
        body = body,
        notificationType = notificationType,
        referenceEntityType = referenceEntityType,
        referenceEntityId = referenceEntityId,
        isRead = isRead,
        createdAt = Instant.parse(createdAt),
        deletedAt = deletedAt?.let(Instant::parse),
        eventPayloadJson = eventPayload?.toString() ?: "{}",
    )

    private companion object {
        const val CAPTURE_REVIEW_PREFIX = "CAPTURE_REVIEW:"
        const val LOCAL_REMINDER_ID_PREFIX = "local-reminder:v1:"
        const val LOCAL_REMINDER_TITLE = "Recordatorio de vencimiento"
        const val LOCAL_REMINDER_BODY = "Recordatorio local de vencimiento."
        val SYSTEM_NOTIFICATION_WINDOW: Duration = Duration.ofHours(24)
    }
}
