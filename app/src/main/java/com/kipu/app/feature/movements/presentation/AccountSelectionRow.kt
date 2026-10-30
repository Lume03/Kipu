package com.kipu.app.feature.movements.presentation


import com.kipu.app.ui.theme.readableContentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberKipuColors


// Design tokens per docs/design/KIPU_UI_PASTEL_PROMPTS_AND_REFERENCE.md Reference A R3
// and the "Selector de cuenta (global)" board of the live canvas.
private val AndeanTealPrimary: Color @Composable get() = rememberKipuColors().primary
private val SurfaceRow: Color @Composable get() = rememberKipuColors().surface
private val SurfaceSelectedMint: Color @Composable get() = rememberKipuColors().selectedSurface
private val BorderNeutralSlate: Color @Composable get() = rememberKipuColors().border
private val TextPrimarySlate: Color @Composable get() = rememberKipuColors().inkPrimary
private val TextSecondarySlate: Color @Composable get() = rememberKipuColors().inkSecondary
private val DragHandleSlate: Color @Composable get() = rememberKipuColors().dragHandle


@Composable
internal fun AccountSelectionRow(
    alias: String,
    subtitle: String,
    type: AccountType,
    preset: AccountPreset?,
    balance: Money?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String = "Disponible",
    amountColor: Color = rememberKipuColors().inkPrimary,
    statusColor: Color = rememberKipuColors().positive,
    missingBalanceText: String = "Saldo no disponible",
    multiSelect: Boolean = false,
    testTag: String = "",
    contentDescription: String? = null,
) {
    val masked = LocalBalanceMasked.current
    val contentDesc = contentDescription ?: buildString {
        append("$alias. $subtitle. $statusText: ")
        append(if (masked) "Monto oculto" else balance?.let { "${it.currency.symbol()} ${formatMinorUnits(it.minorUnits)}" } ?: missingBalanceText)
        append(if (isSelected) ". Seleccionada" else ". No seleccionada")
    }
    SelectableInstrumentSurface(
        isSelected = isSelected,
        multiSelect = multiSelect,
        onSelect = onSelect,
        contentDesc = contentDesc,
        testTag = testTag,
        modifier = modifier,
    ) {
        AccountBadge(alias = alias, type = type, preset = preset)

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = alias,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimarySlate,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondarySlate,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            if (balance != null) {
                MoneyText(
                    money = balance,
                    color = amountColor,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    ),
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                )
            } else {
                Text(
                    text = missingBalanceText,
                    color = TextSecondarySlate,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(88.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        SelectionIndicator(selected = isSelected, multiSelect = multiSelect)
    }
}

@Composable
internal fun SelectableInstrumentSurface(
    isSelected: Boolean,
    multiSelect: Boolean,
    onSelect: () -> Unit,
    contentDesc: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val background = if (isSelected) SurfaceSelectedMint else SurfaceRow
    val borderColor = if (isSelected) AndeanTealPrimary else BorderNeutralSlate
    val shape = RoundedCornerShape(18.dp)
    val selection = if (multiSelect) {
        Modifier.toggleable(value = isSelected, onValueChange = { onSelect() }, role = Role.Checkbox)
    } else {
        Modifier.selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, borderColor), shape)
            .then(selection)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        color = background,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Bank and wallet rows use the shared brand logo; cash keeps the plain icon. */
@Composable
internal fun AccountBadge(
    account: Account,
    modifier: Modifier = Modifier,
) {
    AccountBadge(account.alias, account.type, account.preset, modifier)
}

@Composable
internal fun AccountBadge(alias: String, type: AccountType, preset: AccountPreset?, modifier: Modifier = Modifier) {
    if (type == AccountType.CASH) {
        val container = rememberKipuColors().positiveContainer
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = null,
                tint = readableContentColor(container),
                modifier = Modifier.size(24.dp),
            )
        }
        return
    }
    val brand = preset
        ?.takeIf { it != AccountPreset.GENERIC && it != AccountPreset.CASH }
        ?.let { KipuBrand.from(it.name) }
        ?: KipuBrand.from(alias)
    BrandLogo(
        brand = brand,
        fallbackName = alias,
        contentDescription = null,
        modifier = modifier,
        size = 44.dp,
    )
}

@Composable
internal fun SelectionIndicator(
    selected: Boolean,
    multiSelect: Boolean,
    modifier: Modifier = Modifier,
) {
    if (multiSelect) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier = modifier
                .size(24.dp)
                .clip(shape)
                .then(
                    if (selected) Modifier.background(AndeanTealPrimary)
                    else Modifier.border(BorderStroke(2.dp, DragHandleSlate), shape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = rememberKipuColors().onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    } else {
        // Radio ring as in the reference: a thick teal ring when selected.
        Box(
            modifier = modifier
                .size(24.dp)
                .border(
                    BorderStroke(if (selected) 7.dp else 2.dp, if (selected) AndeanTealPrimary else DragHandleSlate),
                    CircleShape,
                ),
        )
    }
}

