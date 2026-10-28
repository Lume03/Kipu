package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.theme.rememberPlanningColors

/** D6: the Basic plan allows [limit] active debts or loans in total, shared between both directions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPlanLimitSheet(
    activeCount: Int,
    limit: Int,
    onDismiss: () -> Unit,
    onSeePlans: () -> Unit,
    onSeeActiveDebts: () -> Unit,
) {
    val colors = rememberPlanningColors()
    KipuBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("debt_plan_limit_sheet")) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.warningBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = colors.warningText, modifier = Modifier.size(28.dp))
                }
                Text(
                    "Llegaste al límite de tu Plan Free",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp).weight(1f).semantics { heading() },
                )
            }
            Text(
                buildAnnotatedString {
                    append("Tienes ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append("$activeCount de $limit deudas o préstamos activos")
                    }
                    append(". El límite es compartido entre lo que debes y te deben. Salda una para crear otra.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(limit) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (index < activeCount) colors.warningAmber else colors.pillTrack),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.warningBg.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, colors.warningBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CON PRO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.warningText)
                    listOf(
                        "Más presupuestos y metas activas",
                        "Filtros avanzados en movimientos",
                        "Más categorías personalizadas",
                    ).forEach { benefit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(colors.warningAmber),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(16.dp))
                            }
                            Text(benefit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            Button(
                onClick = onSeePlans,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("debt_limit_upgrade"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.warningAmber, contentColor = colors.primaryText),
            ) {
                Text("Mejorar a PRO", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSeeActiveDebts,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Text("Ver mis deudas activas", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ahora no", color = colors.secondaryMuted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
