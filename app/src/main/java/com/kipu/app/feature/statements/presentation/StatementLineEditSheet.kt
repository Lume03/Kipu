package com.kipu.app.feature.statements.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.movements.presentation.AccountSelectorBottomSheet
import com.kipu.app.feature.movements.presentation.AccountSelectorPurpose
import com.kipu.app.feature.receipts.presentation.tealTint
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.ResolverStyle

private fun StatementLineKind.icon(): ImageVector = when (this) {
    StatementLineKind.REFUND -> Icons.Filled.Replay
    StatementLineKind.INTEREST -> Icons.Filled.Percent
    StatementLineKind.INSURANCE -> Icons.Filled.Shield
    StatementLineKind.FEE -> Icons.Filled.ReceiptLong
    StatementLineKind.UNKNOWN -> Icons.Filled.Visibility
    else -> Icons.Filled.ShoppingBag
}

/** One sheet for every correction: big amount, description, date, type with icons, category and (for payments) the source account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatementLineEditSheet(
    line: StatementImportLine,
    state: StatementReviewUiState,
    source: StatementImport,
    onDismiss: () -> Unit,
    onSave: (StatementImportLineEdit) -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val context = LocalContext.current
    var description by remember(line.id) { mutableStateOf(line.description) }
    var amount by remember(line.id) { mutableStateOf(BigDecimal.valueOf(line.amountMinor, 2).toPlainString()) }
    var date by remember(line.id) { mutableStateOf(line.occurredDate.format(statementDateFormat)) }
    var category by remember(line.id) { mutableStateOf(line.categoryId) }
    var sourceAccount by remember(line.id) { mutableStateOf(line.sourceAccountId) }
    var kind by remember(line.id) { mutableStateOf(line.kind) }
    var showCategory by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val parsedAmount = MoneyInputParser.parseMinorUnits(amount)?.takeIf { it > 0 }
    val parsedDate = runCatching { LocalDate.parse(date,
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
    val validDescription = description.trim().length in 1..120
    val valid = parsedAmount != null && parsedDate != null && validDescription
    val bad = statementTone("bad")
    when {
        showCategory -> StatementCategoryPicker(state.categories, category, onSelect = { category = it.id; showCategory = false }, onDismiss = { showCategory = false })
        showSource -> AccountSelectorBottomSheet(onDismissRequest = { showSource = false },
            accounts = state.accounts.filter { it.currency == line.currency },
            selectedAccountId = sourceAccount, allowCreditCards = false, purpose = AccountSelectorPurpose.CARD_PAYMENT,
            title = "Cuenta de origen", subtitle = "Elige desde dónde pagaste la tarjeta", showNewAccountAction = false,
            onAccountSelected = { sourceAccount = it.linkedAccountId?.value ?: it.id.value; showSource = false })
        else -> KipuBottomSheet(onDismissRequest = { if (!state.busy) onDismiss() },
            containerColor = palette.surfaceCard,
            header = {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Editar movimiento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatementBankTile(source.issuer, size = 24.dp)
                        Text("Del estado ${source.issuer} •••• ${source.cardLastFour} · ${line.occurredDate.dayOfMonth}/${line.occurredDate.monthValue}",
                            style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                    }
                }
            },
            footer = {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Button(onClick = {
                        attempted = true
                        if (valid) onSave(StatementImportLineEdit(description.trim(), requireNotNull(parsedAmount), requireNotNull(parsedDate),
                            category, kind, line.merchantId, sourceAccount))
                    }, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("statement_save_line")) {
                        Text("Guardar cambios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Cancelar", fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
                    }
                }
            }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.message?.let { StatementNotice(it, failed = true) }
                // Amount: the number that matters most, large and on its own surface.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.pillTrack)
                    .border(1.5.dp, if (attempted && parsedAmount == null) bad.fg else Color.Transparent, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("IMPORTE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = palette.secondaryMuted, letterSpacing = .6.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(statementSymbol(line.currency).trim(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.secondaryMuted)
                        TextField(amount, { amount = it }, singleLine = true, enabled = !state.busy, modifier = Modifier.weight(1f).testTag("statement_edit_amount"),
                            textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = palette.primaryText),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                                errorContainerColor = Color.Transparent))
                    }
                    if (attempted && parsedAmount == null) ErrorLine("Escribe un importe mayor a 0")
                    else Text("Leído del PDF: ${BigDecimal.valueOf(line.amountMinor, 2).toPlainString()}", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                }
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, enabled = !state.busy, singleLine = false, maxLines = 2,
                    leadingIcon = { Icon(Icons.Filled.Storefront, null, tint = palette.secondaryMuted) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("statement_edit_description"), isError = attempted && !validDescription,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.primaryDeep),
                    supportingText = { Text(if (attempted && !validDescription) "La descripción es obligatoria (hasta 120 caracteres)" else "${description.trim().length}/120") })
                OutlinedTextField(date, { date = it }, label = { Text("Fecha del movimiento") }, singleLine = true, enabled = !state.busy,
                    leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = palette.secondaryMuted) },
                    trailingIcon = { Icon(Icons.Filled.ExpandMore, null, tint = palette.secondaryMuted) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("statement_edit_date"), placeholder = { Text("dd/mm/aaaa") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false), isError = attempted && parsedDate == null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.primaryDeep),
                    supportingText = if (attempted && parsedDate == null) ({ Text("Ingresa una fecha válida: dd/mm/aaaa") }) else null)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("TIPO")
                    if (line.kind in reclassifiableKinds) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(StatementLineKind.PURCHASE, StatementLineKind.REFUND, StatementLineKind.INTEREST, StatementLineKind.INSURANCE, StatementLineKind.FEE, StatementLineKind.UNKNOWN)
                            .filter { it in reclassifiableKinds }.forEach { choice ->
                                val on = kind == choice
                                Surface(onClick = { kind = choice }, enabled = !state.busy, shape = RoundedCornerShape(22.dp), color = if (on) palette.primaryDeep else palette.surfaceCard,
                                    contentColor = if (on) palette.onPrimaryDeep else palette.primaryText,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (on) palette.primaryDeep else palette.borderSubtle),
                                    modifier = Modifier.heightIn(min = 44.dp).testTag("statement_kind_${choice.name.lowercase()}")) {
                                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Icon(choice.icon(), null, Modifier.size(18.dp)); Text(choice.label(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                    } else StatementKindChip(kind, line.installmentCount)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("CATEGORÍA")
                    val picked = state.categories.find { it.id == category }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (picked == null) statementTone("warn").bg else palette.surfaceCard)
                        .border(1.5.dp, if (picked == null) statementTone("warn").border else palette.borderSubtle, RoundedCornerShape(16.dp))
                        .clickable(enabled = !state.busy) { showCategory = true }.padding(horizontal = 12.dp, vertical = 10.dp).testTag("statement_edit_category"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatementCategoryTile(picked, size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(picked?.displayName ?: "Elegir categoría", fontWeight = FontWeight.Bold, color = if (picked == null) statementTone("warn").fg else palette.primaryText)
                            if (picked == null) Text("Se necesita para registrar las compras", style = MaterialTheme.typography.bodySmall, color = statementTone("warn").fg)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = palette.secondaryMuted)
                    }
                }
                if (kind == StatementLineKind.CARD_PAYMENT) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("CUENTA DE ORIGEN")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.5.dp, palette.borderSubtle, RoundedCornerShape(16.dp))
                        .clickable(enabled = !state.busy) { showSource = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.AccountBalance, null, tint = palette.primaryDeep)
                        Text(state.accounts.find { (it.linkedAccountId?.value ?: it.id.value) == sourceAccount }?.alias ?: "Elegir cuenta de origen",
                            Modifier.weight(1f), fontWeight = FontWeight.Bold, color = palette.primaryText)
                        Icon(Icons.Filled.ChevronRight, null, tint = palette.secondaryMuted)
                    }
                }
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp, color = rememberCalmEmeraldColors().secondaryMuted)
}

@Composable
private fun ErrorLine(text: String) {
    val bad = statementTone("bad")
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.WarningAmber, null, Modifier.size(14.dp), tint = bad.fg)
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = bad.fg)
    }
}
