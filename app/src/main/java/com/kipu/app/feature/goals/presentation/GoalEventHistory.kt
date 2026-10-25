package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GoalEventHistory(
    events: List<GoalEvent>,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    KipuCard(modifier = modifier.fillMaxWidth(), containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(24.dp), borderColor = null, elevation = 1.dp) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Historial de la meta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (events.isEmpty()) {
            Text("Aún no hay aportes ni liberaciones.", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                events.asReversed().forEach { event ->
                    Column {
                        HorizontalDivider(color = colors.borderSubtle)
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(event.type.label(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    DATE_FORMAT.format(event.occurredAt.atZone(ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (event.transactionId != null) {
                                    Text("Traslado interno registrado", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            MoneyText(event.delta, modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                                color = if (event.delta.minorUnits > 0L) colors.incomeText else colors.expenseText,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    }
}

private fun GoalEventType.label(): String = when (this) {
    GoalEventType.ALLOCATION -> "Aporte reservado"
    GoalEventType.RELEASE -> "Reserva liberada"
    GoalEventType.REASSIGNMENT_OUT -> "Reasignado a otra meta"
    GoalEventType.REASSIGNMENT_IN -> "Recibido desde otra meta"
    GoalEventType.ADJUSTMENT -> "Ajuste de respaldo"
    GoalEventType.DIRECT_DEPOSIT -> "Aporte histórico"
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
