package com.kipu.app.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.NotificationDismissalQueue
import com.kipu.app.feature.notifications.domain.NotificationDismissalPhase
import com.kipu.app.feature.notifications.domain.PendingNotificationDismissal
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationCategory
import com.kipu.app.feature.notifications.domain.NotificationDestination
import com.kipu.app.feature.notifications.domain.NotificationDestinationResolver
import com.kipu.app.feature.notifications.domain.NotificationReferenceType
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import com.kipu.app.feature.notifications.domain.UNAVAILABLE_DESTINATION_MESSAGE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotificationFilter(val label: String) {
    ALL("Todos"),
    BUDGETS("Presupuestos"),
    GOALS("Metas"),
    ALERTS("Alertas"),
    REMINDERS("Recordatorios"),
}

data class NotificationsUiState(
    val activeUserId: String? = null,
    val notifications: List<AppNotification> = emptyList(),
    val visibleNotifications: List<AppNotification> = emptyList(),
    val selectedFilter: NotificationFilter = NotificationFilter.ALL,
    val isLoading: Boolean = false,
    val pendingDismissal: PendingNotificationDismissal? = null,
    val emptyMessage: String = "Todo al día. No tienes avisos pendientes",
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val hasLoadError: Boolean = false,
)

private data class NotificationSourceState(
    val userId: String?,
    val rows: List<AppNotification> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

private data class NotificationRefreshState(
    val userId: String? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

sealed interface NotificationsUiEvent {
    data class OpenCard(val cardId: String) : NotificationsUiEvent
    data class OpenCapture(val candidateId: String) : NotificationsUiEvent
    data class OpenBudget(val budgetId: String) : NotificationsUiEvent
    data class OpenDebt(val debtId: String) : NotificationsUiEvent
    data class OpenGoal(val goalId: String) : NotificationsUiEvent
    data class ShowMessage(val message: String) : NotificationsUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val destinationResolver: NotificationDestinationResolver,
    private val dismissalQueue: NotificationDismissalQueue,
) : ViewModel() {
    // Preserve source compatibility for existing direct ViewModel callers and tests.
    constructor(repository: NotificationsRepository, sessionCoordinator: SessionCoordinator,
        destinationResolver: NotificationDestinationResolver) : this(
        repository, sessionCoordinator, destinationResolver, NotificationDismissalQueue(repository),
    )

    val pendingDismissals = dismissalQueue.pendingDismissals
    private var observedUserId: String? = null

    private val activeUserId: Flow<String?> = sessionCoordinator.localAccess
        .map { access -> (access as? LocalAccess.Available)?.userId }
        .distinctUntilChanged()

    private val retryRequests = MutableStateFlow(0L)
    private val refreshState = MutableStateFlow(NotificationRefreshState())
    private val sourceNotifications = activeUserId.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(NotificationSourceState(null))
        } else {
            retryRequests.flatMapLatest {
                flow {
                    var latest = NotificationSourceState(userId, loading = true)
                    emit(latest)
                    try {
                        repository.observeActive(userId).collect { rows ->
                            latest = NotificationSourceState(userId, rows)
                            emit(latest)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emit(latest.copy(loading = false, error = "No pudimos cargar tus notificaciones. Inténtalo nuevamente."))
                    }
                }
            }
        }
    }

    private val selectedFilter = kotlinx.coroutines.flow.MutableStateFlow(NotificationFilter.ALL)
    private val eventChannel = Channel<NotificationsUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val state: StateFlow<NotificationsUiState> = combine(
        activeUserId,
        sourceNotifications,
        selectedFilter,
        pendingDismissals,
        refreshState,
    ) { userId, source, filter, pending, refresh ->
        val currentSource = source.takeIf { it.userId == userId }
        val currentRefresh = refresh.takeIf { it.userId == userId }
        val rows = currentSource?.rows.orEmpty()
        val hiddenIds = pending.values.filter { it.userId == userId }.mapTo(hashSetOf()) { it.notificationId }
        val ordered = rows.asSequence()
            .filter { it.userId == userId && it.deletedAt == null && it.id !in hiddenIds }
            .sortedByDescending { it.createdAt }
            .toList()
        val visible = when (filter) {
            NotificationFilter.ALL -> ordered
            NotificationFilter.BUDGETS -> ordered.filter { it.referenceType == NotificationReferenceType.BUDGET }
            NotificationFilter.GOALS -> ordered.filter { it.referenceType == NotificationReferenceType.GOAL }
            NotificationFilter.ALERTS -> ordered.filter { it.category == NotificationCategory.ALERT }
            NotificationFilter.REMINDERS -> ordered.filter { it.category == NotificationCategory.REMINDER }
        }
        NotificationsUiState(
            activeUserId = userId,
            notifications = ordered,
            visibleNotifications = visible,
            selectedFilter = filter,
            pendingDismissal = pending.values.firstOrNull { it.userId == userId && it.phase == NotificationDismissalPhase.PENDING },
            isLoading = userId != null && (currentSource == null || currentSource.loading) && ordered.isEmpty(),
            isRefreshing = currentRefresh?.refreshing == true,
            errorMessage = currentSource?.error ?: currentRefresh?.error,
            hasLoadError = currentSource?.error != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState())

    val unreadCount: StateFlow<Int> = state
        .map { current -> current.notifications.count { !it.isRead } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            activeUserId.collect { userId ->
                observedUserId?.takeIf { it != userId }?.let(dismissalQueue::commitPending)
                observedUserId = userId
            }
        }
        viewModelScope.launch {
            dismissalQueue.failures.collect { failure ->
                if (failure.userId == currentUserId()) eventChannel.send(
                    NotificationsUiEvent.ShowMessage("No se pudo eliminar el aviso. Inténtalo nuevamente."),
                )
            }
        }
    }

    fun setFilter(filter: NotificationFilter) {
        selectedFilter.value = filter
    }

    fun refresh() {
        val userId = currentUserId() ?: return
        if (refreshState.value.userId == userId && refreshState.value.refreshing) return
        if (state.value.hasLoadError) retryRequests.value += 1
        refreshState.value = NotificationRefreshState(userId, refreshing = true)
        viewModelScope.launch {
            try {
                val result = repository.refresh(userId)
                if (currentUserId() == userId) {
                    refreshState.value = NotificationRefreshState(
                        userId,
                        error = if (result.isFailure) "No pudimos actualizar tus notificaciones. Tus avisos guardados siguen disponibles." else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (currentUserId() == userId) {
                    refreshState.value = NotificationRefreshState(
                        userId,
                        error = "No pudimos actualizar tus notificaciones. Tus avisos guardados siguen disponibles.",
                    )
                }
            } finally {
                if (refreshState.value.userId == userId && refreshState.value.refreshing) {
                    refreshState.value = refreshState.value.copy(refreshing = false)
                }
            }
        }
    }

    fun markRead(notificationId: String) {
        mutate { repository.markRead(it, notificationId) }
    }

    fun markAllRead() {
        mutate(repository::markAllRead)
    }

    fun dismiss(notificationId: String) {
        val userId = currentUserId() ?: return
        val notification = state.value.notifications.firstOrNull { it.id == notificationId && it.userId == userId } ?: return
        dismissalQueue.dismiss(notification)
    }

    fun undoDismissal(token: String) {
        currentUserId()?.let { dismissalQueue.undo(token, it) }
    }

    fun commitDismissal(token: String) {
        val userId = currentUserId() ?: return
        if (pendingDismissals.value[token]?.userId == userId) dismissalQueue.commit(token)
    }

    fun commitPendingDismissals() {
        (observedUserId ?: currentUserId())?.let(dismissalQueue::commitPending)
    }

    override fun onCleared() {
        commitPendingDismissals()
        super.onCleared()
    }

    /**
     * Dismisses every already-read notice in the current filter through the same synced logical delete as the
     * per-row X. Unread notices are never removed here; they must be marked read first.
     */
    fun dismissRead() {
        commitPendingDismissals()
        val readIds = state.value.visibleNotifications.filter { it.isRead }.map { it.id }
        if (readIds.isEmpty()) return
        mutate { userId ->
            val failures = readIds.map { id -> repository.dismiss(userId, id) }.filter { it.isFailure }
            failures.firstOrNull() ?: Result.success(Unit)
        }
    }

    fun openNotification(notification: AppNotification) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            when (val destination = destinationResolver.resolve(userId, notification)) {
                is NotificationDestination.CardDetail -> eventChannel.send(NotificationsUiEvent.OpenCard(destination.cardId))
                is NotificationDestination.CaptureCandidate -> eventChannel.send(NotificationsUiEvent.OpenCapture(destination.candidateId))
                is NotificationDestination.BudgetDetail -> eventChannel.send(NotificationsUiEvent.OpenBudget(destination.budgetId))
                is NotificationDestination.DebtDetail -> eventChannel.send(NotificationsUiEvent.OpenDebt(destination.debtId))
                is NotificationDestination.GoalDetail -> eventChannel.send(NotificationsUiEvent.OpenGoal(destination.goalId))
                is NotificationDestination.Unavailable -> eventChannel.send(
                    NotificationsUiEvent.ShowMessage(destination.message.ifBlank { UNAVAILABLE_DESTINATION_MESSAGE }),
                )
            }
        }
    }

    private fun mutate(action: suspend (String) -> Result<Unit>) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            action(userId).onFailure {
                eventChannel.send(NotificationsUiEvent.ShowMessage("No se pudo actualizar el aviso. Inténtalo nuevamente."))
            }
        }
    }

    private fun currentUserId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
}
