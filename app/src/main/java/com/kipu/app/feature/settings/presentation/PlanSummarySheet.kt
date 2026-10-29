package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanSummarySheet(
    plan: SettingsPlanCard,
    onDismiss: () -> Unit,
    onViewOffers: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(plan.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(plan.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Qué incluye", style = MaterialTheme.typography.titleMedium)
            if (plan.plan == SettingsPlan.FREE) {
                val limits = FreePlanLimits()
                Text("Hasta ${limits.instruments} instrumentos financieros")
                Text("Hasta ${limits.customCategories} categorías personalizadas por tipo")
                Text("Hasta ${limits.debts} deudas, ${limits.goals} metas y ${limits.budgets} presupuestos activos")
                Text("Registro y consulta de tus movimientos")
            } else {
                Text("Todas las funciones Premium")
                Text("Instrumentos, categorías personalizadas, deudas, metas y presupuestos sin los límites de cantidad del plan Free")
            }
            onViewOffers?.let { navigate ->
                Button(
                    onClick = { onDismiss(); navigate() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.plan_choose_action)) }
                Text(
                    stringResource(R.string.plan_preference_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
