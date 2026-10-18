package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.ui.component.MoneyText
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GoalEventHistory(
    events: List<GoalEvent>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Historial de la meta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (events.isEmpty()) {
            Text("Aún no hay aportes ni liberaciones.", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                events.asReversed().forEach { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
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
                            MoneyText(event.delta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
