package com.kipu.app.feature.categories.presentation.categories

import com.kipu.app.ui.theme.KipuThemePreviews
import com.kipu.app.ui.theme.KipuTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.parseHexColor
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.theme.rememberKipuColors
import java.text.Normalizer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = rememberKipuColors()
    val semanticColors = rememberCalmEmeraldColors()
    val selectedAccent = if (state.selectedTab == CategoryTab.EXPENSE) semanticColors.expenseText else colors.primary
    val snackbarHostState = remember { SnackbarHostState() }

    // Map to track expanded state of each root category
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    var showInactive by remember { mutableStateOf(false) }

    // Interactive search & filter state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(CategoryFilterOption.ALL) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Buscar categoría...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.inkSecondary,
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surfaceVariant,
                                unfocusedContainerColor = colors.surfaceVariant,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpiar búsqueda",
                                            tint = colors.inkSecondary,
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        Text(
                            text = "Categorías",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.inkPrimary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = colors.inkPrimary,
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar categorías",
                                tint = colors.inkPrimary,
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { filterMenuExpanded = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filtrar categorías",
                                tint = if (currentFilter != CategoryFilterOption.ALL) colors.primary else colors.inkPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas las categorías") },
                                onClick = {
                                    currentFilter = CategoryFilterOption.ALL
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Solo personalizadas") },
                                onClick = {
                                    currentFilter = CategoryFilterOption.ONLY_CUSTOM
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Solo predeterminadas") },
                                onClick = {
                                    currentFilter = CategoryFilterOption.ONLY_DEFAULT
                                    filterMenuExpanded = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog(parentId = null) },
                containerColor = if (state.isFreeLimitReachedFor(state.selectedTab.categoryType)) colors.surfaceVariant
                    else if (state.selectedTab == CategoryTab.EXPENSE) semanticColors.expenseCoral else colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("add_root_category_button"),
            ) {
                Icon(
                    imageVector = if (state.isFreeLimitReachedFor(state.selectedTab.categoryType)) Icons.Default.Lock else Icons.Default.Add,
                    contentDescription = if (state.isFreeLimitReachedFor(state.selectedTab.categoryType)) "Cupo completo de categorías personalizadas" else "Agregar categoría raíz",
                    tint = if (state.isFreeLimitReachedFor(state.selectedTab.categoryType)) colors.inkSecondary else colors.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Segmented Tabs: Gastos vs Ingresos
                val expenseCount = CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot }
                val incomeCount = CategoryTab.INCOME.filterCategories(state.categories).count { it.category.isRoot }

                CategoryTypeTabs(state.selectedTab, expenseCount, incomeCount) {
                    expandedStates.clear()
                    showInactive = false
                    viewModel.onTabSelected(it)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quota Card
                if (!state.isPremiumVerified && (state.activeCustomRootsCount > 0 || state.isFreeLimitReachedFor(state.selectedTab.categoryType))) {
                    QuotaBanner(
                        expenseCount = state.activeCustomExpenseRootsCount,
                        incomeCount = state.activeCustomIncomeRootsCount,
                        selectedTab = state.selectedTab,
                        maxCount = state.maxCustomRoots,
                        isLimitReached = state.isFreeLimitReachedFor(state.selectedTab.categoryType),
                        onOpenQuotaSelection = if (state.activeCustomExpenseRootsCount > state.maxCustomRoots || state.activeCustomIncomeRootsCount > state.maxCustomRoots) {
                            viewModel::openQuotaSelection
                        } else null,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Filter categories based on tab, search query, and filter menu
                val displayCategories = remember(
                    state.categories,
                    state.selectedTab,
                    searchQuery,
                    currentFilter,
                ) {
                    val tabFiltered = state.selectedTab.filterCategories(state.categories)
                    val optionFiltered = when (currentFilter) {
                        CategoryFilterOption.ALL -> tabFiltered
                        CategoryFilterOption.ONLY_CUSTOM -> tabFiltered.filter { it.category.isCustom }
                        CategoryFilterOption.ONLY_DEFAULT -> tabFiltered.filter { !it.category.isCustom }
                    }
                    if (searchQuery.isBlank()) {
                        optionFiltered
                    } else {
                        val q = searchQuery.trim().lowercase()
                        optionFiltered.mapNotNull { rootItem ->
                            val matchesRoot = rootItem.displayName.lowercase().contains(q)
                            val matchingSubcategories = rootItem.subcategories.filter { sub ->
                                sub.displayName.lowercase().contains(q)
                            }
                            if (matchesRoot || matchingSubcategories.isNotEmpty()) {
                                rootItem.copy(subcategories = if (matchesRoot && matchingSubcategories.isEmpty()) rootItem.subcategories else matchingSubcategories)
                            } else null
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Resultados para “$searchQuery” · ${displayCategories.size}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = selectedAccent)
                        TextButton(onClick = { searchQuery = "" }) { Text("Limpiar filtro", color = colors.primaryText) }
                    }
                }
                // List of Categories
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displayCategories.filter { it.category.isActive }, key = { it.category.id.value }) { rootItem ->
                        val isExpanded = expandedStates[rootItem.category.id.value]
                            ?: false

                        CategoryRootCard(
                            item = rootItem,
                            searchQuery = searchQuery,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedStates[rootItem.category.id.value] = !isExpanded
                            },
                            onEdit = { viewModel.openEditDialog(rootItem) },
                            onDelete = { viewModel.requestDeleteCategory(rootItem) },
                            onEditSubcategory = { subItem -> viewModel.openEditDialog(subItem) },
                            onDeleteSubcategory = { subItem -> viewModel.requestDeleteCategory(subItem) },
                            onAddSubcategory = { viewModel.openCreateDialog(rootItem.category.id) },
                        )
                    }

                    val inactive = displayCategories.filterNot { it.category.isActive }
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { showInactive = !showInactive }
                                .testTag("categories_inactive_toggle").padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(18.dp))
                            Text("  Inactivas (${inactive.size})", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
                            Icon(Icons.Default.ExpandMore, contentDescription = if (showInactive) "Ocultar inactivas" else "Mostrar inactivas",
                                modifier = Modifier.graphicsLayer { rotationZ = if (showInactive) 180f else 0f }, tint = colors.inkSecondary)
                        }
                    }
                    if (showInactive) items(inactive, key = { it.category.id.value }) { rootItem ->
                        val expanded = expandedStates[rootItem.category.id.value] ?: false
                        CategoryRootCard(
                            item = rootItem, isExpanded = expanded, searchQuery = searchQuery,
                            onToggleExpand = { expandedStates[rootItem.category.id.value] = !expanded },
                            onEdit = { viewModel.openEditDialog(rootItem) },
                            onToggleActive = { viewModel.toggleCategoryActive(rootItem.category.id, false) },
                            onEditSubcategory = { viewModel.openEditDialog(it) },
                            onDeleteSubcategory = { viewModel.requestDeleteCategory(it) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
    }

    // Modern Form Dialog (Category / Subcategory creation & editing)
    if (state.isCreateDialogOpen) {
        CategoryFormDialog(
            isEditing = state.isEditing,
            categoryType = state.createCategoryType,
            name = state.createCategoryName,
            icon = state.createCategoryIcon,
            color = state.createCategoryColor,
            parentId = state.createParentId,
            availableRoots = state.categories.filter {
                it.category.isRoot &&
                    (it.category.categoryType == state.createCategoryType ||
                        (state.createCategoryType != com.kipu.app.feature.categories.domain.model.CategoryType.GENERAL &&
                            it.category.categoryType == com.kipu.app.feature.categories.domain.model.CategoryType.GENERAL))
            },
            onNameChange = viewModel::onNameChanged,
            onIconChange = viewModel::onIconChanged,
            onColorChange = viewModel::onColorChanged,
            onParentIdChange = viewModel::onParentIdChanged,
            onDismiss = viewModel::closeCreateDialog,
            onConfirm = viewModel::submitCreateCategory,
        )
    }

    // Plan Free Quota Selection Modal
    if (state.isQuotaSelectionOpen) {
        val selectableRoots = state.categories.filter {
            it.category.isRoot && it.category.isCustom && it.category.isActive
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaSelection,
            title = {
                Text(
                    text = "Elegir categorías para Plan Free",
                    fontWeight = FontWeight.Bold,
                    color = colors.inkPrimary,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Elige hasta 5 categorías de gasto y 5 de ingreso personalizadas. Las categorías generales consumen un cupo de cada tipo. Las demás conservarán su historial y quedarán bloqueadas por el plan; su estado activo no cambia.",
                        color = colors.inkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(selectableRoots, key = { it.category.id.value }) { item ->
                            val checked = item.category.id in state.quotaSelectionDraft
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.toggleQuotaSelection(item.category.id) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.toggleQuotaSelection(item.category.id) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(parseHexColor(item.color).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = resolveCategoryIcon(item.icon),
                                        contentDescription = null,
                                        tint = parseHexColor(item.color),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.displayName,
                                    color = colors.inkPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val draftQuota = CategoryRules.evaluateCustomRootQuota(
                        categories = selectableRoots.map { it.category },
                        selectedRootIds = state.quotaSelectionDraft,
                        limitPerType = state.maxCustomRoots,
                    )
                    Text(
                        text = "Gastos: ${draftQuota.selectedCounts.expenseRoots}/${state.maxCustomRoots} • Ingresos: ${draftQuota.selectedCounts.incomeRoots}/${state.maxCustomRoots} (${draftQuota.selectedRootIds.size} seleccionadas)",
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveQuotaSelection) {
                    Text("Guardar selección", fontWeight = FontWeight.Bold, color = colors.primaryText)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQuotaSelection) {
                    Text("Cancelar", color = colors.inkSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }

    // Diálogo de Límite Superado
    if (state.showQuotaExceededDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaDialog,
            title = {
                Text(
                    text = "Límite de Categorías Superado",
                    fontWeight = FontWeight.Bold,
                    color = colors.inkPrimary,
                )
            },
            text = {
                Text(
                    text = "Has alcanzado el límite Free por tipo de categoría: 5 de gasto y 5 de ingreso. Las generales consumen un cupo de cada tipo. Conserva el historial y elige cuáles quieres seguir usando o inactiva una para crear otra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissQuotaDialog) {
                    Text("Aceptar", fontWeight = FontWeight.Bold, color = colors.primaryText)
                }
            },
            containerColor = colors.surface,
        )
    }

    // Diálogo de Confirmación de Inactivación
    state.categoryToDelete?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = {
                Text(
                    text = "Inactivar Categoría",
                    fontWeight = FontWeight.Bold,
                    color = colors.inkPrimary,
                )
            },
            text = {
                Text(
                    text = "¿Deseas inactivar la categoría \"${itemToDelete.displayName}\"? Ya no estará disponible para nuevos movimientos, pero se conservará su historial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteCategory) {
                    Text("Inactivar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text("Cancelar", color = colors.inkSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }
}

private enum class CategoryFilterOption {
    ALL,
    ONLY_CUSTOM,
    ONLY_DEFAULT
}

@Composable
fun QuotaBanner(
    activeCount: Int,
    maxCount: Int,
    isLimitReached: Boolean,
    modifier: Modifier = Modifier,
    onOpenQuotaSelection: (() -> Unit)? = null,
) {
    QuotaBanner(
        expenseCount = activeCount,
        incomeCount = 0,
        maxCount = maxCount,
        isLimitReached = isLimitReached,
        modifier = modifier,
        onOpenQuotaSelection = onOpenQuotaSelection,
    )
}

@Composable
fun QuotaBanner(
    expenseCount: Int,
    incomeCount: Int,
    maxCount: Int,
    isLimitReached: Boolean,
    modifier: Modifier = Modifier,
    onOpenQuotaSelection: (() -> Unit)? = null,
    selectedTab: CategoryTab = CategoryTab.EXPENSE,
) {
    val colors = rememberKipuColors()
    val semanticColors = rememberCalmEmeraldColors()
    val count = if (selectedTab == CategoryTab.EXPENSE) expenseCount else incomeCount
    val accent = if (selectedTab == CategoryTab.EXPENSE) semanticColors.expenseCoral else colors.primary
    Card(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface), border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Categorías personalizadas", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = colors.inkPrimary)
                Surface(color = semanticColors.incomeBg, shape = RoundedCornerShape(4.dp)) {
                    Text("Free", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = colors.primaryText)
                }
                Text("$count/$maxCount usadas", style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary)
                if (onOpenQuotaSelection != null) IconButton(onClick = onOpenQuotaSelection, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Elegir categorías del plan Free", tint = colors.inkSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(maxCount) { index ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (index < count) accent else colors.border))
                }
            }
            if (isLimitReached) Text("Cupo completo", style = MaterialTheme.typography.labelSmall,
                color = if (selectedTab == CategoryTab.EXPENSE) semanticColors.expenseText else colors.primaryText)
        }
    }
}

@Composable
fun CategoryRootCard(
    item: CategoryItem,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEditSubcategory: (CategoryItem) -> Unit = {},
    onDeleteSubcategory: (CategoryItem) -> Unit = {},
    onToggleActive: (() -> Unit)? = null,
    onAddSubcategory: (() -> Unit)? = null,
    onToggleSubcategoryActive: ((CategoryId, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    val colors = rememberKipuColors()
    val isRootActive = item.category.isActive
    val reducedMotion = rememberReducedMotionEnabled()
    val rootAppearance = remember(item.category.id, item.displayName, item.icon, item.color) {
        categoryRootAppearance(item)
    }
    val rotationAngle = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis),
        label = "expand_rotation",
    )
    var showRootMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Category Squircle Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(rootAppearance.tint.copy(alpha = if (colors.isDark) 0.25f else 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = resolveCategoryIcon(rootAppearance.iconId),
                        contentDescription = null,
                        tint = rootAppearance.tint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title, Subtitle, and Badge Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                        .semantics {
                            stateDescription = when {
                                item.category.isPlanLocked -> "Bloqueada por el plan Free"
                                !isRootActive -> "Inactiva"
                                else -> "Activa"
                            }
                        },
                ) {
                    Text(
                        text = highlightedCategoryName(item.displayName, searchQuery, colors.primary),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when (item.subcategories.size) {
                            0 -> "Sin subcategorías"
                            1 -> "1 subcategoría"
                            else -> "${item.subcategories.size} subcategorías"
                        },
                        style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = colors.surfaceVariant) {
                            Text(if (item.category.isCustom) "Personalizada" else "Predeterminada",
                                Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (item.category.isCustom) colors.primaryText else colors.inkSecondary)
                        }
                        Text(if (item.category.isCustom) "Usa 1 cupo" else "No consume cupo",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.inkSecondary)
                    }
                    // Status line if inactive or locked
                    if (item.category.isPlanLocked) {
                        Text(
                            text = "Bloqueada por el plan Free",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (!isRootActive) {
                        Text(
                            text = "Inactiva",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Trailing 3-dots Options Menu
                Box {
                    IconButton(
                        onClick = { showRootMenu = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Editar ${item.displayName}",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showRootMenu,
                        onDismissRequest = { showRootMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary)
                            },
                            onClick = {
                                showRootMenu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isRootActive) "Inactivar" else "Reactivar", color = colors.primaryText) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showRootMenu = false
                                if (isRootActive) onDelete() else onToggleActive?.invoke()
                            },
                        )
                    }
                }

                // Chevron Expand/Collapse
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar ${item.displayName}" else "Expandir ${item.displayName}",
                        tint = colors.inkSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = rotationAngle.value },
                    )
                }
            }

            // Subcategories Collapsible List
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(if (reducedMotion) 0 else KipuMotionTokens.SubtreeEnterMillis)) +
                    expandVertically(tween(if (reducedMotion) 0 else KipuMotionTokens.SubtreeEnterMillis)),
                exit = fadeOut(tween(if (reducedMotion) 0 else KipuMotionTokens.QuickMillis)) +
                    shrinkVertically(tween(if (reducedMotion) 0 else KipuMotionTokens.QuickMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.subcategories.forEach { subItem ->
                        var showSubMenu by remember { mutableStateOf(false) }

                        Surface(
                            color = colors.surfaceVariant.copy(alpha = if (colors.isDark) 0.32f else 0.34f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(32.dp)
                                        .testTag("subcategory_branch_${subItem.category.id.value}")
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(colors.primary.copy(alpha = 0.55f)),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Subcategory Icon
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(parseHexColor(subItem.color).copy(alpha = if (colors.isDark) 0.30f else 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = resolveCategoryIcon(subItem.icon),
                                        contentDescription = null,
                                        tint = parseHexColor(subItem.color),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = highlightedCategoryName(subItem.displayName, searchQuery, colors.primary),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )

                                        // Subcategory 3-dots Menu aligned with label
                                        Box {
                                            IconButton(
                                                onClick = { showSubMenu = true },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Editar ${subItem.displayName}",
                                                    tint = colors.inkSecondary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showSubMenu,
                                                onDismissRequest = { showSubMenu = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Editar") },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary)
                                                    },
                                                    onClick = {
                                                        showSubMenu = false
                                                        onEditSubcategory(subItem)
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Inactivar", color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                    },
                                                    onClick = {
                                                        showSubMenu = false
                                                        onDeleteSubcategory(subItem)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    if (!isRootActive) {
                                        Text(
                                            text = "Inactiva por categoría padre",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Button: Agregar subcategoría
                    if (onAddSubcategory != null && isRootActive) {
                        TextButton(
                            onClick = onAddSubcategory,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .heightIn(min = 44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Agregar subcategoría",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.primaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CategoryRootAppearance(
    val iconId: String,
    val tint: Color,
)

private fun categoryRootAppearance(item: CategoryItem): CategoryRootAppearance {
    val storedTint = parseHexColor(item.color)
    val usesPlaceholderPresentation = item.category.isSystem &&
        item.icon.equals("category", ignoreCase = true) &&
        item.color.equals("#757575", ignoreCase = true)
    if (!usesPlaceholderPresentation) return CategoryRootAppearance(item.icon, storedTint)

    val normalizedName = Normalizer.normalize(item.displayName, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
    return when (normalizedName) {
        "alimentacion" -> CategoryRootAppearance("restaurant", Color(0xFFD97706))
        "transporte" -> CategoryRootAppearance("directions_car", Color(0xFF0284C7))
        "servicios" -> CategoryRootAppearance("receipt", Color(0xFF7C3AED))
        else -> CategoryRootAppearance(item.icon, storedTint)
    }
}


@Composable
internal fun CategoryTypeTabs(selectedTab: CategoryTab, expenseCount: Int, incomeCount: Int, onSelected: (CategoryTab) -> Unit) {
    val colors = rememberKipuColors()
    val semanticColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Gastos Tab
            val isExpenseSelected = selectedTab == CategoryTab.EXPENSE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isExpenseSelected) semanticColors.expenseCoral else Color.Transparent)
                    .clickable { onSelected(CategoryTab.EXPENSE) }
                    .semantics {
                        role = Role.Tab
                        selected = isExpenseSelected
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isExpenseSelected) colors.onPrimary else semanticColors.expenseText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gastos",
                        fontSize = 14.sp,
                        fontWeight = if (isExpenseSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isExpenseSelected) colors.onPrimary else semanticColors.expenseText,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isExpenseSelected) colors.onPrimary.copy(alpha = 0.25f) else colors.surfaceVariant,
                    ) {
                        Text(
                            text = "$expenseCount",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpenseSelected) colors.onPrimary else semanticColors.expenseText,
                        )
                    }
                }
            }

            // Ingresos Tab
            val isIncomeSelected = selectedTab == CategoryTab.INCOME
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isIncomeSelected) colors.primary else Color.Transparent)
                    .clickable { onSelected(CategoryTab.INCOME) }
                    .semantics {
                        role = Role.Tab
                        selected = isIncomeSelected
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isIncomeSelected) colors.onPrimary else colors.inkSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ingresos",
                        fontSize = 14.sp,
                        fontWeight = if (isIncomeSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isIncomeSelected) colors.onPrimary else colors.inkSecondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isIncomeSelected) colors.onPrimary.copy(alpha = 0.25f) else colors.surfaceVariant,
                    ) {
                        Text(
                            text = "$incomeCount",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncomeSelected) colors.onPrimary else colors.inkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@KipuThemePreviews
@Composable
private fun CategoryTypeTabsThemePreview() = KipuTheme {
    CategoryTypeTabs(CategoryTab.EXPENSE, 4, 2, {})
}

private fun highlightedCategoryName(name: String, query: String, accent: Color) = buildAnnotatedString {
    append(name)
    if (query.isNotBlank()) {
        var index = name.indexOf(query.trim(), ignoreCase = true)
        while (index >= 0) {
            addStyle(SpanStyle(background = accent.copy(alpha = 0.12f), fontWeight = FontWeight.Bold), index, index + query.trim().length)
            index = name.indexOf(query.trim(), index + query.trim().length, ignoreCase = true)
        }
    }
}
