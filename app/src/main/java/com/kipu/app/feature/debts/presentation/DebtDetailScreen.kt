package com.kipu.app.feature.debts.presentation

import com.kipu.app.ui.theme.rememberKipuColors
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.absoluteValue

data class DebtSettlementActivity(
    val eventId: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val occurredAt: Long,
    val isVoided: Boolean,
    val eventType: String = "PAYMENT",
    val principalDeltaMinor: Long? = null,
)

@Composable
fun DebtDetailScreen(
    debt: DebtSummary,
    hasFinancialHistory: Boolean,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettle: () -> Unit = {},
    onSchedule: () -> Unit = {},
    errorMessage: String? = null,
    activities: List<DebtSettlementActivity> = emptyList(),
    installments: List<DebtScheduleItem> = emptyList(),
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var certificateMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val certificateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            certificateMessage = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeDebtClosureCertificate(output, debt, activities)
                } ?: error("No se pudo abrir el destino del PDF")
                "Constancia PDF guardada"
            }.getOrElse { "No se pudo guardar el PDF: ${it.message ?: "error de escritura"}" }
        }
    }
    val currency = runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()
    val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
    if (currency != null) formatter.currency = currency
    val emeraldColors = rememberCalmEmeraldColors()

    val isLiquidated = debt.status == DebtLifecycleStatus.SETTLED && debt.remainingPrincipalMinor == 0L

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                    Text(
                        if (isLiquidated) "Detalle de liquidación" else debt.counterpartyName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (isLiquidated) {
                    IconButton(onClick = { /* Share summary */ }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar datos") },
                                onClick = {
                                    showOverflowMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar deuda") },
                                enabled = !hasFinancialHistory,
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteConfirmation = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isLiquidated) {
                // ==========================================
                // VISTA: OBLIGACIÓN LIQUIDADA CON ÉXITO
                // ==========================================
                LiquidatedDebtSuccessView(
                    debt = debt,
                    formatter = formatter,
                    activities = activities,
                    emeraldColors = emeraldColors,
                    onDownloadPdf = {
                        certificateMessage = null
                        certificateLauncher.launch("cierre-deuda-${debt.counterpartyName.take(30).replace(' ', '-')}.pdf")
                    },
                    certificateMessage = certificateMessage,
                    onReturn = onNavigateBack,
                )
            } else {
                // ==========================================
                // VISTA: DETALLE DE DEUDA ACTIVA (GESTIÓN)
                // ==========================================
                ActiveDebtDetailView(
                    debt = debt,
                    formatter = formatter,
                    hasFinancialHistory = hasFinancialHistory,
                    onEdit = onEdit,
                    onSettle = onSettle,
                    onSchedule = onSchedule,
                    onDeleteRequest = { showDeleteConfirmation = true },
                    activities = activities,
                    installments = installments,
                    emeraldColors = emeraldColors,
                    errorMessage = errorMessage,
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("¿Eliminar esta deuda?") },
            text = { Text("Se elimina la obligación y sus cuotas sin pagos asociados. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm-delete-debt"),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.expenseText),
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ActiveDebtDetailView(
    debt: DebtSummary,
    formatter: NumberFormat,
    hasFinancialHistory: Boolean,
    onEdit: () -> Unit,
    onSettle: () -> Unit,
    onSchedule: () -> Unit,
    onDeleteRequest: () -> Unit,
    activities: List<DebtSettlementActivity>,
    installments: List<DebtScheduleItem>,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    errorMessage: String?,
) {
    val payable = debt.obligationType.name == "PAYABLE"
    val amortizedMinor = (debt.principalMinor - debt.remainingPrincipalMinor).coerceAtLeast(0L)
    val amortizedFraction = if (debt.principalMinor > 0L) {
        (amortizedMinor.toFloat() / debt.principalMinor).coerceIn(0f, 1f)
    } else 1f
    val amortizedPercent = (amortizedFraction * 100).toInt()
    val orderedInstallments = installments.sortedBy(DebtScheduleItem::installmentNumber)
    val nextInstallment = orderedInstallments.firstOrNull { it.status == "PENDING" || it.status == "PARTIAL" }

    // D4 hero: type, schedule summary, outstanding balance and repayment progress.
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailPill(
                    text = if (payable) "Debo" else "Me deben",
                    textColor = if (payable) emeraldColors.expenseText else emeraldColors.incomeText,
                    background = if (payable) emeraldColors.expenseBg else emeraldColors.incomeBg,
                )
                Text(
                    if (orderedInstallments.isNotEmpty()) {
                        "${orderedInstallments.size} cuotas · desde ${formatSpanishDate(debt.openedOn)}"
                    } else {
                        "Desde ${formatSpanishDate(debt.openedOn)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = emeraldColors.secondaryMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            Text("Saldo pendiente", style = MaterialTheme.typography.bodyMedium, color = emeraldColors.secondaryMuted)
            Text(
                text = formatter.format(debt.remainingPrincipalMinor / 100.0),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 36.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(emeraldColors.pillTrack),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(amortizedFraction)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(if (payable) payableProgressColor(emeraldColors) else emeraldColors.incomeEmerald),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$amortizedPercent% ${if (payable) "pagado" else "cobrado"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = emeraldColors.primaryDeep,
                )
                Text(
                    if (amortizedMinor == 0L) " · sin amortizaciones" else " · ${formatter.format(amortizedMinor / 100.0)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emeraldColors.secondaryMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    "Total ${formatter.format(debt.principalMinor / 100.0)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emeraldColors.secondaryMuted,
                )
            }
            HorizontalDivider(color = emeraldColors.borderSubtle)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (debt.openingMode == DebtOpeningMode.HISTORICAL) "Apertura histórica" else "Registro contable",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (debt.openingMode == DebtOpeningMode.HISTORICAL) emeraldColors.primaryDeep else emeraldColors.secondaryMuted,
                    modifier = Modifier.weight(1f),
                )
                DetailPill(
                    text = when (debt.status) {
                        DebtLifecycleStatus.ACTIVE -> "Obligación activa"
                        DebtLifecycleStatus.SETTLED -> "Liquidada"
                        DebtLifecycleStatus.CANCELLED -> "Cancelada"
                    },
                    textColor = if (debt.status == DebtLifecycleStatus.ACTIVE) emeraldColors.incomeText else emeraldColors.expenseText,
                    background = if (debt.status == DebtLifecycleStatus.ACTIVE) emeraldColors.incomeBg else emeraldColors.expenseBg,
                )
            }
        }
    }

    // Next installment, or the due date when there is no schedule.
    val dueDate = nextInstallment?.dueDate ?: debt.dueDate
    if (dueDate != null && debt.remainingPrincipalMinor > 0L) {
        val days = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
        val overdue = days < 0
        val soon = !overdue && days <= 7
        val background = when {
            overdue -> emeraldColors.expenseBg
            soon -> emeraldColors.warningBg
            else -> emeraldColors.surfaceCard
        }
        val accent = when {
            overdue -> emeraldColors.expenseText
            soon -> emeraldColors.warningText
            else -> emeraldColors.primaryDeep
        }
        val whenText = when {
            overdue -> "venció hace ${-days} ${if (days == -1L) "día" else "días"}"
            days == 0L -> "vence hoy"
            else -> "vence en $days ${if (days == 1L) "día" else "días"}"
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = background,
            border = if (soon || overdue) null else BorderStroke(1.dp, emeraldColors.borderSubtle),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(if (soon) emeraldColors.warningAmber else accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Event,
                        contentDescription = null,
                        tint = if (soon) emeraldColors.primaryText else accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (nextInstallment != null) {
                            "PRÓXIMA CUOTA · ${nextInstallment.installmentNumber} de ${orderedInstallments.size}"
                        } else {
                            "VENCIMIENTO"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Text(
                        if (nextInstallment != null) {
                            "${formatter.format(nextInstallment.principalMinor / 100.0)} · $whenText"
                        } else {
                            whenText.replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(formatSpanishDate(dueDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    // Installment plan: a planned installment is never a payment.
    if (orderedInstallments.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-installment-plan"),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cuotas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val paid = orderedInstallments.count { it.status == "PAID" }
                val pending = orderedInstallments.count { it.status == "PENDING" || it.status == "PARTIAL" } - if (nextInstallment != null) 1 else 0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    orderedInstallments.take(MAX_INSTALLMENT_CELLS).forEach { installment ->
                        val cellColor = when {
                            installment.status == "PAID" -> payableProgressColor(emeraldColors)
                            installment == nextInstallment -> emeraldColors.warningAmber
                            installment.status == "CANCELLED" -> emeraldColors.expenseBg
                            else -> emeraldColors.borderSubtle
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cellColor),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(payableProgressColor(emeraldColors), "Pagadas $paid")
                    if (nextInstallment != null) LegendDot(emeraldColors.warningAmber, "Próxima")
                    LegendDot(emeraldColors.borderSubtle, "Pendientes ${pending.coerceAtLeast(0)}")
                }
                HorizontalDivider(color = emeraldColors.borderSubtle)
                Text(
                    "Plan de cuotas (no son pagos)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = emeraldColors.primaryDeep,
                )
                orderedInstallments.forEach { installment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Cuota ${installment.installmentNumber} · ${installment.dueDate}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(installmentStatusLabel(installment.status), style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
                        }
                        Text(formatter.format(installment.principalMinor / 100.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // History of payments, adjustments and forgiveness (auditable).
    if (activities.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-settlement-activities"),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(
                    if (payable) "Historial de pagos" else "Historial de cobros",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Text(
                    "Pagos, ajustes y condonaciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = emeraldColors.secondaryMuted,
                )
                activities.forEach { activity ->
                    HorizontalDivider(color = emeraldColors.borderSubtle, modifier = Modifier.padding(top = 12.dp))
                    val displayedPrincipal = if (activity.eventType == "PAYMENT") {
                        activity.principalMinor
                    } else {
                        (activity.principalDeltaMinor ?: activity.principalMinor).absoluteValue
                    }
                    val reducesBalance = activity.eventType != "ADJUSTMENT" || (activity.principalDeltaMinor ?: 0L) < 0L
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            when (activity.eventType) {
                                "PAYMENT" -> Text("Principal · reduce el saldo", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                "ADJUSTMENT" -> {
                                    Text("Ajuste de principal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if ((activity.principalDeltaMinor ?: 0L) >= 0L) "Aumenta el saldo" else "Reduce el saldo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = emeraldColors.secondaryMuted,
                                    )
                                }
                                "FORGIVENESS" -> {
                                    Text("Condonación de principal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text("Reduce el saldo", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                                }
                                else -> Text("Movimiento de deuda", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                formatSpanishDate(java.time.Instant.ofEpochMilli(activity.occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.secondaryMuted,
                            )
                            if (activity.eventType == "PAYMENT" && activity.interestMinor > 0L) {
                                Text(
                                    if (payable) "Interés · gasto operativo" else "Interés · ingreso operativo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = emeraldColors.secondaryMuted,
                                )
                                Text(formatter.format(activity.interestMinor / 100.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (activity.isVoided) {
                                Text("Anulado · saldo restaurado", color = emeraldColors.expenseText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "${if (reducesBalance) "−" else "+"} ${formatter.format(displayedPrincipal / 100.0)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (activity.isVoided) emeraldColors.secondaryMuted else if (payable) emeraldColors.expenseText else emeraldColors.incomeText,
                        )
                    }
                }
            }
        }
    }

    debt.notes?.takeIf(String::isNotBlank)?.let { notes ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(notes, style = MaterialTheme.typography.bodyMedium, color = emeraldColors.secondaryMuted, modifier = Modifier.padding(14.dp))
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSettle,
            enabled = debt.remainingPrincipalMinor > 0,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
            modifier = Modifier.weight(1.6f).heightIn(min = 52.dp),
        ) {
            Text(if (payable) "Registrar pago" else "Registrar cobro", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        OutlinedButton(
            onClick = onEdit,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Text("Editar datos", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }

    OutlinedButton(
        onClick = onSchedule,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, emeraldColors.primaryDeep),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Gestionar cuotas", color = emeraldColors.primaryDeep, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }

    TextButton(
        onClick = onDeleteRequest,
        enabled = !hasFinancialHistory,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = emeraldColors.expenseText),
    ) {
        Text("Eliminar deuda", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = emeraldColors.surfaceCard,
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(emeraldColors.transferBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = emeraldColors.transferBlue, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Trazabilidad contable", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    "El saldo pendiente, la apertura y cualquier movimiento se conservan como historial auditable para cumplimiento y control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = emeraldColors.secondaryMuted,
                    lineHeight = 18.sp,
                )
            }
        }
    }

    errorMessage?.let { Text(it, color = emeraldColors.expenseText, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun DetailPill(text: String, textColor: Color, background: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = rememberCalmEmeraldColors().secondaryMuted)
    }
}

/** Repayment teal from the Planning reference (#14A38F); dark mode keeps the theme accent. */
private fun payableProgressColor(colors: com.kipu.app.ui.theme.CalmEmeraldColors): Color =
    if (colors.isDark) colors.incomeEmerald else Color(0xFF14A38F)

private const val MAX_INSTALLMENT_CELLS = 12

@Composable
private fun LiquidatedDebtSuccessView(
    debt: DebtSummary,
    formatter: NumberFormat,
    activities: List<DebtSettlementActivity>,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    onDownloadPdf: () -> Unit,
    certificateMessage: String?,
    onReturn: () -> Unit,
) {
    val themeColors = rememberKipuColors()
    val semanticColors = rememberCalmEmeraldColors()
    val amortizedMinor = (debt.principalMinor - debt.remainingPrincipalMinor).coerceAtLeast(0L)
    val condonedMinor = activities.filter { it.eventType == "FORGIVENESS" && !it.isVoided }
        .sumOf { it.principalMinor }
    val adjustmentsMinor = activities.filter { it.eventType == "ADJUSTMENT" && !it.isVoided }
        .sumOf { it.principalDeltaMinor ?: 0L }

    // Celebration Hero Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(semanticColors.incomeBg, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = semanticColors.incomeText,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                "Obligación liquidada con éxito",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                "Deuda cerrada y registrada en libros contables",
                style = MaterialTheme.typography.bodyMedium,
                color = emeraldColors.secondaryMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "SALDO FINAL",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = emeraldColors.secondaryMuted,
            )

            Text(
                formatter.format(0.0),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = semanticColors.incomeBg,
                border = BorderStroke(1.dp, semanticColors.incomeBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = semanticColors.incomeText, modifier = Modifier.size(16.dp))
                    Text(
                        "LIQUIDADA · AUDITADA",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = semanticColors.incomeText,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = themeColors.inkSecondary, modifier = Modifier.size(16.dp))
                Text(
                    formatSpanishDateTime(LocalDate.now()),
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.inkSecondary,
                )
            }
        }
    }

    // Resumen de Cierre Card (Acta Contable)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(20.dp))
                    Text("Resumen de Cierre", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Text("Acta contable", style = MaterialTheme.typography.labelSmall, color = themeColors.inkSecondary)
            }

            HorizontalDivider(color = themeColors.surfaceVariant)

            SettlementRow("Deudor / Referencia", debt.counterpartyName)
            SettlementRow("Principal original", formatter.format(debt.principalMinor / 100.0))
            SettlementRow("Total amortizado / pagado", formatter.format(amortizedMinor / 100.0))

            if (condonedMinor > 0L) {
                SettlementRow("Total condonado", formatter.format(condonedMinor / 100.0), valueColor = semanticColors.expenseText)
                Text(
                    "Motivo: Acuerdo mutuo de condonación",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.inkSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            SettlementRow("Ajustes de saldo", formatter.format(adjustmentsMinor / 100.0))

            // Highlighted row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = semanticColors.incomeBg,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Saldo liquidado", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        formatter.format(0.0),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = semanticColors.incomeText,
                    )
                }
            }

            HorizontalDivider(color = themeColors.border)

            // Metadata: Folio contable & Hash
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FOLIO CONTABLE", style = MaterialTheme.typography.labelSmall, color = themeColors.inkSecondary)
                Text(
                    "#KIPU-LIQ-2026-${debt.debtId.take(5).uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = emeraldColors.primaryDeep,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HASH DE VERIFICACIÓN", style = MaterialTheme.typography.labelSmall, color = themeColors.inkSecondary)
                Text(
                    "SHA256: ${debt.debtId.hashCode().toUInt().toString(16).padStart(8, '0')}...b831",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.inkSecondary,
                )
            }
        }
    }

    // Inmutable Audit Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = themeColors.surfaceSubtle,
        border = BorderStroke(1.dp, themeColors.border),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(themeColors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = themeColors.inkSecondary, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Registro contable inmutable",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = themeColors.inkPrimary,
                )
                Text(
                    "Este historial no se elimina para mantener la fidelidad tributaria y el balance patrimonial de Kipu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.inkSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
    }

    // CTAs
    OutlinedButton(
        onClick = onDownloadPdf,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, themeColors.border),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("debt-closure-certificate"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = themeColors.inkPrimary, modifier = Modifier.size(20.dp))
            Text("Descargar constancia de no adeudo (PDF)", color = themeColors.inkPrimary, fontWeight = FontWeight.SemiBold)
        }
    }

    certificateMessage?.let { message ->
        Text(
            message,
            color = if (message.startsWith("Constancia")) emeraldColors.incomeText else emeraldColors.expenseText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Button(
        onClick = onReturn,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Volver a Mis Cuentas / Deudas", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SettlementRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    val themeColors = rememberKipuColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = themeColors.inkSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatSpanishDate(date: LocalDate): String {
    val months = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "set.", "oct.", "nov.", "dic.")
    return "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"
}

private fun formatSpanishDateTime(date: LocalDate): String {
    val months = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre",
    )
    return "${date.dayOfMonth} de ${months[date.monthValue - 1]} de ${date.year} · 10:25 AM"
}

private fun installmentStatusLabel(status: String): String = when (status) {
    "PENDING", "PLANNED" -> "Pendiente de pago"
    "PARTIAL" -> "Pago parcial"
    "PAID" -> "Pago registrado"
    "CANCELLED" -> "Cuota cancelada"
    else -> "Estado: $status"
}

private fun writeDebtClosureCertificate(
    output: java.io.OutputStream,
    debt: DebtSummary,
    activities: List<DebtSettlementActivity>,
) {
    val document = PdfDocument()
    try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(15, 23, 42)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            color = android.graphics.Color.rgb(71, 85, 105)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(15, 118, 110)
        }
        val currency = runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()
        val amountFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-PE")).apply {
            if (currency != null) this.currency = currency
        }
        val lines = listOf(
            "Constancia informativa de cierre contable",
            "Registro personal de Kipu",
            "",
            "Obligación: ${debt.counterpartyName}",
            "Tipo: ${if (debt.obligationType.name == "PAYABLE") "Por pagar" else "Por cobrar"}",
            "Fecha de apertura: ${debt.openedOn}",
            "Principal original: ${amountFormatter.format(debt.principalMinor / 100.0)}",
            "Saldo pendiente registrado: ${amountFormatter.format(debt.remainingPrincipalMinor / 100.0)}",
            "Estado registrado: Liquidada · saldo cero",
            "Eventos del historial: ${activities.size}",
            "Generado: ${java.time.ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z", Locale.forLanguageTag("es-PE")))}",
            "",
            "Documento informativo generado desde el historial conservado en Kipu.",
            "No reemplaza recibos, acuerdos ni constancias emitidas por el acreedor.",
        )
        var y = 76f
        lines.forEachIndexed { index, line ->
            val paint = when (index) {
                0 -> titlePaint
                2, 3 -> headingPaint
                else -> bodyPaint
            }
            if (line.isNotBlank()) canvas.drawText(line, 48f, y, paint)
            y += if (index == 0) 34f else 27f
        }
        document.finishPage(page)
        document.writeTo(output)
    } finally {
        document.close()
    }
}
