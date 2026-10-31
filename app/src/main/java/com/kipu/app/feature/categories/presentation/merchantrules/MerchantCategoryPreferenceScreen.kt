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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.presentation.components.MerchantAvatar
import com.kipu.app.feature.categories.presentation.components.MerchantLogoDrawables
import com.kipu.app.feature.categories.presentation.components.MerchantResultItem
import com.kipu.app.feature.categories.presentation.components.getMerchantVisualProfile
import com.kipu.app.feature.movements.presentation.PastelCategoryPickerBottomSheet
import com.kipu.app.feature.movements.presentation.PastelDesignTokens
import com.kipu.app.feature.movements.presentation.resolveCatalogIcon
import com.kipu.app.ui.component.KipuFieldRow

@Composable
fun MerchantCategoryPreferenceScreen(
    onNavigateBack: () -> Unit,
    viewModel: MerchantCategoryPreferenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    MerchantCategoryPreferenceContent(
        state = state,
        onMerchantQueryChange = viewModel::onMerchantQueryChange,
        onSelectMerchant = viewModel::onSelectMerchant,
        onSelectCategory = viewModel::onSelectCategory,
        onSave = viewModel::onSave,
        onClear = viewModel::onClear,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantCategoryPreferenceContent(
    state: MerchantCategoryPreferenceUiState,
    onMerchantQueryChange: (String) -> Unit = {},
    onSelectMerchant: (MerchantCatalogEntry) -> Unit,
    onSelectCategory: (CategoryId) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val animationDuration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) 0 else 160
    val selectedCategory = state.eligibleCategories.firstOrNull { it.category.id == state.selectedCategoryId }?.option
    val selectedCategoryIsEligible = selectedCategory != null
    var showCategoryPicker by rememberSaveable(state.selectedMerchant?.id?.value) { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Categoría por comercio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Elige la primera sugerencia para operaciones futuras", style = MaterialTheme.typography.bodySmall)
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
        bottomBar = {
            if (state.selectedMerchant != null) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Button(
                        onClick = onSave,
                        enabled = !state.isSaving && selectedCategoryIsEligible && !state.needsNewChoice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .navigationBarsPadding()
                            .imePadding()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (state.isSaving) "Guardando…" else "Guardar preferencia")
                    }
                }
            }
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Disponible también en Free.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Solo afecta operaciones futuras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("1. Elige un comercio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.selectedMerchant == null) {
                    OutlinedTextField(
                        value = state.merchantQuery,
                        onValueChange = onMerchantQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar en el catálogo") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                if (state.selectedMerchant == null) state.merchants.take(5).forEach { merchant ->
                    MerchantResultItem(
                        entry = merchant,
                        onClick = { onSelectMerchant(merchant) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.Button },
                    )
                }
                if (state.selectedMerchant == null && state.merchantQuery.isNotBlank() && state.merchants.isEmpty()) {
                    Text(
                        "No encontramos comercios activos. Revisa el nombre e inténtalo otra vez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(
                    visible = state.selectedMerchant != null,
                    enter = fadeIn(tween(animationDuration)) + expandVertically(tween(animationDuration)),
                    exit = fadeOut(tween(animationDuration)) + shrinkVertically(tween(animationDuration)),
                ) {
                    state.selectedMerchant?.let { merchant ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                Modifier.padding(14.dp),
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
                                Text("${merchant.name} ✓", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                OutlinedButton(onClick = { onMerchantQueryChange("") }, modifier = Modifier.heightIn(min = 44.dp)) {
                                    Text("Cambiar")
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.selectedMerchant != null,
            enter = fadeIn(tween(animationDuration)) + expandVertically(tween(animationDuration)),
            exit = fadeOut(tween(animationDuration)) + shrinkVertically(tween(animationDuration)),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("2. Elige una categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Solo aparecen categorías activas, compatibles con el tipo de operación y habilitadas en tu plan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val categoryLabel = selectedCategory?.displayName?.replace(" > ", " › ")
                    val categoryColors = PastelDesignTokens.resolvePastelToken(
                        selectedCategory?.name.orEmpty(),
                        selectedCategory?.icon.orEmpty(),
                    )
                    KipuFieldRow(
                        icon = resolveCatalogIcon(selectedCategory?.icon ?: "category"),
                        iconBg = categoryColors.containerColor,
                        iconTint = categoryColors.contentColor,
                        label = "Categoría",
                        value = categoryLabel ?: "Elige una categoría",
                        valueMuted = selectedCategory == null,
                        onClick = { showCategoryPicker = true },
                        tag = "merchant_category_picker_open",
                        description = "Categoría: ${categoryLabel ?: "sin elegir"}. Cambiar categoría",
                    )
                    if (state.eligibleCategories.isEmpty()) {
                        Text(
                            "No hay categorías elegibles. Activa una categoría disponible o elige otro comercio más tarde.",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.existingPreference != null) {
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Eliminar preferencia")
                        }
                    }
                    state.message?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.contains("no se pudo", ignoreCase = true)) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            "La preferencia se ofrece como primera sugerencia. Si una categoría deja de ser válida, tendrás que elegir otra antes de continuar.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
    if (showCategoryPicker && state.selectedMerchant != null) {
        PastelCategoryPickerBottomSheet(
            categories = state.eligibleCategories.map { it.option },
            selectedCategoryId = state.selectedCategoryId?.value,
            onSelect = { option ->
                onSelectCategory(CategoryId(option.id))
                showCategoryPicker = false
            },
            onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
            onDismiss = { showCategoryPicker = false },
            directSelectionMode = true,
            allowCreate = false,
            title = "Elige una categoría",
            subtitle = "Kipu la usará para este comercio en tus próximos movimientos",
        )
    }
}
