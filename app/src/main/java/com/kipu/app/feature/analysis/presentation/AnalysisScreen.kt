package com.kipu.app.feature.analysis.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuEmptyState
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Provisional root destination: Análisis has no defined design or functionality yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen() {
    val colors = rememberCalmEmeraldColors()
    Column(Modifier.fillMaxSize().testTag("analysis_screen")) {
        TopAppBar(
            title = { Text("Análisis", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
            KipuEmptyState(
                title = "Próximamente",
                message = "Estamos preparando el análisis de tu dinero.",
                icon = {
                    Surface(shape = CircleShape, color = colors.incomeBg, modifier = Modifier.size(72.dp)) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = colors.primaryDeep,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                },
            )
        }
    }
}
