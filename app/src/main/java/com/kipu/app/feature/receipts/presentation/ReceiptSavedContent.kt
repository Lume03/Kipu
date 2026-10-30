package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal

/** "Gasto registrado" / "Ingreso registrado": the receipt is now a movement; offers the next one in the queue. */
@Composable
fun ReceiptSavedContent(
    saved: ReceiptCandidate,
    pendingCount: Int,
    onNext: () -> Unit,
    onMovement: (() -> Unit)?,
    onInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    val income = saved.isIncome == true
    val tone = if (income) palette.incomeText else palette.expenseText
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(palette.tealTint(.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(46.dp), tint = palette.primaryDeep)
            }
            Spacer(Modifier.size(6.dp))
            Text(if (income) "Ingreso registrado" else "Gasto registrado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
            saved.amountMinor?.let {
                Text((if (income) "+" else "−") + "S/ " + BigDecimal.valueOf(it, 2).setScale(2).toPlainString(), fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.displaySmall, color = tone, maxLines = 1)
            }
            saved.recipient?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.secondaryMuted, textAlign = TextAlign.Center) }
            Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surfaceCard).border(1.dp, palette.borderSubtle, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comprobantes por revisar", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                Text(pendingCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = if (pendingCount > 0) onNext else onInbox, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_review_next")) {
                Text(if (pendingCount > 0) "Revisar el siguiente" else "Volver a comprobantes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (pendingCount > 0) { Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp)) }
            }
            onMovement?.let {
                OutlinedButton(onClick = it, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Ver movimiento", fontWeight = FontWeight.Bold, color = palette.primaryText)
                }
            }
            if (pendingCount > 0) TextButton(onClick = onInbox, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ir a la bandeja", fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
            }
        }
    }
}
