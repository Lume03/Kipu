package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.ui.component.KipuOptionalDateField

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
                BudgetFormContent(state.form, onChange, onSave, Modifier.padding(padding))
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
    onSave: () -> Unit,
    modifier: Modifier,
) {
    val input = form.input
    val errors = form.fieldErrors
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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

        OutlinedTextField(
            value = input.limitText,
            onValueChange = { v -> onChange { it.copy(limitText = v) } },
            label = { Text("Límite de gasto (${input.currency})") },
            singleLine = true,
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
            Column(Modifier.selectableGroup()) {
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
            Column(Modifier.selectableGroup()) {
                BudgetPeriodType.entries.forEach { type ->
                    ChoiceRow(BudgetTexts.periodType(type), input.periodType == type, "budget_period_${type.name}") {
                        onChange { it.copy(periodType = type) }
                    }
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

        Button(
            onClick = onSave,
            enabled = !form.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_save"),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (form.isSaving) "Guardando…" else "Guardar presupuesto", fontWeight = FontWeight.SemiBold)
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
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
