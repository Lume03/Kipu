package com.kipu.app.feature.capture.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuChipTone
import com.kipu.app.ui.component.KipuStatusChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Apps whose notifications the catalog reads (see CaptureSourceCatalog); Plin arrives through them. */
private val CONSENT_PROVIDERS = listOf(
    CaptureProvider.YAPE,
    CaptureProvider.BCP,
    CaptureProvider.INTERBANK,
    CaptureProvider.BBVA,
    CaptureProvider.SCOTIABANK,
)

/**
 * Explicit consent for reading bank notifications (HU-43). The button stays off until the box is
 * checked, so consent is always a deliberate act.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureConsentSheet(
    requiresPremium: Boolean,
    onConsent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    var accepted by rememberSaveable { mutableStateOf(false) }
    KipuBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background, modifier = Modifier.testTag("capture_consent_sheet")) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(72.dp).background(palette.incomeBg, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(34.dp))
            }
            Text(
                "Kipu propone tus movimientos desde los avisos de tu banco",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.primaryText,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requiresPremium) {
                    KipuStatusChip("Premium", KipuChipTone.POSITIVE, leading = { SmallIcon(Icons.Outlined.AutoAwesome, palette.primaryDeep) })
                }
                KipuStatusChip("Se lee en tu teléfono", KipuChipTone.NEUTRAL, leading = { SmallIcon(Icons.Outlined.PhoneAndroid, palette.secondaryMuted) })
            }

            ConsentCard("QUÉ LEE KIPU") {
                Text("Solo los avisos de estas apps. No lee otras apps ni SMS.", style = MaterialTheme.typography.bodyLarge, color = palette.primaryText)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CONSENT_PROVIDERS.forEach { provider ->
                        Surface(shape = RoundedCornerShape(14.dp), color = palette.pillTrack) {
                            Row(
                                Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CaptureProviderLogo(provider, 32.dp)
                                Text(provider.displayName(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette.primaryText)
                            }
                        }
                    }
                }
            }

            ConsentCard("TÚ DECIDES") {
                ConsentFact(Icons.Outlined.CheckCircle, "Cada captura espera tu revisión. Solo los gastos de alta confianza con cuenta o billetera se registran solos.")
                ConsentFact(Icons.Outlined.CreditCard, "Las compras con tarjeta de crédito siempre se confirman.")
                ConsentFact(Icons.Outlined.Lock, "Guardamos un fragmento enmascarado; se borra al resolver la captura o a los 7 días.")
                ConsentFact(Icons.Outlined.Shield, "No se leen avisos anteriores.")
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { accepted = !accepted }
                    .padding(vertical = 4.dp)
                    .testTag("capture_consent_check"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = palette.primaryDeep, checkmarkColor = palette.onPrimaryDeep),
                )
                Spacer(Modifier.width(10.dp))
                Text("Acepto que Kipu lea los avisos de estas apps para proponerme movimientos.", style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
            }
            Button(
                onClick = onConsent,
                enabled = accepted,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("capture_consent_confirm"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryDeep,
                    contentColor = palette.onPrimaryDeep,
                    disabledContainerColor = palette.pillTrack,
                    disabledContentColor = palette.secondaryMuted,
                ),
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dar mi consentimiento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Ahora no", fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
}

@Composable
private fun ConsentCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceCard,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryDeep)
            content()
        }
    }
}

@Composable
private fun ConsentFact(icon: ImageVector, text: String) {
    val palette = rememberCalmEmeraldColors()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(22.dp).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
    }
    Spacer(Modifier.height(0.dp))
}
