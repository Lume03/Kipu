package com.kipu.app.feature.categories.presentation.merchantrules

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.presentation.components.MerchantAvatar
import com.kipu.app.feature.categories.presentation.components.MerchantLogoDrawables
import com.kipu.app.feature.categories.presentation.components.MerchantResultItem
import com.kipu.app.feature.categories.presentation.components.getMerchantVisualProfile
import com.kipu.app.ui.component.KipuIconTile
import com.kipu.app.ui.component.PlanningNotice

@Composable
fun MerchantAliasRulesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    viewModel: MerchantAliasRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    MerchantAliasRulesContent(
        state = state,
        onSourceTextChange = viewModel::onSourceTextChange,
        onMerchantQueryChange = viewModel::onMerchantQueryChange,
        onSelectMerchant = viewModel::onSelectMerchant,
        onConfirmMerchant = viewModel::onConfirmMerchant,
        onSave = viewModel::onSave,
        onEdit = viewModel::onEdit,
        onDelete = viewModel::onDelete,
        onCancelEdit = viewModel::onCancelEdit,
        onNavigateBack = onNavigateBack,
        onNavigateToPremium = onNavigateToPremium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantAliasRulesContent(
    state: MerchantAliasRulesUiState,
    onSourceTextChange: (String) -> Unit,
    onMerchantQueryChange: (String) -> Unit,
    onSelectMerchant: (com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry) -> Unit,
    onConfirmMerchant: () -> Unit = {},
    onSave: () -> Unit,
    onEdit: (MerchantAliasRuleId) -> Unit = {},
    onDelete: (MerchantAliasRuleId) -> Unit,
    onCancelEdit: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
) {
    val animationDuration = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) 0 else 160
    }
    var ruleDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reglas de alias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Reconoce comercios con texto exacto", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

        if (!state.isPremiumVerified && state.editingRule == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("PREMIUM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Automatiza tus comercios frecuentes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(
                        "Crea reglas de alias con Premium. Tus movimientos confirmados nunca cambian.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(onClick = onNavigateToPremium, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Ver Premium")
                    }
                }
            }
        } else Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Nueva regla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.sourceText,
                    onValueChange = onSourceTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Texto fuente exacto") },
                    supportingText = { Text("Se normalizarán mayúsculas, tildes y espacios. El texto original permanece separado.") },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = state.merchantQuery,
                    onValueChange = onMerchantQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar comercio canónico") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                state.merchants.take(5).forEach { merchant ->
                    MerchantResultItem(
                        entry = merchant,
                        onClick = { onSelectMerchant(merchant) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.Button },
                        isSelected = state.candidateMerchant?.id == merchant.id,
                    )
                }
                state.candidateMerchant?.let { candidate ->
                    OutlinedButton(onClick = onConfirmMerchant, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Seleccionar ${candidate.name}")
                    }
                }
                AnimatedVisibility(
                    visible = state.selectedMerchant != null,
                    enter = fadeIn(tween(animationDuration)) + expandVertically(tween(animationDuration)),
                    exit = fadeOut(tween(animationDuration)) + shrinkVertically(tween(animationDuration)),
                ) {
                    state.selectedMerchant?.let { merchant ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MerchantAvatar(
                                    drawableId = MerchantLogoDrawables.resolve(merchant.logoKey),
                                    remoteLogoUrl = merchant.logoUrl,
                                    remoteDarkLogoUrl = merchant.logoDarkUrl,
                                    profile = getMerchantVisualProfile(merchant.name, merchant.brandColor),
                                    size = 40.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Comercio confirmado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("Comercio canónico: ${merchant.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
                Text(
                    "Usa el texto que aparece en la operación y elige su comercio canónico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    onClick = { ruleDetailsExpanded = !ruleDetailsExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("alias_rule_details_toggle")
                        .semantics {
                            role = Role.Button
                            stateDescription = if (ruleDetailsExpanded) "Expandido" else "Contraído"
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KipuIconTile(
                            icon = Icons.Outlined.Info,
                            background = MaterialTheme.colorScheme.surface,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Cómo funcionan las reglas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Ejemplos y coincidencias exactas", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(
                            if (ruleDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = ruleDetailsExpanded,
                    enter = fadeIn(tween(animationDuration)) + expandVertically(tween(animationDuration)),
                    exit = fadeOut(tween(animationDuration)) + shrinkVertically(tween(animationDuration)),
                ) {
                    Column(
                        modifier = Modifier.testTag("alias_rule_details"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Ejemplos de reglas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Si creas estas reglas, cada texto normalizado reconocerá el comercio que elegiste.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AliasNormalizationExample(" Izipay* TÁMBO ", "Tambo", "ic_merchant_tambo")
                        AliasNormalizationExample("  BEMBOS  ", "Bembos", "ic_merchant_bembos")
                        PlanningNotice(
                            title = "1. Se normaliza el texto",
                            message = "Se unifican mayúsculas, tildes y espacios. La puntuación se conserva y el texto original permanece separado.",
                        )
                        PlanningNotice(
                            title = "2. Coincidencia exacta",
                            message = "Si el texto normalizado coincide exactamente con tu regla y el comercio está activo, se reconoce ese comercio.",
                        )
                        PlanningNotice(
                            title = "Sin coincidencia",
                            message = "El alias no asigna un comercio. El texto fuente se conserva para revisión.",
                        )
                        PlanningNotice(
                            title = "Conflicto entre reglas",
                            message = "Si el mismo texto normalizado apunta a comercios distintos, se requiere revisión manual.",
                            isError = true,
                        )
                        Text(
                            "Si Premium o el consentimiento no están vigentes, puedes registrar el movimiento manualmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving && state.sourceText.isNotBlank() && state.selectedMerchant != null &&
                        (state.isPremiumVerified || state.editingRule != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (state.editingRule == null) "Guardar alias" else "Guardar cambios")
                }
                if (state.editingRule != null) {
                    OutlinedButton(onClick = onCancelEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Cancelar edición")
                    }
                }
                state.message?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (message.contains("no pudo", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Text("Tus reglas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val activeRules = state.rules.filter { it.deletedAt == null }
        if (activeRules.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sin reglas de alias aún", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Crea una regla para reconocer textos como Starbucks, Bembos, Tambo o Rappi y elegir su comercio canónico.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Solo se aplicará a operaciones futuras.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            activeRules.forEach { rule ->
                AliasRuleCard(
                    rule = rule,
                    merchantName = state.catalog.firstOrNull { it.id == rule.merchantId }?.name ?: "Comercio del catálogo",
                    onEdit = { onEdit(rule.id) },
                    onDelete = { onDelete(rule.id) },
                )
            }
        }
        }
    }
}

@Composable
private fun AliasNormalizationExample(original: String, merchantName: String, logoKey: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nombre original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("«$original»", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Se normaliza como",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Nombre normalizado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MerchantAliasRules.normalize(original), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MerchantAvatar(
                    drawableId = MerchantLogoDrawables.resolve(logoKey),
                    remoteLogoUrl = null,
                    profile = getMerchantVisualProfile(merchantName),
                    size = 40.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Comercio canónico de ejemplo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(merchantName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AliasRuleCard(
    rule: MerchantAliasRule,
    merchantName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Texto fuente normalizado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rule.normalizedPattern, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Comercio canónico: $merchantName", style = MaterialTheme.typography.bodyMedium)
            Text(syncStateLabel(rule.syncState.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

private fun syncStateLabel(value: String): String = when (value) {
    "SYNCED" -> "Sincronizado"
    "CONFLICT" -> "Requiere revisión"
    "FAILED" -> "Sin sincronizar; intenta de nuevo"
    else -> "Pendiente de sincronización"
}
