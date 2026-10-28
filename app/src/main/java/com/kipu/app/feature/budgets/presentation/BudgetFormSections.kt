package com.kipu.app.feature.budgets.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.movements.presentation.resolveCatalogIcon
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.KipuOptionalDateField
import com.kipu.app.ui.theme.rememberPlanningColors

@Composable
internal fun BudgetIcon(iconId: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(iconId?.let(::resolveCatalogIcon) ?: Icons.Rounded.PieChart, null, modifier, tint = tint)
}

@Composable
internal fun BudgetIconBadge(iconId: String?, tint: Color, background: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = background, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { BudgetIcon(iconId, Modifier.size(24.dp), tint) }
    }
}

@Composable
internal fun BudgetFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
internal fun BudgetFormError(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun BudgetChoiceCard(title: String, subtitle: String?, selected: Boolean, tag: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: () -> Unit) {
    val colors = rememberPlanningColors()
    Surface(shape = RoundedCornerShape(16.dp), color = if (selected) colors.primaryDeep.copy(alpha = 0.08f) else colors.surfaceCard,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primaryDeep else colors.borderSubtle),
        modifier = modifier.heightIn(min = if (subtitle == null) 48.dp else 64.dp).testTag(tag).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitle == null) Icon(if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    null, Modifier.size(22.dp), tint = if (selected) colors.primaryDeep else colors.secondaryMuted)
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText, modifier = Modifier.weight(1f))
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetPeriodSection(form: BudgetFormState, onChange: ((BudgetFormInput) -> BudgetFormInput) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val colors = rememberPlanningColors()
    val input = form.input
    Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, colors.primaryDeep.copy(alpha = 0.25f)), color = colors.surfaceCard) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(onClick = { expanded = !expanded }, color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().testTag("budget_period_toggle").semantics { stateDescription = if (expanded) "Expandido" else "Contraído" }) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.incomeBg, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(24.dp), tint = colors.primaryDeep) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Periodo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(BudgetTexts.periodType(input.periodType), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    BudgetPeriodType.entries.filter { it != BudgetPeriodType.CUSTOM_RANGE }.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { type ->
                                BudgetChoiceCard(BudgetTexts.periodTypeShort(type), null, input.periodType == type, "budget_period_${type.name}",
                                    Modifier.weight(1f).fillMaxHeight()) { onChange { it.copy(periodType = type) } }
                            }
                        }
                    }
                    BudgetChoiceCard("Personalizado", "Elige fecha de inicio y fin", input.periodType == BudgetPeriodType.CUSTOM_RANGE,
                        "budget_period_CUSTOM_RANGE", Modifier.fillMaxWidth()) { onChange { it.copy(periodType = BudgetPeriodType.CUSTOM_RANGE) } }
                }
                if (input.periodType == BudgetPeriodType.CUSTOM_RANGE) {
                    KipuOptionalDateField(input.rangeStartText, { value -> onChange { it.copy(rangeStartText = value) } },
                        label = "Inicio del rango", calendarContentDescription = "Elegir fecha de inicio", tag = "budget_range_start",
                        isError = BudgetFormField.RANGE_START in form.fieldErrors, calendarOnly = true)
                    form.fieldErrors[BudgetFormField.RANGE_START]?.let { BudgetFormError(it) }
                    KipuOptionalDateField(input.rangeEndText, { value -> onChange { it.copy(rangeEndText = value) } },
                        label = "Fin del rango (inclusive)", calendarContentDescription = "Elegir fecha de fin", tag = "budget_range_end",
                        isError = BudgetFormField.RANGE_END in form.fieldErrors, calendarOnly = true)
                    form.fieldErrors[BudgetFormField.RANGE_END]?.let { BudgetFormError(it) }
                }
                HorizontalDivider(color = colors.borderSubtle)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (input.periodType == BudgetPeriodType.CUSTOM_RANGE) Icons.Rounded.Event else Icons.Rounded.Autorenew, null, tint = colors.primaryDeep)
                    Column(Modifier.weight(1f)) {
                        Text(if (input.periodType == BudgetPeriodType.CUSTOM_RANGE) "Un solo periodo" else "Se renueva automáticamente", fontWeight = FontWeight.SemiBold)
                        Text(if (input.periodType == BudgetPeriodType.CUSTOM_RANGE) "Termina en la fecha que elijas" else "Al terminar el periodo comienza uno nuevo",
                            style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                }
            }
            if (form.scopeOrScheduleChanged) Surface(color = colors.incomeBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("budget_next_period_notice")) {
                Text("Los cambios de alcance o calendario aplican desde el siguiente periodo; el periodo actual conserva lo que tenía.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = colors.incomeText)
            }
        }
    }
}

@Composable
internal fun BudgetThresholdEditor(raw: String, error: String?, onChange: (String) -> Unit) {
    val colors = rememberPlanningColors()
    val thresholds = (BudgetFormParser.parseThresholdsBps(raw) ?: Budget.DEFAULT_THRESHOLDS_BPS).sorted()
    val editable = thresholds.filter { it < 10000 }
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceCard, border = BorderStroke(1.dp, colors.borderSubtle), modifier = Modifier.testTag("budget_thresholds")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Avisarme al llegar a", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Por defecto 50, 80 y 100 %. Los colores te ayudan a reconocer cada nivel.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            Row(Modifier.fillMaxWidth().height(8.dp)) {
                listOf(colors.primaryDeep, colors.warningAmber, colors.nearLimitOrange, colors.expenseCoral).forEach { color ->
                    Surface(color = color, modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
            editable.forEachIndexed { index, bps ->
                val lower = if (index == 0) 1 else editable[index - 1] / 100 + 1
                val upper = if (index == editable.lastIndex) 99 else editable[index + 1] / 100 - 1
                val percent = bps / 100
                val label = if (index == 0) "Atención" else if (index == 1) "Casi al límite" else "Alerta ${index + 1}"
                val tint = if (index == 0) colors.warningText else colors.nearLimitOrange
                fun setPercent(value: Int) {
                    val updated = editable.toMutableList().also { it[index] = value * 100 }
                    onChange((updated + 10000).joinToString(", ") { (it / 100).toString() })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Circle, null, Modifier.size(8.dp), tint = tint)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        Text(if (index == 0) "Vas rápido" else "Te queda poco", style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                    }
                    IconButton(onClick = { setPercent(percent - 1) }, enabled = percent > lower,
                        modifier = Modifier.size(48.dp).testTag("budget_threshold_${index}_minus")) {
                        Icon(Icons.Rounded.Remove, "Disminuir umbral de $label")
                    }
                    Text("$percent %", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { setPercent(percent + 1) }, enabled = percent < upper,
                        modifier = Modifier.size(48.dp).testTag("budget_threshold_${index}_plus")) {
                        Icon(Icons.Rounded.Add, "Aumentar umbral de $label")
                    }
                }
                HorizontalDivider(color = colors.borderSubtle)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Circle, null, Modifier.size(8.dp), tint = colors.expenseText)
                Text("Al superar el límite", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("100 %", fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(8.dp), color = colors.pillTrack) {
                    Text("Fijo", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                }
            }
            error?.let { BudgetFormError(it) }
        }
    }
}
