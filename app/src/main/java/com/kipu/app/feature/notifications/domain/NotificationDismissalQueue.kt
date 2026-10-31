package com.kipu.app.feature.notifications.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class NotificationDismissalPhase { PENDING, COMMITTING }

data class PendingNotificationDismissal(
    val token: String,
    val userId: String,
    val notificationId: String,
    val phase: NotificationDismissalPhase = NotificationDismissalPhase.PENDING,
)

data class NotificationDismissalFailure(val userId: String, val notificationId: String)

/**
 * Shared optimistic overlay for the center and unread badge. The singleton owns commit jobs so
 * closing a screen cannot cancel a Room/outbox write. Pending dismissals remain undoable for 5s.
 */
@Singleton
class NotificationDismissalQueue internal constructor(
    private val repository: NotificationsRepository,
    private val mutationScope: CoroutineScope,
) {
    @Inject constructor(repository: NotificationsRepository) : this(
        repository, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    private val _pendingDismissals = MutableStateFlow<Map<String, PendingNotificationDismissal>>(emptyMap())
    val pendingDismissals = _pendingDismissals.asStateFlow()
    private val expiryJobs = mutableMapOf<String, Job>()
    private val failureChannel = Channel<NotificationDismissalFailure>(Channel.BUFFERED)
    val failures = failureChannel.receiveAsFlow()

    fun dismiss(notification: AppNotification) {
        if (notification.deletedAt != null || _pendingDismissals.value.values.any {
                it.userId == notification.userId && it.notificationId == notification.id
            }) return
        commitPending(notification.userId)
        val pending = PendingNotificationDismissal(UUID.randomUUID().toString(), notification.userId, notification.id)
        _pendingDismissals.update { it + (pending.token to pending) }
        expiryJobs[pending.token] = mutationScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            commit(pending.token)
        }
    }

    fun undo(token: String, userId: String) {
        val pending = _pendingDismissals.value[token] ?: return
        if (pending.userId != userId || pending.phase != NotificationDismissalPhase.PENDING) return
        expiryJobs.remove(token)?.cancel()
        _pendingDismissals.update { it - token }
    }

    fun commitPending(userId: String) {
        _pendingDismissals.value.values.filter {
            it.userId == userId && it.phase == NotificationDismissalPhase.PENDING
        }.forEach { commit(it.token) }
    }

    fun commit(token: String) {
        val pending = _pendingDismissals.value[token] ?: return
        if (pending.phase != NotificationDismissalPhase.PENDING) return
        _pendingDismissals.update { it + (token to pending.copy(phase = NotificationDismissalPhase.COMMITTING)) }
        expiryJobs.remove(token)?.cancel()
        mutationScope.launch {
            try {
                repository.dismiss(pending.userId, pending.notificationId).getOrThrow()
                // Retain the overlay until Room emits the logical deletion, preventing a one-frame
                // resurrection and a double badge adjustment during the handoff to persisted state.
                repository.observeActive(pending.userId).first { rows -> rows.none {
                    it.userId == pending.userId && it.id == pending.notificationId && it.deletedAt == null
                } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failureChannel.trySend(NotificationDismissalFailure(pending.userId, pending.notificationId))
            } finally {
                _pendingDismissals.update { it - token }
            }
        }
    }

    /** Count the same visible unread rows, rather than subtracting from two independently timed queries. */
    fun observeUnreadCount(userId: String): Flow<Int> = combine(
        repository.observeActive(userId), pendingDismissals,
    ) { rows, pending ->
        val hidden = pending.values.filter { it.userId == userId }.mapTo(hashSetOf()) { it.notificationId }
        rows.count { it.userId == userId && it.deletedAt == null && !it.isRead && it.id !in hidden }
    }

    companion object {
        const val UNDO_WINDOW_MILLIS = 5_000L
    }
}
