package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.ui.component.KipuOptionalDateField
import com.kipu.app.ui.theme.rememberKipuColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalFormScreen(
    existingGoal: Goal?,
    accounts: List<GoalBackingAccount>,
    isSaving: Boolean,
    errorMessage: String?,
    onNavigateBack: () -> Unit,
    replacementCandidates: List<Goal> = emptyList(),
    currentReserved: Money? = null,
    hasPremiumAccess: Boolean = false,
    onSave: (goalId: String?, name: String, target: Money, targetDate: LocalDate?, linkedAccountId: String?, replacementGoalId: String?, iconKey: String) -> Unit,
) {
    var iconKey by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.iconKey ?: "savings") }
    var iconSheetVisible by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.name.orEmpty()) }
    var amount by rememberSaveable(existingGoal?.id) {
        mutableStateOf(existingGoal?.target?.minorUnits?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty())
    }
    var currency by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.currency?.name ?: Currency.PEN.name) }
    var dateText by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.targetDate?.toString().orEmpty()) }
    var linkedAccount by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.linkedAccountId) }
    var accountSheetVisible by rememberSaveable { mutableStateOf(false) }
    var pendingAccountId by remember { mutableStateOf<String?>(null) }
    var replacementDialogVisible by rememberSaveable(existingGoal?.id) { mutableStateOf(false) }
    var selectedReplacementId by rememberSaveable(existingGoal?.id) { mutableStateOf<String?>(null) }

    val selectedCurrency = runCatching { Currency.valueOf(currency) }.getOrDefault(Currency.PEN)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val compatibleAccounts = accounts.filter { it.currency == selectedCurrency }
    val parsedTarget = parseGoalTarget(amount, selectedCurrency)
    val parsedDate = dateText.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var nameEdited by rememberSaveable(existingGoal?.id) { mutableStateOf(false) }
    var amountEdited by rememberSaveable(existingGoal?.id) { mutableStateOf(false) }
    val nameError = nameEdited && name.isBlank()
    val amountError = amountEdited && parsedTarget == null
    val dateError = dateText.isNotBlank() && parsedDate == null
    val targetExceedsReserved = currentReserved?.let { reserved ->
        reserved.currency == selectedCurrency && parsedTarget?.minorUnits?.let { it > reserved.minorUnits } == true
    } == true
    val requiresReplacement = existingGoal?.status == GoalStatus.COMPLETED && targetExceedsReserved &&
        !hasPremiumAccess && replacementCandidates.count { it.status == GoalStatus.ACTIVE } >= 2
    val canSave = !isSaving && name.isNotBlank() && parsedTarget != null && !dateError
    val kipuColors = rememberKipuColors()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingGoal == null) "Nueva meta" else "Editar meta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (canSave) {
                            if (requiresReplacement) replacementDialogVisible = true
                            else parsedTarget?.let {
                                onSave(existingGoal?.id, name.trim(), it, parsedDate, linkedAccount, null, iconKey)
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("goal_save_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSave) kipuColors.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canSave) kipuColors.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(if (isSaving) "Guardando…" else "Guardar meta", fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, kipuColors.border),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = kipuColors.primary)
                    Text(
                        "Define tu objetivo. Crear o editar una meta organiza tu ahorro y no debita dinero de tus cuentas asociadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (existingGoal == null) {
                Text("Parte de una idea", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Fondo de emergencia" to "shield", "Viaje" to "flight", "Laptop" to "laptop", "Hogar" to "home").forEach { (idea, icon) ->
                        com.kipu.app.ui.component.KipuFilterChip(selected = name == idea, onClick = { name = idea; iconKey = icon }, label = { Text(idea) })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("NOMBRE DE LA META", required = true)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameEdited = true },
                    placeholder = { Text("Ej. Comprar laptop, Fondo de viaje") },
                    singleLine = true,
                    isError = nameError,
                    modifier = Modifier.fillMaxWidth().testTag("goal_form_name"),
                    shape = RoundedCornerShape(16.dp),
                    supportingText = if (nameError) ({ Text("Ingresa un nombre para tu meta de ahorro") }) else null,
                )
            }

            OutlinedButton(
                onClick = { iconSheetVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("goal_icon_picker"),
                shape = RoundedCornerShape(16.dp),
            ) {
                GoalIconBadge(iconKey)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Icono de la meta", style = MaterialTheme.typography.labelLarge)
                    Text(goalIcon(iconKey).label, style = MaterialTheme.typography.bodySmall)
                }
                Text("Cambiar")
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (amountError) MaterialTheme.colorScheme.error else kipuColors.border, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MONTO\nOBJETIVO", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    CurrencySegment(
                        currency = selectedCurrency,
                        modifier = Modifier.width(168.dp),
                        onSelect = { selected ->
                            currency = selected.name
                            if (accounts.none { it.id == linkedAccount && it.currency == selected }) linkedAccount = null
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (selectedCurrency == Currency.PEN) "S/" else "$",
                        color = kipuColors.primary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    BasicTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }; amountEdited = true },
                        modifier = Modifier.weight(1f).testTag("goal_form_target"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(kipuColors.primary),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (amount.isEmpty()) {
                                    Text(
                                        "0.00",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(if (amountError) MaterialTheme.colorScheme.error else kipuColors.border),
                )
                Text(
                    if (amountError) "El monto objetivo debe ser mayor a 0.00" else "Puedes ingresar la cantidad exacta que necesitas acumular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("FECHA OBJETIVO", suffix = "(opcional)")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("3 meses" to 3L, "6 meses" to 6L, "1 año" to 12L, "2 años" to 24L, "Sin fecha" to 0L).forEach { (label, months) ->
                        val date = if (months == 0L) "" else LocalDate.now().plusMonths(months).toString()
                        com.kipu.app.ui.component.KipuFilterChip(selected = dateText == date, onClick = { dateText = date }, label = { Text(label) })
                    }
                }
                KipuOptionalDateField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    label = stringResource(R.string.optional_date_goal_label),
                    calendarContentDescription = stringResource(R.string.optional_date_goal_calendar),
                    tag = "goal_form_date",
                    enabled = !isSaving,
                    isError = dateError,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("CUENTA VINCULADA", suffix = "(opcional)")
                val selectedAccount = compatibleAccounts.firstOrNull { it.id == linkedAccount }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .clickable(role = Role.Button) {
                            pendingAccountId = linkedAccount
                            accountSheetVisible = true
                        }
                        .testTag("goal_form_account"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, kipuColors.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(kipuColors.surfaceSubtle),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (selectedAccount == null) Icons.Default.Savings else Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = kipuColors.primary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedAccount?.name ?: "Sin cuenta vinculada",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                selectedAccount?.let {
                                    "${it.currency.name} · Saldo libre: ${it.available.currency.symbol()} ${it.available.minorUnits.formatAsMoney()}"
                                } ?: "Meta virtual independiente",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "La cuenta real es solo una referencia; la reserva se elige al aportar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (iconSheetVisible) {
        ModalBottomSheet(onDismissRequest = { iconSheetVisible = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Elige un icono", style = MaterialTheme.typography.titleLarge)
                goalIcons.chunked(4).forEach { options ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (iconKey == option.key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable(role = Role.RadioButton) { iconKey = option.key; iconSheetVisible = false }
                                    .semantics { selected = iconKey == option.key }
                                    .testTag("goal_icon_${option.key}").padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                GoalIconBadge(option.key)
                                Text(option.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (accountSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { accountSheetVisible = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            var sheetSelection by remember { mutableStateOf(pendingAccountId) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vincular cuenta de respaldo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Crear una meta no congela fondos. Elegir una cuenta sirve de referencia para sugerirte aportes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { accountSheetVisible = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        AccountChoice(
                            title = "Sin cuenta vinculada",
                            subtitle = "Meta virtual independiente",
                            selected = sheetSelection == null,
                            onClick = { sheetSelection = null },
                        )
                    }
                    if (compatibleAccounts.isNotEmpty()) {
                        item {
                            Text(
                                "CUENTAS DISPONIBLES EN TU PERFIL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                            )
                        }
                        items(compatibleAccounts, key = { it.id }) { account ->
                            AccountChoice(
                                title = account.name,
                                subtitle = "${account.currency.name} · Saldo: ${account.confirmedBalance.currency.symbol()} ${account.confirmedBalance.minorUnits.formatAsMoney()}",
                                selected = sheetSelection == account.id,
                                onClick = { sheetSelection = account.id },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        linkedAccount = sheetSelection
                        accountSheetVisible = false
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Confirmar selección") }
            }
        }
    }

    if (replacementDialogVisible) {
        AlertDialog(
            onDismissRequest = { replacementDialogVisible = false; selectedReplacementId = null },
            title = { Text("Elige una meta para bloquear por plan") },
            text = {
                Column {
                    Text("Al reactivar esta meta, una de tus metas activas pasará a estar bloqueada por el plan. Sus reservas e historial se conservarán.")
                    replacementCandidates.filter { it.status == GoalStatus.ACTIVE }.forEach { candidate ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { selectedReplacementId = candidate.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedReplacementId == candidate.id,
                                onClick = { selectedReplacementId = candidate.id },
                                modifier = Modifier.testTag("replacement_radio_${candidate.id}"),
                            )
                            Text(candidate.name, modifier = Modifier.testTag("replacement_${candidate.id}"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedReplacementId != null && !isSaving,
                    onClick = {
                        val id = selectedReplacementId ?: return@TextButton
                        parsedTarget?.let { onSave(existingGoal?.id, name.trim(), it, parsedDate, linkedAccount, id, iconKey) }
                        replacementDialogVisible = false
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { replacementDialogVisible = false; selectedReplacementId = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun FieldLabel(label: String, suffix: String? = null, required: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        suffix?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (required) Text("*", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CurrencySegment(currency: Currency, modifier: Modifier = Modifier, onSelect: (Currency) -> Unit) {
    val colors = rememberKipuColors()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceSubtle)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Currency.PEN, Currency.USD).forEach { option ->
            val selected = currency == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceSubtle)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics { role = Role.RadioButton; contentDescription = option.label() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (option == Currency.PEN) "Soles\n(PEN)" else "Dólares\n(USD)",
                    color = if (selected) colors.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AccountChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = rememberKipuColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { role = Role.RadioButton; contentDescription = "$title. $subtitle" },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.selectedSurface else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (title == "Sin cuenta vinculada") Icons.Default.Savings else Icons.Default.AccountBalance,
                contentDescription = null,
                tint = colors.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = "Seleccionada", tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.size(24.dp).border(1.5.dp, colors.border, CircleShape))
            }
        }
    }
}

private fun parseGoalTarget(raw: String, currency: Currency): Money? = runCatching {
    val normalized = raw.trim().replace(',', '.')
    val decimal = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
    val minor = decimal.movePointRight(2).longValueExact()
    Money(minor, currency).takeIf { it.minorUnits > 0L }
}.getOrNull()

private fun Currency.label(): String = if (this == Currency.PEN) "Soles (PEN)" else "Dólares (USD)"

private fun Currency.symbol(): String = if (this == Currency.PEN) "S/" else "$"

private fun Long.formatAsMoney(): String = BigDecimal.valueOf(this, 2).toPlainString()
