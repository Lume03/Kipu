package com.kipu.app.feature.calendar.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.calendar.domain.CalendarDay
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventStatus
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.KipuEmptyState
import com.kipu.app.ui.component.MoneyText
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal const val CALENDAR_PREVIOUS_MONTH_TAG = "calendar_previous_month"
internal const val CALENDAR_NEXT_MONTH_TAG = "calendar_next_month"
internal const val CALENDAR_EVENT_TAG_PREFIX = "calendar_event_"

private val SPANISH = Locale.forLanguageTag("es-PE")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)

internal fun monthTitle(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL, SPANISH).replaceFirstChar { it.uppercase(SPANISH) } + " " + month.year

internal fun statusLabel(status: CalendarEventStatus): String = when (status) {
    CalendarEventStatus.REALIZED -> "Realizado"
    CalendarEventStatus.PLANNED -> "Previsto"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Calendario financiero", modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MonthSwitcher(state.month, onPreviousMonth, onNextMonth, onToday)
            Box(Modifier.fillMaxSize()) {
                when (val content = state.content) {
                    CalendarContent.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).semantics { contentDescription = "Cargando calendario" },
                    )
                    CalendarContent.Empty -> KipuEmptyState(
                        title = "Sin eventos en ${monthTitle(state.month)}",
                        message = "No hay movimientos, vencimientos ni cuotas para este mes.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                    is CalendarContent.Error -> Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(content.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                    is CalendarContent.Loaded -> LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("calendar_list"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(content.month.days, key = { it.date.toString() }) { day ->
                            DaySection(day, isToday = day.date == state.today, onEventClick = onEventClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag(CALENDAR_PREVIOUS_MONTH_TAG)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            TextButton(onClick = onToday) { Text("Ir a hoy") }
        }
        IconButton(onClick = onNext, modifier = Modifier.testTag(CALENDAR_NEXT_MONTH_TAG)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
        }
    }
}

@Composable
private fun DaySection(day: CalendarDay, isToday: Boolean, onEventClick: (CalendarEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = day.date.format(DAY_FORMAT).replaceFirstChar { it.uppercase(SPANISH) } + if (isToday) " · Hoy" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        day.events.forEach { event -> EventRow(event, onEventClick) }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onEventClick: (CalendarEvent) -> Unit) {
    val clickable = event.isSourceAvailable
    KipuCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CALENDAR_EVENT_TAG_PREFIX + event.id)
            .semantics(mergeDescendants = true) { if (!clickable) disabled() }
            .then(
                if (clickable) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Abrir ${event.title}") { onEventClick(event) }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (event.status == CalendarEventStatus.REALIZED) Icons.Default.Check else Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                // El estado se comunica con texto además del icono; nunca solo con color.
                Text(
                    text = if (clickable) statusLabel(event.status) else "${statusLabel(event.status)} · No disponible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.amount?.let { MoneyText(money = it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Acceso desde Inicio al calendario financiero. */
@Composable
fun CalendarEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    KipuCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_calendar_entry")
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("Calendario financiero", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Movimientos, cierres, vencimientos y cuotas del mes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
