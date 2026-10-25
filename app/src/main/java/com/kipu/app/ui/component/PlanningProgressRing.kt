package com.kipu.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** A clamped visual ring that still announces the actual percentage, including overspending. */
@Composable
fun PlanningProgressRing(percent: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    Box(
        modifier.size(176.dp).clearAndSetSemantics { contentDescription = "$percent % $label" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = colors.pillTrack,
            strokeWidth = 14.dp,
            gapSize = 0.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
        }
    }
}
