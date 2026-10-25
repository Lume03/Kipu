package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun GoalPlanLimitSheet(onDismiss: () -> Unit, onSeePlans: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    KipuBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("goal_plan_limit_sheet")) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Lock, null, tint = colors.warningText)
            Text("Llegaste al límite de metas de tu Plan Básico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Tienes 2 de 2 metas activas. Conservas tus reservas y tu historial. Para crear otra, completa una meta o revisa los planes.")
            Button(onClick = onSeePlans, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.warningAmber, contentColor = colors.primaryText)) { Text("Mejorar a PRO") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver mis metas activas") }
        }
    }
}
