package com.kipu.app.feature.notifications.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import com.kipu.app.ui.component.FilterOption
import com.kipu.app.ui.component.OptionFilterButton
import com.kipu.app.ui.component.OptionFilterSheet
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationCategory
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    state: NotificationsUiState,
    unreadCount: Int,
    onFilterSelected: (NotificationFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkRead: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onOpenNotification: (AppNotification) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissRead: () -> Unit = {},
    now: Instant? = null,
    reducedMotion: Boolean? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val disableMotion = reducedMotion ?: rememberReducedMotionEnabled()
    val colors = rememberCalmEmeraldColors()
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val readVisibleCount = state.visibleNotifications.count { it.isRead }
    var confirmDismissRead by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val referenceNow = now ?: remember(state.visibleNotifications) { Instant.now() }
    if (showFilterSheet) {
        NotificationFilterSheet(
            selected = state.selectedFilter,
            onSelect = { filter ->
                showFilterSheet = false
                onFilterSelected(filter)
            },
            onDismiss = { showFilterSheet = false },
        )
    }
    if (confirmDismissRead && readVisibleCount > 0) {
        DismissReadDialog(
            readCount = readVisibleCount,
            onConfirm = {
                confirmDismissRead = false
                onDismissRead()
            },
            onCancel = { confirmDismissRead = false },
        )
    }
    AnimatedVisibility(
        visible = true,
        enter = if (disableMotion) EnterTransition.None else slideInVertically { -it / 12 } + fadeIn(),
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(color = colors.surfaceCard, contentColor = colors.primaryText) {
                    Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                            Text(
                                "Notificaciones",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!largeText) {
                                HeaderActions(
                                    showMarkAllRead = unreadCount > 0,
                                    showDismissRead = readVisibleCount > 0,
                                    onMarkAllRead = onMarkAllRead,
                                    onDismissRead = { confirmDismissRead = true },
                                )
                            }
                        }
                        if (largeText && (unreadCount > 0 || readVisibleCount > 0)) {
                            Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                HeaderActions(
                                    showMarkAllRead = unreadCount > 0,
                                    showDismissRead = readVisibleCount > 0,
                                    onMarkAllRead = onMarkAllRead,
                                    onDismissRead = { confirmDismissRead = true },
                                )
                            }
                        }
                    }
                }
            },
            containerColor = colors.background,
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NotificationFilterButton(
                    selected = state.selectedFilter,
                    onClick = { showFilterSheet = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    state.visibleNotifications.isEmpty() -> {
                        val message = if (state.notifications.isEmpty()) state.emptyMessage else "No hay avisos en esta categoría."
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp).testTag("notification-empty"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(message, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("notification-list"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.visibleNotifications, key = { it.id }) { notification ->
                                // animateItem also fades the row out when it is dismissed.
                                val rowModifier = if (disableMotion) Modifier else Modifier.animateItem()
                                NotificationRow(
                                    notification = notification,
                                    now = referenceNow,
                                    largeText = largeText,
                                    modifier = rowModifier,
                                    onOpen = { onOpenNotification(notification) },
                                    onMarkRead = { onMarkRead(notification.id) },
                                    onDismiss = { onDismiss(notification.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActions(
    showMarkAllRead: Boolean,
    showDismissRead: Boolean,
    onMarkAllRead: () -> Unit,
    onDismissRead: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    if (showMarkAllRead) {
        TextButton(onClick = onMarkAllRead, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Marcar todas como leídas", color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
        }
    }
    if (showDismissRead) {
        IconButton(onClick = onDismissRead, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar leídas", tint = colors.secondaryMuted)
        }
    }
}

@Composable
private fun notificationFilterOptions(): List<FilterOption<NotificationFilter>> {
    val colors = rememberCalmEmeraldColors()
    return NotificationFilter.entries.map { filter ->
        val (icon, tint) = when (filter) {
            NotificationFilter.ALL -> Icons.Default.NotificationsNone to colors.primaryDeep
            NotificationFilter.BUDGETS -> Icons.Default.PieChart to colors.warningText
            NotificationFilter.GOALS -> Icons.Default.Flag to colors.incomeText
            NotificationFilter.ALERTS -> Icons.Default.WarningAmber to colors.expenseText
            NotificationFilter.REMINDERS -> Icons.Default.Schedule to colors.warningText
        }
        FilterOption(filter, filter.label, icon, tint, "notification-filter-${filter.name.lowercase()}")
    }
}

@Composable
private fun NotificationFilterButton(selected: NotificationFilter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val option = notificationFilterOptions().first { it.value == selected }
    OptionFilterButton(
        label = selected.label,
        active = selected != NotificationFilter.ALL,
        description = "Filtrar notificaciones: ${selected.label}",
        icon = if (selected == NotificationFilter.ALL) Icons.Default.FilterList else option.icon,
        onClick = onClick,
        modifier = modifier.testTag("notification-filter-button"),
    )
}

@Composable
private fun NotificationFilterSheet(selected: NotificationFilter, onSelect: (NotificationFilter) -> Unit, onDismiss: () -> Unit) {
    OptionFilterSheet("Filtrar notificaciones", notificationFilterOptions(), selected, onSelect, onDismiss)
}

@Composable
private fun DismissReadDialog(
    readCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val noun = if (readCount == 1) "1 notificación leída" else "$readCount notificaciones leídas"
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.surfaceCard,
        title = { Text("¿Borrar leídas?", color = colors.primaryText, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Se eliminarán $noun de esta vista. Las no leídas se mantienen.",
                color = colors.secondaryMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Borrar", color = colors.expenseText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar", color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationRow(
    notification: AppNotification,
    now: Instant,
    largeText: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    // Budget payload thresholdBps records a crossed threshold, not current consumption.
    // Keep budget alerts cautionary rather than claiming every threshold means overspending.
    val eventType = notification.notificationType.trim().uppercase(Locale.ROOT)
    val (accent, iconBackground, iconTint) = when (eventType) {
        "CREDIT_THRESHOLD", "CREDIT_UTILIZATION_THRESHOLD_CROSSED" ->
            Triple(colors.expenseCoral, colors.expenseBg, colors.expenseText)
        "BUDGET_ALERT", "BILLING_DUE" -> Triple(colors.warningAmber, colors.warningBg, colors.warningText)
        "GOAL_REACHED" -> Triple(colors.incomeEmerald, colors.incomeBg, colors.incomeText)
        else -> Triple(colors.primaryDeep, colors.pillTrack, colors.primaryDeep)
    }
    val eventIcon = when (eventType) {
        "CAPTURE_REVIEW" -> Icons.Default.NotificationsNone
        "BILLING_DUE" -> Icons.Default.Schedule
        "GOAL_REACHED" -> Icons.Default.Flag
        "BUDGET_ALERT" -> Icons.Default.PieChart
        "CREDIT_THRESHOLD", "CREDIT_UTILIZATION_THRESHOLD_CROSSED" -> Icons.Default.WarningAmber
        else -> Icons.Default.Info
    }
    val timestampFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("es-PE"))
            .withZone(ZoneId.systemDefault())
    }
    val kindLabel = if (notification.category == NotificationCategory.REMINDER) {
        "Recordatorio"
    } else {
        "Alerta"
    }
    val readLabel = if (notification.isRead) "Leído" else "No leído"
    val dueDateLabel = notification.expectedDueDateLabel()
    val receivedAt = timestampFormatter.format(notification.createdAt)
    val receivedRelative = relativeReceivedLabel(notification.createdAt, now)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("notification-row-${notification.id}")
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription = listOfNotNull(
                    notification.title,
                    kindLabel,
                    dueDateLabel,
                    notification.body,
                    readLabel,
                    "Recibido $receivedAt",
                ).joinToString(", ")
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceCard,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .drawBehind {
                    val railWidth = 4.dp.toPx()
                    val railX = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - railWidth
                    drawRect(accent, topLeft = Offset(railX, 0f), size = Size(railWidth, size.height))
                }
                .padding(start = 14.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(eventIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                    color = colors.primaryText,
                )
                // The full body stays in the row semantics; large text never truncates.
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryMuted,
                    maxLines = if (largeText) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                dueDateLabel?.let { dueDate ->
                    Text(dueDate, style = MaterialTheme.typography.labelMedium, color = colors.primaryDeep)
                }
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!notification.isRead) {
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.primaryDeep, CircleShape)
                                .semantics { contentDescription = "Aviso sin leer" },
                        )
                    }
                    Text(receivedRelative, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                    Text("·", style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                    Text(kindLabel, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                }
            }
            // Unread notices are marked read first; only read notices expose the delete X.
            if (notification.isRead) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Eliminar notificación" },
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = colors.secondaryMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onMarkRead,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Marcar leído: ${notification.title}" },
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.primaryDeep,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
