package com.kipu.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/*
 * Form building blocks shared by "Editar movimiento" and "Revisar captura", so both flows read the
 * same: a big amount card on top and a grouped card of tappable rows with a tinted icon tile.
 */

/** Color role of a [KipuStatusChip]; the text always says the state, color only reinforces it. */
enum class KipuChipTone { NEUTRAL, POSITIVE, WARNING, DANGER }

@Composable
fun KipuStatusChip(
    text: String,
    tone: KipuChipTone,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val ec = rememberCalmEmeraldColors()
    val (bg, fg, border) = when (tone) {
        KipuChipTone.NEUTRAL -> Triple(ec.pillTrack, ec.secondaryMuted, null)
        KipuChipTone.POSITIVE -> Triple(ec.incomeBg, ec.primaryDeep, ec.incomeBorder)
        KipuChipTone.WARNING -> Triple(ec.warningBg, ec.warningText, ec.warningBorder)
        KipuChipTone.DANGER -> Triple(ec.expenseBg, ec.expenseText, ec.expenseBorder)
    }
    Surface(
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = border?.let { BorderStroke(1.dp, it) },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
            )
        }
    }
}

/** Rounded card that groups [KipuFieldRow]s, separated by [KipuFieldRowDivider]. */
@Composable
fun KipuFieldGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ec = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ec.surfaceCard,
        border = BorderStroke(1.dp, ec.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
fun KipuFieldRowDivider() {
    HorizontalDivider(color = rememberCalmEmeraldColors().borderSubtle, modifier = Modifier.padding(start = 68.dp))
}

/**
 * One tappable field: icon tile, label over value, optional status [badge], chevron. [before] shows
 * the struck-through previous value when [modified]. [leading] replaces the icon tile (e.g. a bank logo).
 */
@Composable
fun KipuFieldRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    value: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueMuted: Boolean = false,
    modified: Boolean = false,
    before: String? = null,
    badge: (@Composable () -> Unit)? = null,
    error: String? = null,
    errorTag: String = "",
    hint: String? = null,
    hintColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    description: String? = null,
) {
    val ec = rememberCalmEmeraldColors()
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(semantics)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(tag),
        ) {
            if (leading != null) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
            } else {
                KipuIconTile(icon, iconBg, iconTint)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = ec.secondaryMuted)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (valueMuted) ec.secondaryMuted else ec.primaryText,
                )
                if (modified && before != null) {
                    Text(
                        text = "Antes: $before",
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = ec.secondaryMuted,
                    )
                }
            }
            val trailingBadge: (@Composable () -> Unit)? = badge ?: if (modified) ({ KipuStatusChip("Modificado", KipuChipTone.POSITIVE) }) else null
            if (trailingBadge != null) {
                Spacer(Modifier.width(8.dp))
                trailingBadge()
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ec.secondaryMuted,
            )
        }
        val note = error ?: hint
        if (note != null) {
            Text(
                text = note,
                color = if (error != null) ec.expenseText else hintColor ?: ec.secondaryMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (error != null) FontWeight.Medium else FontWeight.Normal),
                modifier = Modifier
                    .padding(start = 68.dp, end = 16.dp, bottom = 10.dp)
                    .then(if (error != null && errorTag.isNotEmpty()) Modifier.testTag(errorTag) else Modifier),
            )
        }
    }
}

@Composable
fun KipuIconTile(icon: ImageVector, background: Color, tint: Color, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(background, RoundedCornerShape(size * 0.3f)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Big editable amount: label + status chip on top, currency symbol and a display-size input below.
 * The border thickens and takes [accent] while the value is modified or wrong.
 */
@Composable
fun KipuAmountCard(
    label: String,
    symbol: String,
    amountText: String,
    onAmountChanged: (String) -> Unit,
    amountColor: Color,
    inputTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    placeholder: String = "0.00",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    inputModifier: Modifier = Modifier,
    chip: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ec = rememberCalmEmeraldColors()
    val borderColor by animateColorAsState(accent ?: ec.borderSubtle, label = "amountBorder")
    val borderWidth by animateDpAsState(if (accent != null) 2.dp else 1.dp, label = "amountBorderWidth")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ec.surfaceCard,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ec.primaryText,
                )
                chip?.invoke()
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (symbol.isNotBlank()) {
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ec.secondaryMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                    if (amountText.isEmpty()) {
                        Text(placeholder, style = style, color = ec.secondaryMuted.copy(alpha = 0.6f))
                    }
                    BasicTextField(
                        value = amountText,
                        onValueChange = onAmountChanged,
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = visualTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = style.copy(color = amountColor),
                        cursorBrush = SolidColor(ec.primaryDeep),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag(inputTag)
                            .then(inputModifier),
                    )
                }
            }
            footer?.invoke(this)
        }
    }
}

/** Two-or-more option pill selector (Gasto / Ingreso, Directo / En cuotas). */
@Composable
fun <T> KipuSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ((T) -> ImageVector?)? = null,
    selectedColors: @Composable (T) -> Pair<Color, Color> = { rememberSegmentDefault() },
    tag: (T) -> String = { "" },
) {
    val ec = rememberCalmEmeraldColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ec.pillTrack, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .alpha(if (enabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val (selBg, selFg) = selectedColors(option)
            val bg by animateColorAsState(if (isSelected) selBg else Color.Transparent, label = "segmentBg")
            val fg by animateColorAsState(if (isSelected) selFg else ec.secondaryMuted, label = "segmentFg")
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(bg, RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled && !isSelected, role = Role.RadioButton) { onSelect(option) }
                    .semantics { this.selected = isSelected }
                    .then(tag(option).takeIf(String::isNotEmpty)?.let { Modifier.testTag(it) } ?: Modifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke(option)?.let {
                    Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label(option),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = fg,
                )
            }
        }
    }
}

@Composable
private fun rememberSegmentDefault(): Pair<Color, Color> {
    val ec = rememberCalmEmeraldColors()
    return ec.surfaceCard to ec.primaryDeep
}
