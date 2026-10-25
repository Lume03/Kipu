package com.kipu.app.feature.notifications.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationCategory
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
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
    reducedMotion: Boolean? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val disableMotion = reducedMotion ?: rememberReducedMotionEnabled()
    val colors = rememberCalmEmeraldColors()
    val largeText = LocalDensity.current.fontScale >= 1.3f
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
                            if (unreadCount > 0 && !largeText) {
                                TextButton(onClick = onMarkAllRead, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Marcar leídas", color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (unreadCount > 0 && largeText) {
                            TextButton(
                                onClick = onMarkAllRead,
                                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                            ) {
                                Text("Marcar leídas", color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            containerColor = colors.background,
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NotificationFilter.entries.forEach { filter ->
                        KipuFilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            label = {
                                Text(
                                    filter.label,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.surfaceCard,
                                labelColor = colors.primaryText,
                                selectedContainerColor = colors.primaryDeep,
                                selectedLabelColor = colors.onPrimaryDeep,
                            ),
                        )
                    }
                }

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
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.visibleNotifications, key = { it.id }) { notification ->
                                val rowModifier = if (disableMotion) Modifier else Modifier.animateItem()
                                NotificationRow(
                                    notification = notification,
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
private fun NotificationRow(
    notification: AppNotification,
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
        "BILLING_DUE" -> Icons.Default.Schedule
        "GOAL_REACHED" -> Icons.Default.CheckCircle
        "BUDGET_ALERT", "CREDIT_THRESHOLD", "CREDIT_UTILIZATION_THRESHOLD_CROSSED" -> Icons.Default.WarningAmber
        else -> Icons.Default.Info
    }
    val timestampFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("es-PE"))
            .withZone(ZoneId.systemDefault())
    }
    val kindLabel = if (notification.category == NotificationCategory.REMINDER) {
        "Recordatorio · Fecha prevista"
    } else {
        "Alerta · Condición ocurrida"
    }
    val readLabel = if (notification.isRead) "Leído" else "No leído"
    val dueDateLabel = notification.expectedDueDateLabel()
    val receivedAt = timestampFormatter.format(notification.createdAt)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceCard,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .drawBehind {
                    val railWidth = 5.dp.toPx()
                    val railX = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - railWidth
                    drawRect(accent, topLeft = Offset(railX, 0f), size = Size(railWidth, size.height))
                }
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(eventIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(notification.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                Text(notification.body, style = MaterialTheme.typography.bodyLarge, color = colors.secondaryMuted)
                dueDateLabel?.let { dueDate ->
                    Text(dueDate, style = MaterialTheme.typography.labelLarge, color = colors.primaryDeep)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(kindLabel, style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!notification.isRead) {
                            Spacer(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryDeep, CircleShape)
                                    .semantics { contentDescription = "Aviso sin leer" },
                            )
                        }
                        Text(readLabel, style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                    }
                }
                Text(
                    "Recibido $receivedAt",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!notification.isRead) {
                        IconButton(
                            onClick = onMarkRead,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Marcar leído: ${notification.title}" },
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Archivar aviso: ${notification.title}" },
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null)
                    }
                }
            }
        }
    }
}
