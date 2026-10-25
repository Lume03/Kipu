package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun GoalMonthlyContributions(events: List<GoalEvent>) {
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    val monthly = remember(events) {
        events.filter { !it.transactionVoided && it.type in setOf(GoalEventType.ALLOCATION, GoalEventType.DIRECT_DEPOSIT) }
            .groupBy { YearMonth.from(it.occurredAt.atZone(ZoneId.systemDefault())) }
            .toSortedMap().entries.toList().takeLast(6).map { it.key to it.value.sumOf { event -> event.delta.minorUnits } }
    }
    if (monthly.isEmpty()) return
    val maximum = monthly.maxOf { it.second }.coerceAtLeast(1L)
    val formatter = remember { DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-PE")) }
    KipuCard(containerColor = colors.surfaceCard, borderColor = null, shape = RoundedCornerShape(24.dp), elevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Aportes por mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                monthly.forEach { (month, minor) ->
                    Column(Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = if (masked) "${month.format(formatter)}: monto oculto"
                        else "${month.format(formatter)}: ${events.first().delta.currency.symbol()} ${formatMinorUnits(minor)}"
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height((96f * minor.toFloat() / maximum).coerceAtLeast(4f).dp)
                            .background(colors.primaryDeep, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        Text(month.format(formatter), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                    }
                }
            }
        }
    }
}
