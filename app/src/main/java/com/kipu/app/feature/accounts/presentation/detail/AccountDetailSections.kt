package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.KipuSkeletonBlock
import com.kipu.app.ui.component.loadingSemantics
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.feature.movements.presentation.privateAmount
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Tint of the summary card: mint for cash, the brand color for banks and wallets, teal otherwise. */
@Composable
private fun accountSummaryContainer(account: Account): Color {
    val colors = rememberCalmEmeraldColors()
    val surface = MaterialTheme.colorScheme.surface
    val brand = account.preset?.let { KipuBrand.from(it.name) }
    return when {
        account.type == AccountType.CASH -> colors.incomeBg
        brand != null -> brand.brandColor.copy(alpha = if (colors.isDark) 0.22f else 0.12f).compositeOver(surface)
        else -> colors.primaryDeep.copy(alpha = if (colors.isDark) 0.22f else 0.10f).compositeOver(surface)
    }
}

private fun Account.subtitle(linkedAccount: Account?): String {
    val institution = preset?.takeIf { it != AccountPreset.CASH && it != AccountPreset.GENERIC }?.defaultName
    return when (type) {
        AccountType.CASH -> "${currency.name} · en bolsillo"
        AccountType.DIGITAL_WALLET -> when {
            linkedAccount != null -> "${institution ?: "Billetera"} · afiliada a ${linkedAccount.alias}"
            else -> "${institution ?: "Billetera"} · saldo propio · ${currency.name}"
        }
        AccountType.SAVINGS -> listOfNotNull(institution, "Ahorros", currency.name).joinToString(" · ")
        AccountType.BANK -> listOfNotNull(institution, "Corriente", currency.name).joinToString(" · ")
        AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta · ${currency.name}"
    }
}

private fun Account.kindLabel(): String = when (type) {
    AccountType.CASH -> "Efectivo"
    AccountType.SAVINGS, AccountType.BANK -> "Cuenta bancaria"
    AccountType.DIGITAL_WALLET -> "Billetera digital"
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

/** C1-C3: logo, kind, institution line, current balance and the shared-balance tag of a wallet. */
@Composable
internal fun AccountSummaryCard(
    account: Account,
    currentBalance: Money?,
    linkedAccount: Account?,
    affiliatedWallets: List<Account>,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val textColor = colors.primaryText
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accountSummaryContainer(account)),
        modifier = modifier.fillMaxWidth().testTag("account_summary_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (account.type == AccountType.CASH) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, tint = colors.incomeText)
                    }
                } else {
                    BrandLogo(
                        brand = account.preset?.let { KipuBrand.from(it.name) },
                        fallbackName = account.alias,
                        contentDescription = account.preset?.defaultName?.let { "Logo de $it" },
                        size = 48.dp,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = account.kindLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                    Text(
                        text = account.subtitle(linkedAccount),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryMuted,
                    )
                }
            }
            if (currentBalance != null) {
                Text(
                    text = if (account.isArchived) "Saldo inicial registrado" else "Saldo actual",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                MoneyText(
                    money = if (account.isArchived) account.initialBalance else currentBalance,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                )
            } else if (account.isArchived) {
                Text("Saldo inicial registrado", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = textColor)
                MoneyText(
                    money = account.initialBalance,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                )
            }
            if (linkedAccount != null) {
                SharedBalanceTag("Mismo saldo que ${linkedAccount.alias}")
            }
            affiliatedWallets.forEach { wallet ->
                SharedBalanceTag("${wallet.alias} comparte este saldo")
            }
        }
    }
}

@Composable
private fun SharedBalanceTag(text: String) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** C1-C3 quick actions. Spend and transfer only appear when the host wires them. */
@Composable
internal fun AccountQuickActions(
    onSpend: (() -> Unit)?,
    onTransfer: (() -> Unit)?,
    onMovements: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onSpend == null && onTransfer == null && onMovements == null) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        onSpend?.let { QuickActionTile("Gasto", Icons.Rounded.NorthEast, it, Modifier.weight(1f).testTag("account_action_spend")) }
        onTransfer?.let { QuickActionTile("Transferir", Icons.Rounded.SwapHoriz, it, Modifier.weight(1f).testTag("account_action_transfer")) }
        onMovements?.let {
            QuickActionTile("Movimientos", Icons.AutoMirrored.Rounded.ReceiptLong, it, Modifier.weight(1f).testTag("account_view_movements"))
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            )
        }
    }
}

@Composable
internal fun AccountSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = rememberCalmEmeraldColors().primaryDeep,
        modifier = modifier,
    )
}

/** C4-C5: alias, institution (not for cash) and the save action, all inside one card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPresentationCard(
    account: Account,
    alias: String,
    aliasError: String?,
    onAliasChange: (String) -> Unit,
    selectedPreset: AccountPreset?,
    onPresetSelected: (AccountPreset) -> Unit,
    isInstitutionMenuExpanded: Boolean,
    onInstitutionMenuExpandedChange: (Boolean) -> Unit,
    isSubmitting: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val selectablePresets = AccountPreset.selectableFor(account.type)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = alias,
                onValueChange = onAliasChange,
                label = { Text("Alias de la cuenta") },
                singleLine = true,
                isError = aliasError != null,
                supportingText = aliasError?.let { { Text(it) } },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("account_alias"),
            )
            if (account.type == AccountType.CASH) {
                ReadOnlyKindRow(
                    icon = Icons.Rounded.Payments,
                    text = "Cuenta de efectivo · ${account.currency.name}",
                    modifier = Modifier.testTag("cash_presentation"),
                )
            } else if (selectablePresets.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = isInstitutionMenuExpanded,
                    onExpandedChange = onInstitutionMenuExpandedChange,
                    modifier = Modifier.fillMaxWidth().testTag("account_preset_selector"),
                ) {
                    OutlinedTextField(
                        value = selectablePresets.firstOrNull { it == selectedPreset }?.defaultName.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Institución") },
                        leadingIcon = {
                            BrandLogo(
                                brand = selectedPreset?.let { KipuBrand.from(it.name) },
                                fallbackName = selectedPreset?.defaultName ?: account.alias,
                                contentDescription = null,
                                size = 28.dp,
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isInstitutionMenuExpanded) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .testTag("account_institution_selector"),
                    )
                    ExposedDropdownMenu(
                        expanded = isInstitutionMenuExpanded,
                        onDismissRequest = { onInstitutionMenuExpandedChange(false) },
                    ) {
                        selectablePresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.defaultName) },
                                leadingIcon = {
                                    BrandLogo(
                                        brand = KipuBrand.from(preset.name),
                                        fallbackName = preset.defaultName,
                                        contentDescription = null,
                                        size = 28.dp,
                                    )
                                },
                                onClick = { onPresetSelected(preset) },
                                modifier = Modifier.testTag("account_preset_${preset.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = !isSubmitting && alias.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_account_presentation"),
            ) {
                Text(if (isSubmitting) "Guardando..." else "Guardar presentación", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReadOnlyKindRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        color = colors.incomeBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.incomeText)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.incomeText,
                modifier = Modifier.weight(1f),
            )
            Text("No editable", style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
        }
    }
}

/** C6: audited opening balance correction. Keeps the reverse + adjustment semantics of the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpeningBalanceCorrectionSheet(
    account: Account,
    currentBalance: Money?,
    amountText: String,
    amountError: String?,
    parsedMinorUnits: Long?,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val difference = parsedMinorUnits?.let { it - account.initialBalance.minorUnits }
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Corregir saldo inicial",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }
        },
        footer = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp).testTag("apply_opening_correction"),
            ) { Text("Aplicar corrección", fontWeight = FontWeight.Bold) }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Saldo inicial registrado", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    MoneyText(account.initialBalance, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text("Saldo inicial correcto (${account.currency.name})") },
                prefix = { Text(account.currency.symbol() + " ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("opening_amount")
                    .privateAmount(LocalBalanceMasked.current, true, "Saldo inicial oculto", onAmountChange),
            )
            if (difference != null && difference != 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Diferencia", style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                    MoneyText(
                        amount = (if (difference > 0) "+" else "-") + com.kipu.app.ui.component.formatMinorUnits(kotlin.math.abs(difference)),
                        currencySymbol = account.currency.symbol(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (difference > 0) colors.incomeText else colors.expenseText,
                    )
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = colors.incomeBg) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Se conserva el movimiento de apertura original y se registra su reverso junto con el importe corregido. Tu historial no se borra.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.incomeText,
                    )
                    if (currentBalance != null && difference != null && !account.isArchived) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Saldo actual", style = MaterialTheme.typography.bodyMedium, color = colors.incomeText)
                            MoneyText(currentBalance, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.incomeText)
                            Text("→", color = colors.incomeText)
                            MoneyText(
                                Money(currentBalance.minorUnits + difference, currentBalance.currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.incomeText,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/** C7-C8: archive confirmation body notes (affiliated wallets and remaining balance). */
@Composable
internal fun ColumnScope.ArchiveAccountNotes(
    affiliatedWallets: List<Account>,
    remainingBalance: Money?,
) {
    val colors = rememberCalmEmeraldColors()
    affiliatedWallets.forEach { wallet ->
        ArchiveNote("${wallet.alias} está afiliada a esta cuenta y comparte su saldo.", colors.warningBg, colors.warningText)
    }
    if (remainingBalance != null && remainingBalance.minorUnits > 0L) {
        Surface(shape = RoundedCornerShape(12.dp), color = colors.warningBg, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aún tiene saldo (", style = MaterialTheme.typography.bodySmall, color = colors.warningText)
                MoneyText(remainingBalance, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.warningText)
                Text("). Considera transferirlo antes de archivar.", style = MaterialTheme.typography.bodySmall, color = colors.warningText)
            }
        }
    }
}

@Composable
private fun ArchiveNote(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun ColumnScope.ArchiveDebtNote(debt: Money) {
    val colors = rememberCalmEmeraldColors()
    Surface(shape = RoundedCornerShape(12.dp), color = colors.warningBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tienes deuda pendiente de", style = MaterialTheme.typography.bodySmall, color = colors.warningText)
            MoneyText(debt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.warningText)
        }
    }
    Text(
        "La deuda se conserva; reactiva la tarjeta para registrar el pago.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.secondaryMuted,
    )
}

/** C10 / T loading: placeholders shaped like the final content, announced once to TalkBack. */
@Composable
internal fun AccountDetailSkeleton(isCard: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag("account_detail_loading")
            .loadingSemantics("Cargando detalle"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(4.dp))
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = if (isCard) 204.dp else 160.dp, cornerRadius = 24.dp)
        if (isCard) {
            KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 220.dp, cornerRadius = 22.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { KipuSkeletonBlock(Modifier.weight(1f), height = 72.dp, cornerRadius = 18.dp) }
            }
            KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 180.dp, cornerRadius = 20.dp)
        }
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 52.dp, cornerRadius = 16.dp)
    }
}
