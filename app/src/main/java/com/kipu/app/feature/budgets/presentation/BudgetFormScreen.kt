package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.IconCatalogDialog
import com.kipu.app.feature.movements.presentation.PastelCategoryPickerBottomSheet
import com.kipu.app.feature.movements.presentation.privateAmount
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormScreen(
    state: BudgetFormUiState, onNavigateBack: () -> Unit,
    onChange: ((BudgetFormInput) -> BudgetFormInput) -> Unit, onSave: () -> Unit,
    onRetry: () -> Unit, onDismissQuota: () -> Unit, onSeePlans: () -> Unit,
) {
    val isEdit = (state as? BudgetFormUiState.Content)?.form?.isEdit == true
    val colors = rememberCalmEmeraldColors()
    Scaffold(
        modifier = Modifier.testTag("budget_form_screen"), containerColor = colors.background,
        topBar = { TopAppBar(
            title = { Text(if (isEdit) "Editar presupuesto" else "Nuevo presupuesto", fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
        ) },
        bottomBar = {
            (state as? BudgetFormUiState.Content)?.form?.let { form ->
                Surface(color = colors.surfaceCard, shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val scope = if (form.input.scopeKind == BudgetScopeKind.TOTAL) "Todos los gastos"
                            else form.categories.firstOrNull { it.id == form.input.categoryId }?.label ?: "Elegir categoría"
                        Text("${form.input.currency} · ${BudgetTexts.periodTypeShort(form.input.periodType)} · $scope",
                            style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                        Button(onClick = onSave, enabled = !form.isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("budget_save"), shape = RoundedCornerShape(16.dp)) {
                            Text(if (form.isSaving) "Guardando…" else if (form.isEdit) "Guardar presupuesto" else "Crear presupuesto", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (state) {
            BudgetFormUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Cargando formulario" })
            }
            BudgetFormUiState.NotFound -> BudgetErrorPanel("Este presupuesto ya no existe.", onNavigateBack, Modifier.padding(padding), "budget_form_not_found")
            is BudgetFormUiState.Error -> BudgetErrorPanel(state.message, onRetry, Modifier.padding(padding), "budget_form_error")
            is BudgetFormUiState.Content -> {
                BudgetFormContent(state.form, onChange, Modifier.padding(padding))
                state.form.quotaLimitDialog?.let { limit -> BudgetQuotaDialog(limit, onDismissQuota) { onDismissQuota(); onSeePlans() } }
            }
        }
    }
}

@Composable
private fun BudgetFormContent(form: BudgetFormState, onChange: ((BudgetFormInput) -> BudgetFormInput) -> Unit, modifier: Modifier) {
    val input = form.input
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    val selectedCategory = form.categories.firstOrNull { it.id == input.categoryId }
    val categoryScope = input.scopeKind == BudgetScopeKind.CATEGORY
    val iconId = budgetIconId(if (categoryScope) input.categoryId else null, if (categoryScope) null else input.iconId, form.categories)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BudgetFormSection("Límite de gasto por periodo") {
            val changeAmount: (String) -> Unit = { value -> onChange { it.copy(limitText = value) } }
            OutlinedTextField(value = input.limitText, onValueChange = changeAmount,
                label = { Text("Límite de gasto (${input.currency})") },
                prefix = { Text(Currency.fromCode(input.currency).symbol() + " ", color = colors.primaryDeep, style = MaterialTheme.typography.headlineSmall) },
                placeholder = { Text("0.00") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = BudgetFormField.LIMIT in form.fieldErrors,
                supportingText = { Text(form.fieldErrors[BudgetFormField.LIMIT] ?: if (form.isEdit)
                    "El nuevo límite aplica al periodo actual y a los futuros; los periodos cerrados conservan el suyo."
                    else "El máximo que quieres gastar durante el periodo.") },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("budget_limit_input")
                    .privateAmount(masked, true, "Límite del presupuesto oculto", changeAmount))
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showIconPicker = true }, enabled = !categoryScope, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.borderSubtle), contentPadding = PaddingValues(12.dp),
                modifier = Modifier.size(56.dp).testTag("budget_icon_picker").semantics {
                    contentDescription = if (categoryScope) "Icono de la categoría elegida" else "Elegir icono del presupuesto"
                }) {
                BudgetIcon(iconId, Modifier.size(28.dp), colors.primaryDeep)
            }
            OutlinedTextField(value = input.name, onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text("Nombre del presupuesto") }, singleLine = true, isError = BudgetFormField.NAME in form.fieldErrors,
                supportingText = form.fieldErrors[BudgetFormField.NAME]?.let { { Text(it) } },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).testTag("budget_name"))
        }
        BudgetFormSection("Moneda") {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("PEN" to "Soles (PEN)", "USD" to "Dólares (USD)").forEach { (code, label) ->
                    BudgetChoiceCard(label, null, input.currency == code, "budget_currency_$code", Modifier.weight(1f).fillMaxHeight(), !form.isEdit) {
                        onChange { it.copy(currency = code) }
                    }
                }
            }
            if (form.isEdit) Text("La moneda se mantiene después de crear el presupuesto.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        }
        BudgetFormSection("Qué gastos cuentan") {
            Text(BudgetTexts.currencyNotice(input.currency) + ", no ingresos. Un presupuesto es planificación: no mueve dinero.",
                style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, modifier = Modifier.testTag("budget_currency_notice"))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BudgetChoiceCard("Todos los gastos", "Tope general", input.scopeKind == BudgetScopeKind.TOTAL, "budget_scope_total", Modifier.weight(1f)) {
                    onChange { it.copy(scopeKind = BudgetScopeKind.TOTAL, categoryId = null) }
                }
                BudgetChoiceCard("Una categoría", "o subcategoría", input.scopeKind == BudgetScopeKind.CATEGORY, "budget_scope_category", Modifier.weight(1f)) {
                    onChange { it.copy(scopeKind = BudgetScopeKind.CATEGORY) }
                }
            }
            if (input.scopeKind == BudgetScopeKind.CATEGORY) {
                Text("Se usará el icono de la categoría o subcategoría que elijas.",
                    style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                Surface(onClick = { showCategoryPicker = true }, shape = RoundedCornerShape(16.dp), color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.primaryDeep), modifier = Modifier.fillMaxWidth().testTag("budget_category_picker")) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BudgetIconBadge(selectedCategory?.iconId, colors.primaryDeep, colors.incomeBg)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (selectedCategory == null) "Categoría de gasto" else "Categoría elegida", style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                            Text(selectedCategory?.label?.replace(" > ", " › ") ?: "Elegir categoría", fontWeight = FontWeight.Bold)
                        }
                        Text(if (selectedCategory == null) "Elegir" else "Cambiar", style = MaterialTheme.typography.labelMedium, color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
                    }
                }
                form.fieldErrors[BudgetFormField.CATEGORY]?.let { BudgetFormError(it) }
            }
        }
        BudgetPeriodSection(form, onChange)
        BudgetThresholdEditor(input.thresholdsText, form.fieldErrors[BudgetFormField.THRESHOLDS]) { value -> onChange { it.copy(thresholdsText = value) } }
        form.generalError?.let { BudgetFormError(it, Modifier.testTag("budget_general_error")) }
        Spacer(Modifier.height(8.dp))
    }
    if (showCategoryPicker) PastelCategoryPickerBottomSheet(
        categories = form.categories.map { CategoryOption(it.id, it.name, it.iconId, parentCategoryId = it.parentId, parentName = it.parentName, color = it.color) },
        selectedCategoryId = input.categoryId,
        onSelect = { category -> onChange { it.copy(scopeKind = BudgetScopeKind.CATEGORY, categoryId = category.id) }; showCategoryPicker = false },
        onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) }, onDismiss = { showCategoryPicker = false },
        directSelectionMode = true, allowCreate = false, title = "Elegir categoría de gasto",
        subtitle = "Una categoría incluye sus subcategorías, sin contar dos veces el mismo gasto")
    if (showIconPicker) IconCatalogDialog(initialIconId = iconId ?: "pie_chart",
        subtitle = "Elige el icono para tu presupuesto",
        onConfirmIcon = { icon -> onChange { it.copy(iconId = icon) }; showIconPicker = false }, onDismiss = { showIconPicker = false })
}
