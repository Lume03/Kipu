package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.theme.KipuModalSystemBars
import com.kipu.app.ui.theme.kipuBottomSheetProperties
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Name shown for a bank in account flows ("Otro banco" for the generic entry). */
internal fun accountBankLabel(bank: BankChoice): String = if (bank.code == "OTHER") "Otro banco" else bank.name

/** Bank logo tile: bundled brand logo, or a neutral "..." tile for the generic entry. */
@Composable
internal fun BankLogoTile(bank: BankChoice, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val brand = KipuBrand.from(bank.code)
    if (bank.code == "OTHER" || brand == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(Color(0xFF475569))
                .semantics { contentDescription = accountBankLabel(bank) },
            contentAlignment = Alignment.Center,
        ) {
            Text("…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    } else {
        BrandLogo(brand = brand, fallbackName = bank.name, contentDescription = bank.name, size = size, modifier = modifier)
    }
}

@Composable
internal fun AddFormTopBar(title: String, onBack: () -> Unit) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(color = calmColors.background, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = calmColors.primaryText)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
                color = calmColors.primaryText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Section label with an optional right-aligned tag ("Obligatorio", "Opcional · por defecto 0"). */
@Composable
internal fun AddFormLabel(label: String, tag: String? = null, isError: Boolean = false, modifier: Modifier = Modifier) {
    val calmColors = rememberCalmEmeraldColors()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
            color = if (isError) calmColors.expenseText else calmColors.primaryText,
            modifier = Modifier.weight(1f),
        )
        if (tag != null) {
            Text(tag, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp), color = calmColors.secondaryMuted)
        }
    }
}

/** Single-line field with optional prefix, inline error (icon + text) and helper text. */
@Composable
internal fun AddFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    errorMessage: String? = null,
    hint: String? = null,
) {
    val calmColors = rememberCalmEmeraldColors()
    val isError = errorMessage != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = calmColors.secondaryMuted) },
            prefix = prefix?.let { { Text(it, fontWeight = FontWeight.Bold, color = calmColors.secondaryMuted, modifier = Modifier.padding(end = 8.dp)) } },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = calmColors.surfaceCard,
                unfocusedContainerColor = calmColors.surfaceCard,
                errorContainerColor = calmColors.surfaceCard,
                focusedBorderColor = calmColors.primaryDeep,
                unfocusedBorderColor = calmColors.borderSubtle,
                errorBorderColor = calmColors.expenseText,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        if (errorMessage != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = calmColors.expenseText, modifier = Modifier.size(16.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = calmColors.expenseText)
            }
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = calmColors.secondaryMuted)
        }
    }
}

/** Selected-state chip used for account type and currency. */
@Composable
internal fun AddFormChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) calmColors.primaryDeep.copy(alpha = 0.16f) else calmColors.surfaceCard,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) calmColors.primaryDeep else calmColors.borderSubtle),
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                color = if (selected) calmColors.primaryDeep else calmColors.primaryText,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddFormChoiceRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/** Bank row for "Banco" with the current logo and a "Cambiar" action. */
@Composable
internal fun AddFormBankField(bank: BankChoice, onClick: () -> Unit) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Banco ${accountBankLabel(bank)}. Cambiar" },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BankLogoTile(bank, 48.dp)
            Text(
                accountBankLabel(bank),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = calmColors.primaryText,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Cambiar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = calmColors.primaryDeep)
        }
    }
}

/** Large Yape/Plin selector: logo tile + name, with a check when selected. */
@Composable
internal fun AddFormWalletProviderCard(brand: KipuBrand, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) calmColors.incomeBg else calmColors.surfaceCard,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) calmColors.primaryDeep else calmColors.borderSubtle),
        modifier = modifier
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandLogo(brand = brand, fallbackName = label, contentDescription = null, size = 72.dp)
                Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = calmColors.primaryText)
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(calmColors.primaryDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = calmColors.onPrimaryDeep, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Radio card for the wallet balance mode ("Saldo propio" / "Afiliada a una cuenta"). */
@Composable
internal fun AddFormBalanceModeCard(
    isLink: Boolean,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val calmColors = rememberCalmEmeraldColors()
    val tileColor = if (isLink) Color(0xFFDCE8F7) else Color(0xFFEBDDF5)
    val iconColor = if (isLink) Color(0xFF1E4FA8) else Color(0xFF6B2FA0)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) calmColors.incomeBg else calmColors.surfaceCard,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) calmColors.primaryDeep else calmColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(tileColor), contentAlignment = Alignment.Center) {
                Icon(if (isLink) Icons.Default.Link else Icons.Default.Wallet, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = calmColors.primaryText)
                Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = calmColors.secondaryMuted)
            }
            RadioDot(selected)
        }
    }
}

/** Candidate bank account for an affiliated wallet. */
@Composable
internal fun AddFormLinkedAccountRow(bank: BankChoice?, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) calmColors.incomeBg else calmColors.surfaceCard,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) calmColors.primaryDeep else calmColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bank != null) BankLogoTile(bank, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = calmColors.primaryText)
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = calmColors.secondaryMuted)
            }
            RadioDot(selected)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val calmColors = rememberCalmEmeraldColors()
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(if (selected) 7.dp else 2.dp, if (selected) calmColors.primaryDeep else calmColors.borderSubtle, CircleShape),
    )
}

/** Bottom bar: primary save action plus a status line ("Todo listo para guardar" / review hint). */
@Composable
internal fun AddFormBottomBar(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    hasErrors: Boolean,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = calmColors.primaryDeep, contentColor = calmColors.onPrimaryDeep),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_save_instrument"),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                if (hasErrors) "Revisa los campos marcados en rojo" else "Todo listo para guardar",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (hasErrors) calmColors.expenseText else calmColors.secondaryMuted,
            )
        }
    }
}


/** "Elegir banco" sheet for bank accounts: one row per bank with its logo and a check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFormBankPickerSheet(
    visible: Boolean,
    currentBankCode: String,
    banks: List<BankChoice>,
    onConfirm: (BankChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val calmColors = rememberCalmEmeraldColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tempCode by rememberSaveable(currentBankCode) { mutableStateOf(currentBankCode) }
    ModalBottomSheet(
        properties = kipuBottomSheetProperties(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = calmColors.background,
    ) {
        KipuModalSystemBars()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Elegir banco",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = calmColors.primaryText,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Cerrar", tint = calmColors.primaryText)
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                banks.forEach { bank ->
                    val selected = bank.code == tempCode
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) calmColors.incomeBg else calmColors.surfaceCard,
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) calmColors.primaryDeep else calmColors.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable(role = Role.RadioButton) { tempCode = bank.code }
                            .semantics(mergeDescendants = true) { this.selected = selected },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            BankLogoTile(bank, 48.dp)
                            Text(accountBankLabel(bank), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = calmColors.primaryText, modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = calmColors.primaryDeep, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            Button(
                onClick = { onConfirm(banks.firstOrNull { it.code == tempCode } ?: banks.first()) },
                colors = ButtonDefaults.buttonColors(containerColor = calmColors.primaryDeep, contentColor = calmColors.onPrimaryDeep),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Listo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
