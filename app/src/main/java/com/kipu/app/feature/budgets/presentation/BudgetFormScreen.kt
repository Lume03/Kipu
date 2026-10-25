package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.KipuOptionalDateField
import com.kipu.app.ui.component.symbol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormScreen(
    state: BudgetFormUiState,
    onNavigateBack: () -> Unit,
    onChange: ((BudgetFormInput) -> BudgetFormInput) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDismissQuota: () -> Unit,
    onSeePlans: () -> Unit,
) {
    val isEdit = (state as? BudgetFormUiState.Content)?.form?.isEdit == true
    Scaffold(
        modifier = Modifier.testTag("budget_form_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "Editar presupuesto" else "Nuevo presupuesto",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            (state as? BudgetFormUiState.Content)?.form?.let { form ->
                Surface(shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val scope = if (form.input.scopeKind == BudgetScopeKind.TOTAL) "Todos los gastos"
                            else form.categories.firstOrNull { it.id == form.input.categoryId }?.label ?: "Elegir categoría"
                        Text("${form.input.currency} ${form.input.limitText.ifBlank { "—" }} · ${BudgetTexts.periodTypeShort(form.input.periodType)} · $scope",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onSave, enabled = !form.isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("budget_save"), shape = RoundedCornerShape(20.dp)) {
                            Text(if (form.isSaving) "Guardando…" else if (form.isEdit) "Guardar presupuesto" else "Crear presupuesto", fontWeight = FontWeight.SemiBold)
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
            BudgetFormUiState.NotFound -> BudgetErrorPanel(
                "Este presupuesto ya no existe.", onNavigateBack, Modifier.padding(padding), "budget_form_not_found",
            )
            is BudgetFormUiState.Error -> BudgetErrorPanel(state.message, onRetry, Modifier.padding(padding), "budget_form_error")
            is BudgetFormUiState.Content -> {
                BudgetFormContent(state.form, onChange, Modifier.padding(padding))
                state.form.quotaLimitDialog?.let { limit ->
                    BudgetQuotaDialog(limit, onDismiss = onDismissQuota, onSeePlans = { onDismissQuota(); onSeePlans() })
                }
            }
        }
    }
}

@Composable
private fun BudgetFormContent(
    form: BudgetFormState,
    onChange: ((BudgetFormInput) -> BudgetFormInput) -> Unit,
    modifier: Modifier,
) {
    val input = form.input
    val errors = form.fieldErrors
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KipuCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), borderColor = null, elevation = 1.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Límite de gasto por periodo", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = input.limitText,
                    onValueChange = { v -> onChange { it.copy(limitText = v) } },
                    label = { Text("Límite de gasto (${input.currency})") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    prefix = { Text(Currency.fromCode(input.currency).symbol(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = BudgetFormField.LIMIT in errors,
                    supportingText = {
                        Text(errors[BudgetFormField.LIMIT] ?: if (form.isEdit) {
                            "El nuevo límite aplica al periodo actual y a los futuros; los periodos cerrados conservan el suyo."
                        } else "Importe máximo a gastar en cada periodo.")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("300", "500", "800", "1000").forEach { amount ->
                        KipuFilterChip(selected = input.limitText == amount,
                            onClick = { onChange { it.copy(limitText = amount) } },
                            label = { Text("${Currency.fromCode(input.currency).symbol()} $amount") }, modifier = Modifier.testTag("budget_quick_$amount"))
                    }
                }
            }
        }
        OutlinedTextField(
            value = input.name,
            onValueChange = { v -> onChange { it.copy(name = v) } },
            label = { Text("Nombre") },
            singleLine = true,
            isError = BudgetFormField.NAME in errors,
            supportingText = errors[BudgetFormField.NAME]?.let { { Text(it) } },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("budget_name"),
        )


        Section("Moneda") {
            if (form.isEdit) {
                Text("${input.currency} (no se puede cambiar después de crear el presupuesto)")
            } else {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PEN" to "Soles (PEN)", "USD" to "Dólares (USD)").forEach { (code, label) ->
                        ChoiceRow(label, input.currency == code, "budget_currency_$code", Modifier.weight(1f)) {
                            onChange { it.copy(currency = code) }
                        }
                    }
                }
            }
            Text(
                BudgetTexts.currencyNotice(input.currency) + ". Un presupuesto es planificación: no mueve dinero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("budget_currency_notice"),
            )
        }

        Section("Qué gastos cuenta") {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow("Todos los gastos", input.scopeKind == BudgetScopeKind.TOTAL, "budget_scope_total") {
                    onChange { it.copy(scopeKind = BudgetScopeKind.TOTAL, categoryId = null) }
                }
                ChoiceRow("Una categoría o subcategoría", input.scopeKind == BudgetScopeKind.CATEGORY, "budget_scope_category") {
                    onChange { it.copy(scopeKind = BudgetScopeKind.CATEGORY) }
                }
            }
            if (input.scopeKind == BudgetScopeKind.CATEGORY) {
                val selected = form.categories.firstOrNull { it.id == input.categoryId }
                OutlinedButton(
                    onClick = { showCategoryPicker = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_category_picker"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(selected?.label ?: "Elegir categoría de gasto")
                }
                errors[BudgetFormField.CATEGORY]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Una categoría padre incluye sus subcategorías sin contar dos veces el mismo gasto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("Periodo") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetPeriodType.entries.forEach { type ->
                    KipuFilterChip(selected = input.periodType == type, onClick = { onChange { it.copy(periodType = type) } },
                        label = { Text(BudgetTexts.periodType(type)) }, modifier = Modifier.testTag("budget_period_${type.name}"))
                }
            }
            if (input.periodType == BudgetPeriodType.CUSTOM_RANGE) {
                KipuOptionalDateField(
                    value = input.rangeStartText,
                    onValueChange = { v -> onChange { it.copy(rangeStartText = v) } },
                    label = "Inicio del rango",
                    calendarContentDescription = "Elegir fecha de inicio",
                    tag = "budget_range_start",
                    isError = BudgetFormField.RANGE_START in errors,
                    calendarOnly = true,
                )
                errors[BudgetFormField.RANGE_START]?.let { ErrorLine(it) }
                KipuOptionalDateField(
                    value = input.rangeEndText,
                    onValueChange = { v -> onChange { it.copy(rangeEndText = v) } },
                    label = "Fin del rango (inclusive)",
                    calendarContentDescription = "Elegir fecha de fin",
                    tag = "budget_range_end",
                    isError = BudgetFormField.RANGE_END in errors,
                    calendarOnly = true,
                )
                errors[BudgetFormField.RANGE_END]?.let { ErrorLine(it) }
            }
            if (form.scopeOrScheduleChanged) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().testTag("budget_next_period_notice"),
                ) {
                    Text(
                        "Los cambios de alcance o calendario aplican desde el siguiente periodo; " +
                            "el periodo actual conserva lo que tenía.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        OutlinedTextField(
            value = input.thresholdsText,
            onValueChange = { v -> onChange { it.copy(thresholdsText = v) } },
            label = { Text("Umbrales de alerta (%)") },
            singleLine = true,
            isError = BudgetFormField.THRESHOLDS in errors,
            supportingText = {
                Text(errors[BudgetFormField.THRESHOLDS] ?: "Porcentajes separados por coma. Por defecto: 50, 80, 100.")
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("budget_thresholds"),
        )

        form.generalError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("budget_general_error").semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }


    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Categoría de gasto") },
            text = {
                if (form.categories.isEmpty()) {
                    Text("Aún no tienes categorías de gasto disponibles.")
                } else {
                    LazyColumn(Modifier.selectableGroup().testTag("budget_category_list")) {
                        items(form.categories, key = { it.id }) { option ->
                            ChoiceRow(
                                label = if (option.isSubcategory) "   ${option.name} (en ${option.parentName})" else option.name,
                                selected = option.id == input.categoryId,
                                tag = "budget_category_${option.id}",
                            ) {
                                onChange { it.copy(scopeKind = BudgetScopeKind.CATEGORY, categoryId = option.id) }
                                showCategoryPicker = false
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun ErrorLine(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
