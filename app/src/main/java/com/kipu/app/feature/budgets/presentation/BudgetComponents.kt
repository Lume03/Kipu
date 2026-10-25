package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.budgets.domain.BudgetConsumption
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Importe en unidad menor con la mascara de privacidad existente. */
@Composable
internal fun BudgetMoney(
    minorUnits: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    MoneyText(
        minorUnits = minorUnits,
        currency = Currency.fromCode(currencyCode),
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
    )
}

internal fun BudgetConsumption.needsAttention(thresholdsBps: List<Int>): Boolean =
    !isExceeded && thresholdsBps.any { percentBps >= it }

internal fun BudgetConsumption.statusLabel(thresholdsBps: List<Int> = emptyList()): String = when {
    isExceeded -> "Excedido"
    remainingMinor == 0L -> "Límite alcanzado"
    percentBps >= 8000 && needsAttention(thresholdsBps) -> "Casi al límite"
    needsAttention(thresholdsBps) -> "Atención"
    else -> "Dentro del límite"
}

/** Detail reference: three peer metrics below the ring, with no nested statistic cards. */
@Composable
internal fun BudgetDetailAmounts(consumption: BudgetConsumption, currencyCode: String, thresholdsBps: List<Int> = com.kipu.app.feature.budgets.domain.Budget.DEFAULT_THRESHOLDS_BPS) {
    val colors = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("Consumido", consumption.consumedMinor, "budget_consumed"),
            Triple(if (consumption.isExceeded) "Te pasaste" else "Restante",
                if (consumption.isExceeded) -consumption.remainingMinor else consumption.remainingMinor, "budget_remaining"),
            Triple("Límite", consumption.limitMinor, "budget_limit"),
        ).forEachIndexed { index, (label, amount, tag) ->
            Column(Modifier.weight(1f).testTag(tag).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                BudgetMoney(amount, currencyCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    color = if (index == 1) when {
                        consumption.isExceeded -> colors.expenseText
                        consumption.needsAttention(thresholdsBps) -> colors.warningText
                        else -> colors.primaryDeep
                    } else colors.primaryText)
            }
        }
    }
}

@Composable
internal fun BudgetInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private fun spoken(minor: Long, currencyCode: String): String =
    "${Currency.fromCode(currencyCode).symbol()} ${formatMinorUnits(minor)}"

/**
 * Barra de progreso accesible: el estado no depende solo del color (icono y texto "Excedido" /
 * "Límite alcanzado") y la descripcion hablada incluye porcentaje, consumido, limite y restante.
 * Con la mascara de privacidad activa solo se anuncia el porcentaje.
 */
@Composable
internal fun BudgetProgressBar(
    consumption: BudgetConsumption,
    currencyCode: String,
    modifier: Modifier = Modifier,
    thresholdsBps: List<Int> = emptyList(),
) {
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    val fraction = if (consumption.limitMinor <= 0L) 0f
    else (consumption.consumedMinor.toDouble() / consumption.limitMinor.toDouble()).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        consumption.isExceeded -> colors.expenseCoral
        consumption.percentBps >= 8000 && consumption.needsAttention(thresholdsBps) -> colors.nearLimitOrange
        consumption.remainingMinor == 0L || consumption.needsAttention(thresholdsBps) -> colors.warningAmber
        else -> MaterialTheme.colorScheme.primary
    }
    val status = consumption.statusLabel(thresholdsBps)
    val description = if (masked) {
        "Consumido ${consumption.percentWhole} %. $status. Importes ocultos."
    } else {
        "Consumido ${consumption.percentWhole} %: ${spoken(consumption.consumedMinor, currencyCode)} de " +
            "${spoken(consumption.limitMinor, currencyCode)}. " + if (consumption.isExceeded) {
            "Excedido por ${spoken(-consumption.remainingMinor, currencyCode)}."
        } else {
            "Restante ${spoken(consumption.remainingMinor, currencyCode)}. $status."
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag("budget_progress")
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = status
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (consumption.isExceeded || consumption.remainingMinor == 0L || consumption.needsAttention(thresholdsBps)) {
                Icon(
                    Icons.Default.ErrorOutline, contentDescription = null, tint = barColor, modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    Icons.Default.CheckCircle, contentDescription = null, tint = barColor, modifier = Modifier.size(16.dp),
                )
            }
            Text(
                "${consumption.percentWhole} % · $status",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    consumption.isExceeded -> colors.expenseText
                    consumption.remainingMinor == 0L || consumption.needsAttention(thresholdsBps) -> colors.warningText
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Importe principal y dos estadísticas, manteniendo la máscara de privacidad. */
@Composable
internal fun BudgetAmountsRow(consumption: BudgetConsumption, currencyCode: String, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).testTag("budget_consumed").semantics(mergeDescendants = true) {}) {
            Text("Consumido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BudgetMoney(consumption.consumedMinor, currencyCode, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.testTag("budget_limit").semantics(mergeDescendants = true) {}) {
                Text("de ", style = MaterialTheme.typography.labelSmall)
                BudgetMoney(consumption.limitMinor, currencyCode, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.weight(1f).testTag("budget_remaining").semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.End) {
            Text(if (consumption.isExceeded) "Te pasaste" else "Restante", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            BudgetMoney(
                if (consumption.isExceeded) -consumption.remainingMinor else consumption.remainingMinor,
                currencyCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (consumption.isExceeded) colors.expenseText else colors.primaryDeep)
        }
    }
}

/** Explica el cupo Free sin borrar ni ocultar nada y ofrece ver planes. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun BudgetQuotaDialog(limit: Int, onDismiss: () -> Unit, onSeePlans: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    com.kipu.app.ui.component.KipuBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("budget_quota_dialog")) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.WorkspacePremium, null, tint = colors.warningText)
            Text("Llegaste al límite de tu Plan Básico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("El plan Free permite hasta $limit presupuestos activos y ya los tienes. No se borró ni se ocultó ninguno. Puedes seguir consultándolos y editándolos. Para crear otro, finaliza uno activo o revisa los planes.")
            androidx.compose.material3.Button(onClick = onSeePlans, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.warningAmber, contentColor = colors.primaryText)) { Text("Ver planes") }
            androidx.compose.material3.OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Ver mis presupuestos") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Ahora no") }
        }
    }
}
