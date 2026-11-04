package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.at
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.theme.rememberPastelTones

/** Starting points offered when there are no goals yet; each one only prefills the creation form. */
internal enum class GoalIdea(
    val key: String,
    val title: String,
    val subtitle: String,
    /** Name prefilled in the form; blank leaves the person to decide. */
    val goalName: String,
    val iconKey: String,
    val icon: ImageVector,
    private val toneIndex: Int,
) {
    EMERGENCY("emergency", "Fondo de emergencia", "3 meses de gastos", "Fondo de emergencia", "shield", Icons.Default.Shield, 0),
    TRIP("trip", "Viaje", "Vacaciones o escapada", "Viaje", "flight", Icons.Default.Flight, 1),
    TECH("tech", "Tecnología", "Laptop, celular…", "Tecnología", "laptop", Icons.Default.LaptopMac, 3),
    OTHER("other", "Otra meta", "Tú decides", "", "savings", Icons.Default.Add, 5);

    fun tone(tones: List<com.kipu.app.ui.theme.PastelTone>) = tones.at(toneIndex)

    companion object {
        fun fromKey(key: String?): GoalIdea? = entries.firstOrNull { it.key == key }
    }
}

@Composable
internal fun GoalIdeasSection(onPick: (GoalIdea) -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val tones = rememberPastelTones()
    Column(modifier.fillMaxWidth().testTag("goal_ideas"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Empieza con una idea", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = colors.secondaryMuted, modifier = Modifier.padding(start = 4.dp, top = 2.dp).semantics { heading() },
        )
        GoalIdea.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { idea ->
                    val tone = idea.tone(tones)
                    Surface(
                        onClick = { onPick(idea) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.weight(1f).heightIn(min = 92.dp).testTag("goal_idea_${idea.key}"),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = tone.tile, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(idea.icon, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp)) }
                            }
                            Column {
                                Text(idea.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                                Text(idea.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
