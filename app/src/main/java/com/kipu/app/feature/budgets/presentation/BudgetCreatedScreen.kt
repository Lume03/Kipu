package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
internal fun BudgetCreatedScreen(content: BudgetDetailContent, onView: () -> Unit, onReturn: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Scaffold(modifier = Modifier.testTag("budget_created_screen"), bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onView, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver presupuesto") }
            OutlinedButton(onClick = onReturn, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Volver a Planificación") }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = colors.incomeBg) {
                Icon(Icons.Default.Check, null, tint = colors.primaryDeep, modifier = Modifier.padding(24.dp).size(48.dp))
            }
            Text("Presupuesto creado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Desde ahora verás cuánto llevas gastado. No se movió dinero de tus cuentas.",
                style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
            KipuCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), borderColor = null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(content.budget.name, fontWeight = FontWeight.Bold)
                    Text("${content.scopeLabel} · ${BudgetTexts.schedule(content.budget.schedule)}", style = MaterialTheme.typography.bodySmall)
                    content.current?.let { BudgetAmountsRow(it.consumption, content.budget.currency); BudgetProgressBar(it.consumption, content.budget.currency, thresholdsBps = content.budget.thresholdsBps) }
                    Text("Alertas configuradas: ${BudgetTexts.thresholds(content.budget.thresholdsBps)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
