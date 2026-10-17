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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
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
    val selectedCategoryIsEligible = state.selectedCategoryId != null &&
        state.eligibleCategories.any { it.category.id == state.selectedCategoryId }
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    val filteredCategories = state.eligibleCategories.filter {
        it.displayName.contains(categoryQuery, ignoreCase = true)
    }
    val selectedCategoryIsVisible = state.selectedCategoryId == null ||
        filteredCategories.any { it.category.id == state.selectedCategoryId }

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
                        enabled = !state.isSaving && selectedCategoryIsEligible && selectedCategoryIsVisible && !state.needsNewChoice,
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .semantics { role = Role.Button }
                            .clickable { onSelectMerchant(merchant) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.selectedMerchant?.id == merchant.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Text(merchant.name, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
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
                    OutlinedTextField(
                        value = categoryQuery,
                        onValueChange = { categoryQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Filtrar categorías") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    if (!selectedCategoryIsVisible) {
                        Text(
                            "La categoría seleccionada no coincide con el filtro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.eligibleCategories.isEmpty()) {
                        Text(
                            "No hay categorías elegibles. Activa una categoría disponible o elige otro comercio más tarde.",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (filteredCategories.isEmpty()) {
                        Text(
                            "No encontramos categorías con ese nombre.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        filteredCategories.forEach { choice ->
                            val selected = state.selectedCategoryId == choice.category.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .semantics { role = Role.RadioButton }
                                    .clickable { onSelectCategory(choice.category.id) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = { onSelectCategory(choice.category.id) })
                                Spacer(Modifier.size(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(choice.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (choice.category.parentId == null) "Categoría principal" else "Subcategoría",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
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
}
