package com.kipu.app.feature.statements.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.presentation.components.MerchantAvatar
import com.kipu.app.feature.categories.presentation.components.MerchantLogoDrawables
import com.kipu.app.feature.categories.presentation.components.getMerchantVisualProfile
import com.kipu.app.feature.statements.domain.StatementImportLine
import com.kipu.app.feature.statements.domain.StatementLearningWarning
import com.kipu.app.feature.statements.domain.StatementLearningWarningKind
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Catalog logo of a merchant, the same avatar the merchant picker shows. */
@Composable
internal fun StatementMerchantLogo(merchant: MerchantCatalogEntry, size: Dp) {
    MerchantAvatar(
        drawableId = MerchantLogoDrawables.resolve(merchant.logoKey),
        remoteLogoUrl = merchant.logoUrl,
        remoteDarkLogoUrl = merchant.logoDarkUrl,
        profile = getMerchantVisualProfile(merchant.name, merchant.brandColor),
        size = size,
    )
}

/** Merchant the statement detected but the person has not confirmed yet. */
internal fun StatementImportLine.pendingSuggestion(merchants: List<MerchantCatalogEntry>): MerchantCatalogEntry? =
    suggestedMerchantId?.takeIf { merchantId == null && !merchantReviewed }?.let { id -> merchants.firstOrNull { it.id.value == id } }

/** Compact "¿Es {Comercio}?" chip: accepting sets the merchant, rejecting hides the suggestion. */
@Composable
internal fun StatementMerchantSuggestion(
    lineId: String,
    merchant: MerchantCatalogEntry,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val tone = statementTone("teal")
    Surface(shape = RoundedCornerShape(50), color = tone.bg, border = BorderStroke(1.dp, tone.border),
        modifier = Modifier.testTag("statement_suggestion_$lineId")) {
        Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatementMerchantLogo(merchant, size = 22.dp)
            Text("¿Es ${merchant.name}?", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = palette.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onAccept, enabled = enabled, modifier = Modifier.size(44.dp).testTag("statement_suggestion_accept_$lineId")) {
                Icon(Icons.Filled.Check, "Sí, es ${merchant.name}", Modifier.size(18.dp), tint = tone.fg)
            }
            IconButton(onClick = onReject, enabled = enabled, modifier = Modifier.size(44.dp).testTag("statement_suggestion_reject_$lineId")) {
                Icon(Icons.Filled.Close, "No es ${merchant.name}", Modifier.size(18.dp), tint = palette.secondaryMuted)
            }
        }
    }
}

/** Merchant already set on a line: logo and name, under the description. */
@Composable
internal fun StatementLineMerchant(lineId: String, merchant: MerchantCatalogEntry) {
    Row(Modifier.testTag("statement_merchant_$lineId"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatementMerchantLogo(merchant, size = 18.dp)
        Text(merchant.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color = rememberCalmEmeraldColors().secondaryMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What Kipu could not learn after registering; the movements themselves were registered. */
internal fun statementLearningWarningsText(warnings: List<StatementLearningWarning>, lines: List<StatementImportLine>,
    merchants: List<MerchantCatalogEntry>): String? {
    if (warnings.isEmpty()) return null
    fun source(warning: StatementLearningWarning) = lines.firstOrNull { it.id in warning.lineIds }?.originalDescription ?: warning.key
    fun merchant(warning: StatementLearningWarning) = merchants.firstOrNull { it.id.value == warning.key }?.name ?: "este comercio"
    val details = warnings.distinctBy { it.kind to it.key }.map { warning ->
        when (warning.kind) {
            StatementLearningWarningKind.ALIAS_CONFLICT -> "No recordamos el comercio de '${source(warning)}': lo asignaste a comercios distintos."
            StatementLearningWarningKind.CATEGORY_CONFLICT -> "No recordamos la categoría de ${merchant(warning)}: usaste categorías distintas."
            StatementLearningWarningKind.ALIAS_SAVE_FAILED -> "No pudimos recordar el comercio de '${source(warning)}'."
            StatementLearningWarningKind.CATEGORY_SAVE_FAILED -> "No pudimos recordar la categoría de ${merchant(warning)}."
            StatementLearningWarningKind.LEARNING_FAILED -> "No pudimos guardar lo que elegiste para recordar."
        }
    }
    return (details + "Tus movimientos sí se registraron.").joinToString("\n")
}
