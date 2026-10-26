package com.kipu.app.feature.calendar.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.calendar.domain.CalendarDay
import com.kipu.app.feature.calendar.domain.CalendarEvent
import com.kipu.app.feature.calendar.domain.CalendarEventKind
import com.kipu.app.feature.calendar.domain.CalendarEventStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.KipuErrorState
import com.kipu.app.ui.component.KipuSkeletonBlock
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.loadingSemantics
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal const val CALENDAR_PREVIOUS_MONTH_TAG = "calendar_previous_month"
internal const val CALENDAR_NEXT_MONTH_TAG = "calendar_next_month"
internal const val CALENDAR_EVENT_TAG_PREFIX = "calendar_event_"
internal const val CALENDAR_DAY_TAG_PREFIX = "calendar_day_"
internal const val CALENDAR_MASK_TOGGLE_TAG = "calendar_mask_toggle"
internal const val CALENDAR_EVENT_SHEET_TAG = "calendar_event_sheet"
internal const val CALENDAR_OPEN_SOURCE_TAG = "calendar_open_source"

private val SPANISH = Locale.forLanguageTag("es-PE")
private val DAY_TITLE_FORMAT = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH)
private val DAY_FULL_FORMAT = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)
private val WEEK_DAYS = listOf("L", "M", "M", "J", "V", "S", "D")
private val WEEK_DAY_NAMES = DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, SPANISH) }

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
    val colors = rememberCalmEmeraldColors()
    val inheritedMask = LocalBalanceMasked.current
    var masked by rememberSaveable { mutableStateOf(inheritedMask) }
    var filter by rememberSaveable { mutableStateOf(CalendarFilter.ALL) }
    var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }

    val days = (state.content as? CalendarContent.Loaded)?.month?.days.orEmpty()
    // La selección vuelve al día por defecto al cambiar de mes o cuando cambian los eventos del mes.
    var selectedDateText by rememberSaveable(state.month) { mutableStateOf<String?>(null) }
    val selectedDate = selectedDateText?.let(LocalDate::parse)?.takeIf { YearMonth.from(it) == state.month }
        ?: defaultSelectedDate(state.month, state.today, days)

    CompositionLocalProvider(LocalBalanceMasked provides masked) {
        Scaffold(
            modifier = modifier,
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                    title = {
                        Text(
                            "Calendario financiero",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { masked = !masked },
                            modifier = Modifier.testTag(CALENDAR_MASK_TOGGLE_TAG),
                        ) {
                            Icon(
                                imageVector = if (masked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (masked) "Mostrar importes" else "Ocultar importes",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("calendar_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "switcher") {
                    MonthSwitcher(state.month, state.today, onPreviousMonth, onNextMonth, onToday)
                }
                when (val content = state.content) {
                    CalendarContent.Loading -> item(key = "loading") { LoadingContent() }
                    is CalendarContent.Error -> item(key = "error") {
                        KipuErrorState(
                            title = "No pudimos cargar este mes",
                            message = content.message,
                            onRetry = onRetry,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    CalendarContent.Empty, is CalendarContent.Loaded -> {
                        item(key = "filters") { FilterRow(filter) { filter = it } }
                        item(key = "grid") {
                            MonthGrid(
                                month = state.month,
                                today = state.today,
                                days = days,
                                filter = filter,
                                selected = selectedDate,
                                onSelect = { selectedDateText = it.toString() },
                            )
                        }
                        if (content is CalendarContent.Empty) {
                            item(key = "empty") { EmptyMonthCard() }
                        } else {
                            val dayEvents = days.firstOrNull { it.date == selectedDate }?.events.orEmpty()
                                .filter(filter::matches)
                            item(key = "day_header") { DayHeader(selectedDate, dayEvents.size) }
                            if (dayEvents.isEmpty()) {
                                item(key = "day_empty") {
                                    Text(
                                        text = "No hay eventos ${filterSuffix(filter)}para este día.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.secondaryMuted,
                                    )
                                }
                            }
                            items(dayEvents, key = { it.id }) { event ->
                                EventRow(event, state.today, onClick = { selectedEventId = event.id })
                            }
                        }
                        monthFootnote(state.month, state.today)?.let { note ->
                            item(key = "footnote") {
                                Text(note, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                            }
                        }
                    }
                }
            }
        }

        val selectedEvent = selectedEventId?.let { id -> days.flatMap { it.events }.firstOrNull { it.id == id } }
        if (selectedEvent != null) {
            EventDetailSheet(
                event = selectedEvent,
                today = state.today,
                onOpen = {
                    selectedEventId = null
                    onEventClick(selectedEvent)
                },
                onDismiss = { selectedEventId = null },
            )
        }
    }
}

private fun filterSuffix(filter: CalendarFilter) = when (filter) {
    CalendarFilter.ALL -> ""
    CalendarFilter.PLANNED -> "previstos "
    CalendarFilter.REALIZED -> "realizados "
}

private fun monthFootnote(month: YearMonth, today: LocalDate): String? = when (monthPosition(month, today)) {
    MonthPosition.PAST -> "Los meses pasados siempre están disponibles gratis."
    MonthPosition.FUTURE -> "Los eventos previstos son recordatorios: Kipu no hace cargos automáticos."
    MonthPosition.CURRENT -> null
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleNavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", onPrevious, CALENDAR_PREVIOUS_MONTH_TAG)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = monthTitle(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(
                text = monthSubtitle(month, today),
                style = MaterialTheme.typography.labelMedium,
                color = if (monthPosition(month, today) == MonthPosition.PAST) colors.secondaryMuted else colors.primaryDeep,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (monthPosition(month, today) != MonthPosition.CURRENT) {
                TextButton(onClick = onToday) { Text("Ir a hoy", color = colors.primaryDeep) }
            }
        }
        CircleNavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", onNext, CALENDAR_NEXT_MONTH_TAG)
    }
}

@Composable
private fun CircleNavButton(icon: ImageVector, description: String, onClick: () -> Unit, tag: String) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.size(48.dp).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = colors.primaryText)
        }
    }
}

@Composable
private fun FilterRow(selected: CalendarFilter, onSelect: (CalendarFilter) -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalendarFilter.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) colors.primaryDeep else colors.surfaceCard,
                contentColor = if (isSelected) colors.onPrimaryDeep else colors.primaryText,
                border = if (isSelected) null else BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .testTag("calendar_filter_${option.name.lowercase()}"),
            ) {
                Box(Modifier.padding(horizontal = 2.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    // Sin maxLines: con fuente grande el texto puede partirse, nunca truncarse.
                    Text(option.label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    days: List<CalendarDay>,
    filter: CalendarFilter,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val eventsByDate = remember(days, filter) {
        days.associate { day -> day.date to day.events.filter(filter::matches) }
    }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    KipuCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), borderColor = null) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                WEEK_DAYS.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                DayCell(
                                    date = date,
                                    events = eventsByDate[date].orEmpty(),
                                    today = today,
                                    isSelected = date == selected,
                                    onClick = { onSelect(date) },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Legend()
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    events: List<CalendarEvent>,
    today: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val isToday = date == today
    val statuses = events.map { displayStatus(it, today) }.toSet()
    val shape = RoundedCornerShape(14.dp)
    val description = buildString {
        append(date.dayOfMonth).append(" de ").append(date.month.getDisplayName(TextStyle.FULL, SPANISH).lowercase(SPANISH))
        append(", ").append(WEEK_DAY_NAMES[date.dayOfWeek.value - 1])
        if (isToday) append(", hoy")
        append(", ").append(eventCountLabel(events.size).substringBefore(" ·").lowercase(SPANISH))
        CalendarDisplayStatus.entries.filter { it in statuses }.forEach { append(", ").append(it.label.lowercase(SPANISH)) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (isSelected) colors.primaryDeep else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primaryDeep, shape) else Modifier)
            .testTag(CALENDAR_DAY_TAG_PREFIX + date)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) colors.onPrimaryDeep else colors.primaryText,
            )
            Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                CalendarDisplayStatus.entries.filter { it in statuses }.forEach { status ->
                    StatusMarker(status, colors, onSelected = isSelected)
                }
            }
        }
    }
}

/** Marcas distintas en forma (no solo color): punto lleno, anillo y cuadrado. */
@Composable
private fun StatusMarker(status: CalendarDisplayStatus, colors: CalmEmeraldColors, onSelected: Boolean = false, size: Int = 6) {
    val base = Modifier.size(size.dp)
    when (status) {
        CalendarDisplayStatus.REALIZED -> Box(base.clip(CircleShape).background(if (onSelected) colors.onPrimaryDeep else colors.primaryDeep))
        CalendarDisplayStatus.PLANNED -> Box(base.border(1.2.dp, if (onSelected) colors.onPrimaryDeep else colors.primaryDeep, CircleShape))
        CalendarDisplayStatus.OVERDUE -> Box(
            base.clip(RoundedCornerShape(1.dp)).background(if (onSelected) colors.onPrimaryDeep else colors.expenseText),
        )
    }
}

@Composable
private fun Legend() {
    val colors = rememberCalmEmeraldColors()
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarDisplayStatus.entries.forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusMarker(status, colors, size = 8)
                Text(status.label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeader(date: LocalDate, count: Int) {
    val colors = rememberCalmEmeraldColors()
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(date.format(DAY_TITLE_FORMAT).lowercase(SPANISH), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            eventCountLabel(count),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryMuted,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun EventRow(event: CalendarEvent, today: LocalDate, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val status = displayStatus(event, today)
    KipuCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CALENDAR_EVENT_TAG_PREFIX + event.id)
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "Ver detalle de ${event.title}", onClick = onClick),
        borderColor = colors.borderSubtle,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventIcon(event, size = 48)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(eventSubtitle(event), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                // El estado se comunica con icono y texto además del color.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    StatusChip(status)
                    if (!event.isSourceAvailable) UnavailableChip()
                }
            }
            EventAmount(event)
        }
    }
}

@Composable
private fun EventAmount(event: CalendarEvent, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall) {
    val amount = event.amount
    if (amount != null) {
        MoneyText(money = amount, style = style, fontWeight = FontWeight.Bold)
    } else {
        Text("—", style = style, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Sin importe" })
    }
}

@Composable
private fun EventIcon(event: CalendarEvent, size: Int) {
    val colors = rememberCalmEmeraldColors()
    val institution = event.institution
    if (institution != null && (event.kind == CalendarEventKind.CARD_CLOSING || event.kind == CalendarEventKind.CARD_DUE)) {
        val brand = KipuBrand.from(institution) ?: KipuBrand.from(institution.substringBefore(' '))
        BrandLogo(brand = brand, fallbackName = institution, contentDescription = null, size = size.dp)
        return
    }
    val (icon, tint, background) = when (event.kind) {
        CalendarEventKind.MOVEMENT -> when (event.moneyDirection) {
            MovementType.INCOME -> Triple(Icons.Outlined.SouthWest, colors.incomeText, colors.incomeBg)
            MovementType.EXPENSE -> Triple(Icons.Outlined.NorthEast, colors.expenseText, colors.expenseBg)
            else -> Triple(Icons.Outlined.SwapHoriz, colors.transferBlue, colors.transferBg)
        }
        CalendarEventKind.CARD_CLOSING, CalendarEventKind.CARD_DUE ->
            Triple(Icons.Outlined.CreditCard, colors.transferBlue, colors.transferBg)
        CalendarEventKind.DEBT_INSTALLMENT, CalendarEventKind.DEBT_DUE ->
            if (event.moneyDirection == MovementType.INCOME) {
                Triple(Icons.Outlined.SouthWest, colors.incomeText, colors.incomeBg)
            } else {
                Triple(Icons.Outlined.Payments, colors.warningText, colors.warningBg)
            }
        CalendarEventKind.RECURRENCE -> Triple(Icons.Outlined.Autorenew, colors.primaryDeep, colors.pillTrack)
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun StatusChip(status: CalendarDisplayStatus) {
    val colors = rememberCalmEmeraldColors()
    val (icon, content, background) = when (status) {
        CalendarDisplayStatus.REALIZED -> Triple(Icons.Outlined.CheckCircle, colors.incomeText, colors.incomeBg)
        CalendarDisplayStatus.PLANNED -> Triple(Icons.Outlined.Schedule, colors.primaryDeep, colors.transferBg)
        CalendarDisplayStatus.OVERDUE -> Triple(Icons.Outlined.WarningAmber, colors.expenseText, colors.expenseBg)
    }
    Chip(icon, status.label, content, background)
}

@Composable
private fun UnavailableChip() {
    val colors = rememberCalmEmeraldColors()
    Chip(Icons.Outlined.ErrorOutline, "No disponible", colors.secondaryMuted, colors.pillTrack)
}

@Composable
private fun Chip(icon: ImageVector, label: String, content: Color, background: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyMonthCard() {
    val colors = rememberCalmEmeraldColors()
    KipuCard(modifier = Modifier.fillMaxWidth().testTag("calendar_empty"), shape = RoundedCornerShape(20.dp), borderColor = null) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Sin eventos este mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cuando registres deudas, tarjetas o movimientos con fecha, aparecerán aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).loadingSemantics("Cargando calendario"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 320.dp, cornerRadius = 20.dp)
        KipuSkeletonBlock(Modifier.width(160.dp), height = 20.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 76.dp, cornerRadius = 16.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 76.dp, cornerRadius = 16.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(event: CalendarEvent, today: LocalDate, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val status = displayStatus(event, today)
    KipuBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(CALENDAR_EVENT_SHEET_TAG)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EventIcon(event, size = 48)
                Column(Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(event.date.format(DAY_FULL_FORMAT).lowercase(SPANISH), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = colors.background, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Importe") { EventAmount(event, MaterialTheme.typography.bodyLarge) }
                    DetailRow("Estado") {
                        Text(status.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    DetailRow("Origen") {
                        Text(
                            eventOrigin(event, status),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            if (!event.isSourceAvailable) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.warningBg).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.warningText, modifier = Modifier.size(20.dp))
                    Text(
                        "Objeto no disponible: el registro de origen ya no existe. El evento se muestra solo como referencia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warningText,
                    )
                }
            }
            Text(
                text = when (event.status) {
                    CalendarEventStatus.PLANNED -> if (event.kind == CalendarEventKind.CARD_CLOSING) {
                        "Fecha de cierre prevista: no genera cargos ni movimientos automáticos."
                    } else {
                        "Evento previsto: no se descuenta saldo automáticamente. Regístralo cuando ocurra."
                    }
                    CalendarEventStatus.REALIZED -> "Evento realizado: ya está registrado en tu historial."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryMuted,
            )
            if (event.isSourceAvailable) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(CALENDAR_OPEN_SOURCE_TAG),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                ) {
                    Text(openSourceLabel(event), fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cerrar", color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.secondaryMuted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
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
