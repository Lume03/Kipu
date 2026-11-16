package com.kipu.app.feature.movements.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.theme.at
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.theme.rememberPastelTones
import java.util.Locale

/** Identical merchant trigger for movement entry and credit simulation. */
@Composable
fun MerchantSelectionRow(
    merchantName: String,
    onOpenMerchantPicker: () -> Unit,
    onClearMerchant: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val merchantBg = emeraldColors.warningBg
    val merchantTint = emeraldColors.warningText
    val hasMerchant = merchantName.isNotBlank()

    CalmEmeraldSelectionRow(
        icon = Icons.Default.Storefront,
        iconContainer = merchantBg,
        iconTint = merchantTint,
        label = "Comercio",
        value = if (hasMerchant) merchantName else "Agregar comercio",
        supportingText = null,
        onClick = onOpenMerchantPicker,
        modifier = Modifier.testTag("input_merchant"),
        trailingContent = if (hasMerchant) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClearMerchant,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_clear_merchant")
                            .semantics { contentDescription = "Limpiar comercio" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onOpenMerchantPicker,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Editar comercio" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = emeraldColors.secondaryMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/**
 * "Recordar…" switch shown under the category (and merchant) fields: the whole row toggles,
 * like the switch rows of the settings and filter forms.
 */
@Composable
fun RememberToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helper: String = "La próxima vez se elegirá automáticamente",
) {
    val emeraldColors = rememberCalmEmeraldColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = emeraldColors.primaryText,
            )
            Text(
                text = helper,
                style = MaterialTheme.typography.labelSmall,
                color = emeraldColors.secondaryMuted,
            )
        }
        // The row owns the toggle semantics; the switch is only its visual state.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = emeraldColors.onPrimaryDeep,
                checkedTrackColor = emeraldColors.primaryDeep,
            ),
        )
    }
}

/** Identical category trigger, including empty and error states. */
@Composable
fun CategorySelectionRow(
    categoryName: String?,
    categoryIcon: String?,
    required: Boolean,
    error: String?,
    onClick: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val hasCategory = !categoryName.isNullOrBlank()
    val (catIcon, catBg, catTint) = if (hasCategory) {
        val colorsPair = categoryPastelColors(categoryName.orEmpty())
        Triple(
            resolveCategoryIcon(categoryIcon ?: "label"),
            colorsPair.first,
            colorsPair.second,
        )
    } else {
        val tone = rememberPastelTones().at(3)
        Triple(Icons.Default.Label, tone.tile, tone.ink)
    }

    CalmEmeraldSelectionRow(
        icon = catIcon,
        iconContainer = catBg,
        iconTint = catTint,
        label = if (required) "Categoría *" else "Categoría (opcional)",
        value = categoryName ?: "Sin categoría seleccionada",
        // The label already says whether it is required; only an error needs a second line.
        supportingText = if (error != null) "Este campo es obligatorio" else null,
        onClick = onClick,
        isError = error != null,
        errorMessage = error,
        modifier = Modifier.testTag("category_picker_open"),
    )
}

@Composable
internal fun CalmEmeraldSelectionRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    label: String,
    value: String,
    supportingText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingActionText: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    brandName: String? = null,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val borderColor = if (isError) emeraldColors.expenseText else emeraldColors.borderSubtle

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = listOfNotNull(label, value, supportingText).joinToString(". ")
                role = Role.Button
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (brandName != null) BrandLogo(KipuBrand.from(brandName), brandName, null, size = 38.dp)
                    else Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) emeraldColors.expenseText else emeraldColors.secondaryMuted,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = emeraldColors.primaryText,
                    )
                    if (supportingText != null) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isError) emeraldColors.expenseText else emeraldColors.secondaryMuted,
                        )
                    }
                }

                if (trailingContent != null) {
                    trailingContent()
                } else if (trailingActionText != null && onTrailingActionClick != null) {
                    TextButton(
                        onClick = onTrailingActionClick,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = trailingActionText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.expenseText,
                        )
                    }
                } else {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = emeraldColors.secondaryMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (isError && errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "▲ $errorMessage",
                    style = MaterialTheme.typography.labelSmall,
                    color = emeraldColors.expenseText,
                )
            }
        }
    }
}

@Composable
private fun categoryPastelColors(categoryName: String): Pair<Color, Color> {
    val name = categoryName.lowercase(Locale.ROOT)
    val pastels = rememberPastelTones()
    val tone = when {
        "aliment" in name || "mercado" in name || "comida" in name || "restauran" in name -> pastels.at(2)
        "suscrip" in name || "stream" in name || "entreten" in name -> pastels.at(3)
        "transp" in name || "movil" in name || "auto" in name || "gas" in name -> pastels.at(1)
        "salud" in name || "cuidado" in name || "medic" in name || "farmac" in name -> pastels.at(4)
        "hogar" in name || "servicio" in name || "casa" in name || "luz" in name || "agua" in name -> pastels.at(5)
        "educ" in name || "libro" in name || "curso" in name -> pastels.at(0)
        "compr" in name || "ropa" in name || "tienda" in name -> pastels.at(4)
        else -> pastels.at(6)
    }
    return tone.tile to tone.ink
}
