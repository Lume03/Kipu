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
import androidx.compose.material3.AlertDialog
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

internal fun BudgetConsumption.statusLabel(): String = when {
    isExceeded -> "Excedido"
    remainingMinor == 0L -> "Límite alcanzado"
    else -> "Dentro del límite"
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
) {
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    val fraction = if (consumption.limitMinor <= 0L) 0f
    else (consumption.consumedMinor.toDouble() / consumption.limitMinor.toDouble()).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        consumption.isExceeded -> colors.expenseCoral
        consumption.remainingMinor == 0L -> colors.warningAmber
        else -> MaterialTheme.colorScheme.primary
    }
    val status = consumption.statusLabel()
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
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (consumption.isExceeded || consumption.remainingMinor == 0L) {
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
                color = if (consumption.isExceeded) colors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Limite, consumido y restante (o excedido) en filas etiquetadas. */
@Composable
internal fun BudgetAmountsRow(consumption: BudgetConsumption, currencyCode: String, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountLine("Límite", consumption.limitMinor, currencyCode, "budget_limit")
        AmountLine("Consumido", consumption.consumedMinor, currencyCode, "budget_consumed")
        if (consumption.isExceeded) {
            AmountLine("Excedido", -consumption.remainingMinor, currencyCode, "budget_remaining", colors.expenseText)
        } else {
            AmountLine("Restante", consumption.remainingMinor, currencyCode, "budget_remaining")
        }
    }
}

@Composable
private fun AmountLine(label: String, minor: Long, currencyCode: String, tag: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BudgetMoney(minor, currencyCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Explica el cupo Free sin borrar ni ocultar nada y ofrece ver planes. */
@Composable
internal fun BudgetQuotaDialog(limit: Int, onDismiss: () -> Unit, onSeePlans: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("budget_quota_dialog"),
        icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
        title = { Text("Límite de presupuestos del plan Free") },
        text = {
            Text(
                "El plan Free permite hasta $limit presupuestos activos y ya los tienes. " +
                    "No se borró ni se ocultó ninguno: puedes seguir consultándolos y editándolos. " +
                    "Para crear otro, finaliza uno activo o revisa los planes.",
                modifier = Modifier.padding(top = 4.dp),
            )
        },
        confirmButton = { TextButton(onClick = onSeePlans) { Text("Ver planes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}
