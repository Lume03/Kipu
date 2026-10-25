package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Section heading used by the debt form and the payment sheet (D3, D5). */
@Composable
internal fun DebtSectionLabel(text: String, modifier: Modifier = Modifier, optionalHint: String? = null) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        optionalHint?.let {
            Text(" $it", style = MaterialTheme.typography.bodyMedium, color = rememberCalmEmeraldColors().secondaryMuted)
        }
    }
}

internal fun debtCurrencySymbol(currencyCode: String): String = if (currencyCode == "USD") "US$" else "S/"

internal fun debtMoney(currencyCode: String, minor: Long): String = "${debtCurrencySymbol(currencyCode)} ${formatMinorUnits(minor)}"

/** Big amount entry on a card: label, optional PEN/USD toggle and the typed value with its symbol. */
@Composable
internal fun DebtAmountCard(
    label: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    currencyCode: String,
    modifier: Modifier = Modifier,
    onCurrencyChange: ((String) -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
    footer: @Composable () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = colors.secondaryMuted, modifier = Modifier.weight(1f))
                if (onCurrencyChange != null) DebtCurrencyToggle(currencyCode, onCurrencyChange, enabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    debtCurrencySymbol(currencyCode),
                    color = colors.primaryDeep,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(colors.primaryDeep),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = label }
                        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (amount.isEmpty()) {
                                Text(
                                    "0.00",
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = colors.secondaryMuted.copy(alpha = 0.45f),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
            footer()
        }
    }
}

@Composable
private fun DebtCurrencyToggle(selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    val colors = rememberCalmEmeraldColors()
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.pillTrack)
            .padding(3.dp),
    ) {
        listOf("PEN", "USD").forEach { currency ->
            val isSelected = currency == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else colors.pillTrack)
                    .selectable(isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(currency) }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    currency,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colors.primaryDeep else colors.secondaryMuted,
                )
            }
        }
    }
}

/** Selectable account row (radio, name, detail and balance) for the form and the payment sheet. */
@Composable
internal fun DebtAccountOptionCard(
    name: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    balance: String? = null,
) {
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.incomeBg else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primaryDeep else colors.borderSubtle),
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = colors.primaryDeep),
            )
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.secondaryMuted,
                )
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
            balance?.let {
                Text(
                    if (masked) "••••" else it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
