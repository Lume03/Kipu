package com.kipu.app.feature.categories.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.categories.domain.MerchantSearchCandidate
import com.kipu.app.ui.theme.rememberKipuColors

/** Internet search and private creation offered by [MerchantPicker]; `null` keeps the catalog-only picker. */
data class MerchantDiscoveryUi(
    val state: MerchantDiscoveryState,
    val onQueryChanged: (String) -> Unit,
    val onSearchInternet: () -> Unit,
    val onAddCandidate: (MerchantSearchCandidate) -> Unit,
    val onCreatePrivate: () -> Unit,
    val onClearError: () -> Unit,
    val onAddedMerchantConsumed: () -> Unit,
)

/** Binds [MerchantDiscoveryViewModel] for the screen that hosts the merchant picker. */
@Composable
fun rememberMerchantDiscovery(viewModel: MerchantDiscoveryViewModel = hiltViewModel()): MerchantDiscoveryUi {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return MerchantDiscoveryUi(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onSearchInternet = viewModel::searchInternet,
        onAddCandidate = viewModel::addCandidate,
        onCreatePrivate = viewModel::createPrivate,
        onClearError = viewModel::clearError,
        onAddedMerchantConsumed = viewModel::consumeAddedMerchant,
    )
}

/** Below this many local matches the picker offers to look the merchant up on the internet. */
internal const val FEW_LOCAL_RESULTS = 3

/**
 * Search on Logo.dev (only on the explicit tap), its candidates and the private merchant option.
 * Picking a candidate already in the catalog selects it; a new one is added and selected when ready.
 */
@Composable
internal fun MerchantDiscoverySection(
    discovery: MerchantDiscoveryUi,
    query: String,
    localResultCount: Int,
    showCreatePrivate: Boolean,
    onPickExisting: (MerchantSearchCandidate) -> Boolean,
) {
    val state = discovery.state
    val typed = query.trim()
    val busy = state.isSearching || state.isSaving
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.error?.let { MerchantDiscoveryError(it, onDismiss = discovery.onClearError) }
        if (typed.length in 2..60 && state.candidates.isEmpty() && localResultCount < FEW_LOCAL_RESULTS) {
            DiscoveryActionRow(
                icon = Icons.Default.Language,
                text = "Buscar “$typed” en internet",
                loading = state.isSearching,
                enabled = !busy,
                onClick = discovery.onSearchInternet,
                tag = "merchant_search_internet",
            )
        }
        if (state.candidates.isNotEmpty()) {
            Text(
                text = "Resultados de internet:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.testTag("merchant_internet_results"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.candidates.forEach { candidate ->
                    MerchantCandidateItem(
                        candidate = candidate,
                        enabled = !busy,
                        onClick = { if (!onPickExisting(candidate)) discovery.onAddCandidate(candidate) },
                    )
                }
            }
            Text(
                text = "Logos por Logo.dev",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp).testTag("merchant_logo_attribution"),
            )
        }
        if (showCreatePrivate && typed.length in 1..60) {
            DiscoveryActionRow(
                icon = Icons.Default.PersonAdd,
                text = "Crear “$typed” solo para mí",
                supporting = "Necesita conexión. Solo tú lo verás.",
                loading = state.isSaving,
                enabled = !busy,
                onClick = discovery.onCreatePrivate,
                tag = "merchant_create_private",
            )
        }
    }
}

@Composable
private fun DiscoveryActionRow(
    icon: ImageVector,
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    supporting: String? = null,
) {
    val kipuColors = rememberKipuColors()
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = kipuColors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(BorderStroke(1.dp, kipuColors.border), shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(kipuColors.selectedSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = kipuColors.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = kipuColors.primaryText,
                )
                supporting?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = kipuColors.inkSecondary)
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag("${tag}_loading"),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun MerchantCandidateItem(
    candidate: MerchantSearchCandidate,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val kipuColors = rememberKipuColors()
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = kipuColors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(BorderStroke(1.dp, kipuColors.border), shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("merchant_candidate_${candidate.domain}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MerchantAvatar(
                drawableId = 0,
                remoteLogoUrl = candidate.logoUrl,
                profile = getMerchantVisualProfile(candidate.name),
                size = 44.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = kipuColors.inkPrimary,
                )
                Text(
                    text = if (candidate.alreadyInCatalog) "${candidate.domain} · Ya está en tu catálogo" else candidate.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = kipuColors.inkSecondary,
                )
            }
            Icon(
                imageVector = if (candidate.alreadyInCatalog) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (candidate.alreadyInCatalog) kipuColors.primary else kipuColors.inkSecondary,
                modifier = Modifier.size(if (candidate.alreadyInCatalog) 22.dp else 16.dp),
            )
        }
    }
}

/** Same look as [CatalogStatusBanner], in the error palette. */
@Composable
private fun MerchantDiscoveryError(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().testTag("merchant_discovery_error"),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
