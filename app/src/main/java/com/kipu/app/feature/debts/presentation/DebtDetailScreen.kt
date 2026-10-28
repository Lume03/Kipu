package com.kipu.app.feature.debts.presentation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.kipu.app.ui.theme.rememberKipuColors
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import com.kipu.app.ui.component.PlanningNotice
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.rememberPlanningColors
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
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
    val transactionId: String? = null,
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
    initialAction: String? = null,
    onClose: () -> Unit = onSchedule,
    onOpenMovement: (String) -> Unit = {},
) {
    var showDeleteConfirmation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialAction == "manage") }
    var showHistory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialAction == "history") }
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
    val emeraldColors = rememberPlanningColors()

    val isLiquidated = debt.status == DebtLifecycleStatus.SETTLED && debt.remainingPrincipalMinor == 0L

    Scaffold(
        containerColor = emeraldColors.background,
        bottomBar = {
            if (!isLiquidated) Surface(color = emeraldColors.surfaceCard, shadowElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSettle, enabled = debt.remainingPrincipalMinor > 0L && debt.status == DebtLifecycleStatus.ACTIVE,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep, contentColor = emeraldColors.onPrimaryDeep)) {
                        Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(20.dp))
                        Text(if (debt.obligationType == DebtObligationType.PAYABLE) "Registrar pago" else "Registrar cobro",
                            Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onSchedule, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(18.dp))
                            Text("Cuotas", Modifier.padding(start = 6.dp), color = emeraldColors.primaryDeep)
                        }
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Edit, null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(18.dp))
                            Text("Editar", Modifier.padding(start = 6.dp), color = emeraldColors.primaryDeep)
                        }
                    }
                }
            }
        },
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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                    Text(
                        "Detalle de deuda",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { showOverflowMenu = true }, modifier = Modifier.testTag("debt_detail_options")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
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
            verticalArrangement = if (isLiquidated) Arrangement.Center else Arrangement.spacedBy(14.dp),
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
                    onHistory = { showHistory = true },
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
                    onHistory = { showHistory = true },
                    onMovement = onOpenMovement,
                )
            }
        }
    }

    if (showOverflowMenu) {
        DebtActionsSheet(debt, { showOverflowMenu = false }, {}, onSettle,
            { showHistory = true }, onEdit, onClose, { showDeleteConfirmation = true })
    }
    if (showHistory) {
        DebtHistorySheet(debt, activities, onDismiss = { showHistory = false }, onOpenMovement = onOpenMovement)
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(if (hasFinancialHistory) "Historial protegido" else "¿Eliminar esta deuda?") },
            text = { Text(if (hasFinancialHistory)
                "Tiene movimientos. Para resolverla, usa Cerrar deuda."
                else "Se elimina la obligación y sus cuotas sin pagos asociados. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    enabled = !hasFinancialHistory,
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
    onHistory: () -> Unit,
    onMovement: (String) -> Unit,
) {
    val payable = debt.obligationType.name == "PAYABLE"
    val amortizedMinor = (debt.principalMinor - debt.remainingPrincipalMinor).coerceAtLeast(0L)
    val amortizedFraction = if (debt.principalMinor > 0L) {
        (amortizedMinor.toFloat() / debt.principalMinor).coerceIn(0f, 1f)
    } else 1f
    val amortizedPercent = (amortizedFraction * 100).toInt()
    val orderedInstallments = installments.sortedBy(DebtScheduleItem::installmentNumber)
    val nextInstallment = orderedInstallments.firstOrNull { it.status == "PENDING" || it.status == "PARTIAL" }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = emeraldColors.incomeBg, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.ReceiptLong, null, tint = emeraldColors.primaryDeep, modifier = Modifier.padding(14.dp).size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (payable) "DEUDA POR PAGAR" else "PRÉSTAMO POR COBRAR", style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
            Text(debt.counterpartyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("debt_balance_summary"),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailPill(if (payable) "Debo" else "Me deben", emeraldColors.primaryDeep, emeraldColors.incomeBg)
                DetailPill(if (debt.status == DebtLifecycleStatus.CANCELLED) "Cancelada" else "Activa",
                    if (debt.status == DebtLifecycleStatus.CANCELLED) emeraldColors.expenseText else emeraldColors.primaryDeep,
                    if (debt.status == DebtLifecycleStatus.CANCELLED) emeraldColors.expenseBg else emeraldColors.incomeBg)
                DetailPill(debt.currencyCode, emeraldColors.secondaryMuted, emeraldColors.pillTrack)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Saldo pendiente", style = MaterialTheme.typography.bodyMedium, color = emeraldColors.secondaryMuted)
                Text(formatter.format(debt.remainingPrincipalMinor / 100.0),
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = emeraldColors.primaryText)
            }
            LinearProgressIndicator(progress = { amortizedFraction }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = emeraldColors.incomeEmerald, trackColor = emeraldColors.incomeBg)
            Text("$amortizedPercent% del principal resuelto", style = MaterialTheme.typography.labelLarge, color = emeraldColors.primaryDeep)
            HorizontalDivider(color = emeraldColors.borderSubtle)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Monto original", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                    Text(formatter.format(debt.principalMinor / 100.0), fontWeight = FontWeight.Bold, color = emeraldColors.primaryText)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Principal resuelto", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                    Text(formatter.format(amortizedMinor / 100.0), fontWeight = FontWeight.Bold, color = emeraldColors.primaryDeep)
                }
            }
            Text("Registrada el ${formatSpanishDate(debt.openedOn)}", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
        }
    }
    errorMessage?.let { PlanningNotice("No se pudo completar la acción", it, isError = true) }

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
                Text("Plan de cuotas (no son pagos)", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 6) {
                    orderedInstallments.take(MAX_INSTALLMENT_CELLS).forEach { installment ->
                        val paid = installment.status == "PAID"
                        val next = installment == nextInstallment
                        val background = when {
                            paid -> emeraldColors.incomeBg
                            next -> emeraldColors.warningBg
                            else -> emeraldColors.pillTrack
                        }
                        val foreground = when {
                            paid -> emeraldColors.primaryDeep
                            next -> emeraldColors.warningText
                            else -> emeraldColors.secondaryMuted
                        }
                        Surface(color = background, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (paid) Icon(Icons.Rounded.Check, "Cuota ${installment.installmentNumber}: pagada", tint = foreground, modifier = Modifier.size(20.dp))
                                else Text(installment.installmentNumber.toString(), color = foreground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendDot(emeraldColors.incomeEmerald, "Pagada")
                    LegendDot(emeraldColors.warningAmber, "Próxima")
                    LegendDot(emeraldColors.borderSubtle, "Pendiente")
                }
                HorizontalDivider(color = emeraldColors.borderSubtle)
                orderedInstallments.take(3).forEach { installment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Cuota ${installment.installmentNumber} · ${formatSpanishDate(installment.dueDate)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (payable) "Historial de pagos" else "Historial de cobros",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = onHistory, modifier = Modifier.testTag("debt_history_all")) { Text("Ver todo", color = emeraldColors.primaryDeep) }
                }
                activities.take(3).forEach { activity ->
                    HorizontalDivider(color = emeraldColors.borderSubtle, modifier = Modifier.padding(top = 12.dp))
                    val displayedPrincipal = if (activity.eventType == "PAYMENT") {
                        activity.principalMinor
                    } else {
                        (activity.principalDeltaMinor ?: activity.principalMinor).absoluteValue
                    }
                    val reducesBalance = activity.eventType != "ADJUSTMENT" || (activity.principalDeltaMinor ?: 0L) < 0L
                    Row(Modifier.fillMaxWidth().clickable(enabled = activity.transactionId != null) {
                        activity.transactionId?.let(onMovement)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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

    if (orderedInstallments.size > 3) TextButton(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
        Text("Ver las ${orderedInstallments.size} cuotas", color = emeraldColors.primaryDeep)
    }

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
        Text(label, style = MaterialTheme.typography.bodySmall, color = rememberPlanningColors().secondaryMuted)
    }
}

private const val MAX_INSTALLMENT_CELLS = 12

@Composable
private fun LiquidatedDebtSuccessView(
    debt: DebtSummary,
    formatter: NumberFormat,
    activities: List<DebtSettlementActivity>,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    onHistory: () -> Unit,
    onReturn: () -> Unit,
) {
    val payable = debt.obligationType == DebtObligationType.PAYABLE
    val effectiveActivities = activities.filterNot { it.isVoided }
    val paidMinor = effectiveActivities.filter { it.eventType == "PAYMENT" }.sumOf { it.principalMinor }
    val condonedMinor = effectiveActivities.filter { it.eventType == "FORGIVENESS" }.sumOf { it.principalMinor }
    val adjustmentsMinor = effectiveActivities.filter { it.eventType == "ADJUSTMENT" }
        .sumOf { it.principalDeltaMinor ?: 0L }
    // The closing date is the last effective settlement event, never the current clock.
    val closedOn = effectiveActivities.maxOfOrNull { it.occurredAt }?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LiquidatedDebtCelebration(emeraldColors)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Deuda liquidada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, color = emeraldColors.primaryText)
            Text(listOfNotNull(debt.counterpartyName, closedOn?.let(::formatSpanishDate)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = emeraldColors.secondaryMuted, textAlign = TextAlign.Center)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(), color = emeraldColors.surfaceCard,
            shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettlementRow("Monto original", formatter.format(debt.principalMinor / 100.0))
                SettlementRow(if (payable) "Pagado" else "Cobrado", formatter.format(paidMinor / 100.0))
                if (condonedMinor != 0L) SettlementRow("Condonado", formatter.format(condonedMinor / 100.0))
                if (adjustmentsMinor != 0L) SettlementRow("Ajustes", formatter.format(adjustmentsMinor / 100.0))
                LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = emeraldColors.primaryDeep, trackColor = emeraldColors.pillTrack)
                Text(if (payable) "100 % pagado" else "100 % cobrado", style = MaterialTheme.typography.labelLarge,
                    color = emeraldColors.primaryDeep, fontWeight = FontWeight.Bold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep, contentColor = emeraldColors.onPrimaryDeep)) {
                Text("Ver historial", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onReturn, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, emeraldColors.borderSubtle)) {
                Text("Volver a Deudas", color = emeraldColors.primaryDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiquidatedDebtCelebration(colors: com.kipu.app.ui.theme.CalmEmeraldColors) {
    val reducedMotion = rememberReducedMotionEnabled()
    val pulse = if (!reducedMotion) {
        val transition = rememberInfiniteTransition(label = "halo")
        transition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "halo scale").value
    } else 1f
    val confetti = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) confetti.animateTo(1f, tween(1800)) else confetti.snapTo(1f)
    }
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        if (!reducedMotion && confetti.value < 1f) {
            val palette = listOf(colors.primaryDeep, colors.incomeText, colors.warningAmber, colors.expenseText)
            Canvas(Modifier.fillMaxSize()) {
                val progress = confetti.value
                repeat(24) { index ->
                    val x = size.width * (0.08f + ((index * 37) % 85) / 100f)
                    val y = size.height * (-0.15f + progress * (1.1f + (index % 4) * 0.12f) - (index % 5) * 0.08f)
                    drawCircle(palette[index % palette.size].copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                        radius = (3 + index % 3).dp.toPx(), center = Offset(x, y))
                }
            }
        }
        Box(Modifier.size(122.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
            .background(colors.incomeBg, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(86.dp).background(colors.primaryDeep, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimaryDeep, modifier = Modifier.size(48.dp))
            }
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
