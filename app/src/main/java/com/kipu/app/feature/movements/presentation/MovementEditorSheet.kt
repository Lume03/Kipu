package com.kipu.app.feature.movements.presentation

import com.kipu.app.ui.theme.KipuModalSystemBars
import com.kipu.app.ui.theme.kipuBottomSheetProperties

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.R
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.KipuExpense
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.theme.rememberKipuColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun MovementEditorRoute(
    transactionId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: MovementEditorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val merchantPickerViewModel: MerchantPickerViewModel = hiltViewModel()
    val merchantPickerState by merchantPickerViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MovementEditorUiEvent.CloseEditor -> onDismiss()
                is MovementEditorUiEvent.TransactionRevised -> onSaved()
                is MovementEditorUiEvent.ShowMessage -> Unit
            }
        }
    }

    MovementEditorSheet(
        uiState = uiState,
        onAmountChanged = viewModel::onAmountChanged,
        onSourceAccountSelected = viewModel::onSourceAccountSelected,
        onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
        onCategorySelected = viewModel::onCategorySelected,
        onMerchantChanged = viewModel::onMerchantChanged,
        merchantPickerState = merchantPickerState,
        onMerchantQueryChanged = merchantPickerViewModel::onQueryChanged,
        onMerchantSelected = viewModel::onMerchantSelected,
        onDateChanged = viewModel::onDateChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onSave = viewModel::onSave,
        onClose = viewModel::onAttemptClose,
        onConfirmDiscard = viewModel::onConfirmDiscard,
        onDismissDiscardDialog = viewModel::onDismissDiscardDialog,
        onDiscardConflict = { uiState.conflictProposal?.let { viewModel.onDiscardConflict(it.proposalId) } },
        onRedoConflict = { uiState.conflictProposal?.let { viewModel.onRedoConflict(it.proposalId) } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementEditorSheet(
    uiState: MovementEditorUiState,
    onAmountChanged: (String) -> Unit,
    onSourceAccountSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onCategorySelected: (CategoryOption) -> Unit,
    onMerchantChanged: (String) -> Unit,
    onDateChanged: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onDiscardConflict: () -> Unit,
    onRedoConflict: () -> Unit,
    modifier: Modifier = Modifier,
    merchantPickerState: MerchantPickerState = MerchantPickerState(),
    onMerchantQueryChanged: (String) -> Unit = {},
    onMerchantSelected: (MerchantCatalogEntry) -> Unit = { onMerchantChanged(it.name) },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        properties = kipuBottomSheetProperties(),
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("editor_sheet"),
    ) {
        KipuModalSystemBars()
        MovementEditorContent(
            uiState = uiState,
            onAmountChanged = onAmountChanged,
            onSourceAccountSelected = onSourceAccountSelected,
            onDestinationAccountSelected = onDestinationAccountSelected,
            onCategorySelected = onCategorySelected,
            onMerchantChanged = onMerchantChanged,
            merchantPickerState = merchantPickerState,
            onMerchantQueryChanged = onMerchantQueryChanged,
            onMerchantSelected = onMerchantSelected,
            onDateChanged = onDateChanged,
            onNoteChanged = onNoteChanged,
            onSave = onSave,
            onClose = onClose,
            onConfirmDiscard = onConfirmDiscard,
            onDismissDiscardDialog = onDismissDiscardDialog,
            onDiscardConflict = onDiscardConflict,
            onRedoConflict = onRedoConflict,
        )
    }
}

private enum class EditorPicker { SOURCE, DESTINATION, CATEGORY, MERCHANT, NOTE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementEditorContent(
    uiState: MovementEditorUiState,
    onAmountChanged: (String) -> Unit,
    onSourceAccountSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onCategorySelected: (CategoryOption) -> Unit,
    onMerchantChanged: (String) -> Unit,
    onDateChanged: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onDiscardConflict: () -> Unit,
    onRedoConflict: () -> Unit,
    modifier: Modifier = Modifier,
    merchantPickerState: MerchantPickerState = MerchantPickerState(),
    onMerchantQueryChanged: (String) -> Unit = {},
    onMerchantSelected: (MerchantCatalogEntry) -> Unit = { onMerchantChanged(it.name) },
) {
    val colorScheme = MaterialTheme.colorScheme
    val ec = rememberCalmEmeraldColors()
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<EditorPicker?>(null) }
    val editable = !uiState.isSpecialized && !uiState.hasConflict
    val changeCount = editorChangeCount(uiState)
    val parsedAmount = MoneyInputParser.parseMinorUnits(uiState.amountText)
    val amountInvalid = parsedAmount == null || parsedAmount <= 0L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        // Header: cerrar, título con resumen del movimiento y contador de cambios
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_close_editor")
                    .semantics { contentDescription = "Cerrar editor" },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = ec.primaryText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.movement_edit_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ec.primaryText,
                )
                if (!uiState.isLoading) {
                    Text(
                        text = editorSubtitle(uiState),
                        style = MaterialTheme.typography.bodySmall,
                        color = ec.secondaryMuted,
                        modifier = Modifier.testTag("badge_movement_type"),
                    )
                }
            }
            if (!uiState.isLoading) {
                Spacer(Modifier.width(8.dp))
                EditorChip(
                    text = when (changeCount) {
                        0 -> "Sin cambios"
                        1 -> "1 cambio"
                        else -> "$changeCount cambios"
                    },
                    highlighted = changeCount > 0,
                    ec = ec,
                    modifier = Modifier.testTag("editor_change_count"),
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("editor_loading"),
            ) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
            ) {
                if (uiState.generalError != null) {
                    Text(
                        text = uiState.generalError,
                        color = KipuExpense,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .testTag("editor_general_error"),
                    )
                }

                // Specialized Warning Card
                if (uiState.isSpecialized) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = rememberKipuColors().warningContainer),
                        border = BorderStroke(1.dp, rememberKipuColors().warning),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("warning_specialized_movement"),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = rememberKipuColors().onWarningContainer,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.specializedMessage
                                    ?: stringResource(R.string.movement_edit_specialized_warning),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = rememberKipuColors().onWarningContainer,
                            )
                        }
                    }
                }

                // Conflict Card (T072)
                if (uiState.hasConflict) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, KipuExpense),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("card_conflict_warning"),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = KipuExpense,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.movement_edit_conflict_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Versión oficial actual", style = MaterialTheme.typography.labelLarge)
                            MoneyText(formatMinorUnits(uiState.initialAmountMinor), currencySymbol = uiState.currency,
                                style = MaterialTheme.typography.bodyLarge)
                            uiState.conflictProposedAmountMinor?.let { amount ->
                                Spacer(Modifier.height(8.dp))
                                Text("Propuesta en conflicto", style = MaterialTheme.typography.labelLarge)
                                MoneyText(formatMinorUnits(amount), currencySymbol = uiState.currency,
                                    style = MaterialTheme.typography.bodyLarge)
                            }

                            if (uiState.isOfficialVoided) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.movement_edit_conflict_voided),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = KipuExpense,
                                    modifier = Modifier.testTag("text_conflict_voided"),
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = onDiscardConflict,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_conflict_discard"),
                                ) {
                                    Text(stringResource(R.string.movement_edit_conflict_discard))
                                }
                                if (!uiState.isOfficialVoided) {
                                    Button(
                                        onClick = onRedoConflict,
                                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_conflict_redo"),
                                    ) {
                                        Text(stringResource(R.string.movement_edit_conflict_redo))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "TOCA LO QUE QUIERAS CORREGIR",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = ec.secondaryMuted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )

                EditorAmountCard(
                    uiState = uiState,
                    parsedAmount = parsedAmount,
                    amountInvalid = amountInvalid,
                    enabled = editable,
                    onAmountChanged = onAmountChanged,
                    ec = ec,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ec.surfaceCard,
                    border = BorderStroke(1.dp, ec.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        val dateChanged = uiState.occurredAt != uiState.initialOccurredAt
                        EditorRow(
                            icon = Icons.Outlined.CalendarToday,
                            iconBg = ec.incomeBg,
                            iconTint = ec.primaryDeep,
                            label = stringResource(R.string.movement_date),
                            value = editorDateLabel(uiState.occurredAt),
                            modified = dateChanged,
                            before = editorDateLabel(uiState.initialOccurredAt),
                            enabled = editable,
                            tag = "editor_date_picker",
                            ec = ec,
                            onClick = { showDatePicker = true },
                        )
                        EditorRowDivider(ec)
                        val sourceName = uiState.availableAccounts.firstOrNull { it.id.value == uiState.selectedSourceAccountId }?.alias
                        val sourceBefore = uiState.availableAccounts.firstOrNull { it.id.value == uiState.initialSourceAccountId }?.alias
                        EditorRow(
                            icon = Icons.Outlined.AccountBalance,
                            iconBg = ec.transferBg,
                            iconTint = ec.transferBlue,
                            label = if (uiState.movementType == MovementType.TRANSFER) stringResource(R.string.movement_source_account) else "Cuenta",
                            value = sourceName ?: "Seleccionar cuenta",
                            modified = uiState.selectedSourceAccountId != uiState.initialSourceAccountId,
                            before = sourceBefore ?: "Cuenta histórica",
                            enabled = editable,
                            error = uiState.sourceAccountError,
                            tag = "editor_source_account",
                            errorTag = "error_source_account",
                            ec = ec,
                            onClick = { picker = EditorPicker.SOURCE },
                        )
                        if (uiState.movementType == MovementType.TRANSFER) {
                            EditorRowDivider(ec)
                            val destName = uiState.availableAccounts.firstOrNull { it.id.value == uiState.selectedDestinationAccountId }?.alias
                            val destBefore = uiState.availableAccounts.firstOrNull { it.id.value == uiState.initialDestinationAccountId }?.alias
                            EditorRow(
                                icon = Icons.Outlined.AccountBalance,
                                iconBg = ec.transferBg,
                                iconTint = ec.transferBlue,
                                label = stringResource(R.string.movement_destination_account),
                                value = destName ?: "Seleccionar cuenta",
                                modified = uiState.selectedDestinationAccountId != uiState.initialDestinationAccountId,
                                before = destBefore ?: "Cuenta histórica",
                                enabled = editable,
                                error = uiState.destinationAccountError,
                                tag = "editor_destination_account",
                                errorTag = "error_destination_account",
                                ec = ec,
                                onClick = { picker = EditorPicker.DESTINATION },
                            )
                        } else {
                            EditorRowDivider(ec)
                            EditorRow(
                                icon = Icons.Outlined.Category,
                                iconBg = ec.warningBg,
                                iconTint = ec.warningText,
                                label = stringResource(R.string.movement_category) + if (uiState.movementType == MovementType.EXPENSE) " *" else "",
                                value = uiState.selectedCategoryName ?: "Seleccionar categoría",
                                modified = uiState.selectedCategoryId != uiState.initialCategoryId,
                                before = uiState.initialCategoryName ?: "Sin categoría",
                                enabled = editable,
                                error = uiState.categoryError,
                                tag = "editor_category_picker",
                                errorTag = "error_category",
                                ec = ec,
                                onClick = { picker = EditorPicker.CATEGORY },
                            )
                        }
                        EditorRowDivider(ec)
                        val merchantChanged = uiState.merchantName.trim() != uiState.initialMerchantName.orEmpty().trim() || uiState.selectedMerchantId != uiState.initialMerchantId
                        EditorRow(
                            icon = Icons.Outlined.Storefront,
                            iconBg = colorScheme.tertiaryContainer,
                            iconTint = colorScheme.onTertiaryContainer,
                            label = stringResource(R.string.movement_merchant),
                            value = uiState.merchantName.trim().ifBlank { "Sin comercio" },
                            modified = merchantChanged,
                            before = uiState.initialMerchantName.orEmpty().trim().ifBlank { "Sin comercio" },
                            enabled = editable,
                            tag = "editor_merchant_row",
                            ec = ec,
                            onClick = { picker = EditorPicker.MERCHANT },
                        )
                        EditorRowDivider(ec)
                        val noteChanged = uiState.note.trim() != uiState.initialNote.orEmpty().trim()
                        EditorRow(
                            icon = Icons.Outlined.Description,
                            iconBg = ec.pillTrack,
                            iconTint = ec.secondaryMuted,
                            label = stringResource(R.string.movement_note),
                            value = uiState.note.trim().ifBlank { "Sin nota" },
                            modified = noteChanged,
                            before = uiState.initialNote.orEmpty().trim().ifBlank { "Sin nota" },
                            enabled = editable,
                            tag = "editor_note_row",
                            ec = ec,
                            onClick = { picker = EditorPicker.NOTE },
                        )
                    }
                }

                MovementChangeSummary(uiState)

                if (changeCount == 0 && editable) {
                    Text(
                        text = "Aún no cambiaste nada. Al guardar se recalculan el saldo de la cuenta, el presupuesto y tus métricas, y se conserva la versión anterior.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ec.secondaryMuted,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .testTag("editor_no_changes_hint"),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Pie fijo: acción principal siempre visible
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val canSave = editable && uiState.hasUnsavedChanges && !uiState.isSaving && !amountInvalid
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ec.primaryDeep,
                        contentColor = ec.onPrimaryDeep,
                        disabledContainerColor = ec.pillTrack,
                        disabledContentColor = ec.secondaryMuted,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("editor_btn_save"),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = colorScheme.onPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("editor_saving_progress"),
                        )
                    } else {
                        Text(
                            text = when {
                                !editable -> stringResource(R.string.movement_edit_save)
                                changeCount == 0 -> "Sin cambios para guardar"
                                amountInvalid -> "Corrige el monto para guardar"
                                changeCount == 1 -> "Guardar 1 cambio"
                                else -> "Guardar $changeCount cambios"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
                if (changeCount > 0 && editable) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_discard_changes"),
                    ) {
                        Text(
                            text = "Descartar cambios",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = ec.expenseText,
                        )
                    }
                }
            }
        }
    }

    // Hojas de selección y edición por fila
    when (picker) {
        EditorPicker.SOURCE -> AccountPickerSheet(
            purpose = when (uiState.movementType) {
                MovementType.EXPENSE -> AccountSelectorPurpose.EXPENSE_SOURCE
                MovementType.INCOME -> AccountSelectorPurpose.INCOME_DESTINATION
                MovementType.TRANSFER -> AccountSelectorPurpose.TRANSFER_SOURCE
            },
            title = if (uiState.movementType == MovementType.TRANSFER) "Cuenta origen" else "Cuenta",
            accounts = uiState.availableAccounts,
            selectedId = uiState.selectedSourceAccountId,
            onSelect = { onSourceAccountSelected(it); picker = null },
            onDismiss = { picker = null },
        )
        EditorPicker.DESTINATION -> AccountPickerSheet(
            purpose = AccountSelectorPurpose.TRANSFER_DESTINATION,
            title = "Cuenta destino",
            accounts = uiState.availableAccounts,
            selectedId = uiState.selectedDestinationAccountId,
            onSelect = { onDestinationAccountSelected(it); picker = null },
            onDismiss = { picker = null },
        )
        EditorPicker.CATEGORY -> CategoryPickerSheet(
            categories = uiState.availableCategories,
            selectedId = uiState.selectedCategoryId,
            isIncome = uiState.movementType == MovementType.INCOME,
            onSelect = { onCategorySelected(it); picker = null },
            onDismiss = { picker = null },
        )
        EditorPicker.MERCHANT -> MerchantPickerBottomSheet(
            state = merchantPickerState.copy(
                selectedMerchant = merchantPickerState.catalogEntries.firstOrNull { it.id.value == uiState.selectedMerchantId },
                provisionalText = uiState.merchantName.takeIf { uiState.selectedMerchantId == null && it.isNotBlank() },
            ),
            onQueryChange = onMerchantQueryChanged,
            onSelectMerchant = onMerchantSelected,
            onSetProvisionalText = onMerchantChanged,
            onClearSelection = { onMerchantChanged("") },
            onDismiss = { onMerchantQueryChanged(""); picker = null },
        )
        EditorPicker.NOTE -> TextEditSheet(
            title = "Editar nota",
            value = uiState.note,
            onValueChange = onNoteChanged,
            singleLine = false,
            hint = "Puedes dejarla vacía.",
            inputTag = "editor_note_input",
            onDismiss = { picker = null },
            ec = ec,
        )
        null -> Unit
    }

    // Descartar cambios (hoja inferior)
    if (uiState.showConfirmDiscardDialog) {
        KipuBottomSheet(
            onDismissRequest = onDismissDiscardDialog,
            modifier = Modifier.testTag("dialog_confirm_discard"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "¿Descartar los cambios?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ec.primaryText,
                )
                Text(
                    text = if (changeCount == 0) {
                        "Si sales ahora, el movimiento queda como estaba."
                    } else if (changeCount == 1) {
                        "Modificaste 1 campo. Si sales ahora, el movimiento queda como estaba."
                    } else {
                        "Modificaste $changeCount campos. Si sales ahora, el movimiento queda como estaba."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ec.secondaryMuted,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onConfirmDiscard,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C), contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("btn_confirm_discard"),
                ) {
                    Text("Sí, descartar", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                }
                OutlinedButton(
                    onClick = onDismissDiscardDialog,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("btn_cancel_discard"),
                ) {
                    Text(
                        "Seguir editando",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = ec.primaryText,
                    )
                }
            }
        }
    }

    // DatePicker Dialog
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.occurredAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { selected ->
                            onDateChanged(selected)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun EditorChip(text: String, highlighted: Boolean, ec: CalmEmeraldColors, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) ec.incomeBg else ec.pillTrack,
        border = if (highlighted) BorderStroke(1.dp, ec.incomeBorder) else null,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (highlighted) ec.primaryDeep else ec.secondaryMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorAmountCard(
    uiState: MovementEditorUiState,
    parsedAmount: Long?,
    amountInvalid: Boolean,
    enabled: Boolean,
    onAmountChanged: (String) -> Unit,
    ec: CalmEmeraldColors,
) {
    val masked = LocalBalanceMasked.current
    val currency = remember(uiState.currency) { runCatching { Currency.fromCode(uiState.currency) }.getOrDefault(Currency.PEN) }
    val symbol = currency.symbol()
    val modified = parsedAmount != null && parsedAmount != uiState.initialAmountMinor
    val hasError = uiState.amountError != null || amountInvalid
    val accent = when {
        hasError -> ec.expenseText
        modified -> ec.primaryDeep
        else -> ec.borderSubtle
    }
    val amountColor = when (uiState.movementType) {
        MovementType.EXPENSE -> ec.expenseText
        MovementType.INCOME -> ec.incomeText
        MovementType.TRANSFER -> ec.transferBlue
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ec.surfaceCard,
        border = BorderStroke(if (hasError || modified) 2.dp else 1.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.movement_amount_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ec.primaryText,
                )
                EditorChip(if (modified) "Modificado" else "Original", modified, ec)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ec.secondaryMuted,
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = uiState.amountText,
                    onValueChange = onAmountChanged,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                        color = amountColor,
                    ),
                    cursorBrush = SolidColor(ec.primaryDeep),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .testTag("editor_amount_input")
                        .privateAmount(masked, enabled, onChange = onAmountChanged),
                )
            }
            when {
                uiState.amountError != null || amountInvalid -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ec.expenseText, modifier = Modifier.size(16.dp))
                    Text(
                        text = uiState.amountError ?: "Ingresa un monto mayor a $symbol 0.00",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = ec.expenseText,
                        modifier = Modifier.testTag("error_amount"),
                    )
                }
                modified -> {
                    val diff = parsedAmount!! - uiState.initialAmountMinor
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            Text("Antes: ", style = MaterialTheme.typography.bodySmall, color = ec.secondaryMuted)
                            MoneyText(
                                formatMinorUnits(uiState.initialAmountMinor),
                                currencySymbol = symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = ec.secondaryMuted,
                            )
                        }
                        Row {
                            Text("Diferencia ", style = MaterialTheme.typography.bodySmall, color = ec.secondaryMuted)
                            MoneyText(
                                (if (diff < 0) "-" else "+") + formatMinorUnits(abs(diff)),
                                currencySymbol = symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = ec.secondaryMuted,
                            )
                        }
                    }
                }
                else -> Text(
                    text = "Toca el monto para cambiarlo",
                    style = MaterialTheme.typography.bodySmall,
                    color = ec.secondaryMuted,
                )
            }
        }
    }
}

@Composable
private fun EditorRowDivider(ec: CalmEmeraldColors) {
    HorizontalDivider(color = ec.borderSubtle, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun EditorRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    value: String,
    modified: Boolean,
    before: String?,
    enabled: Boolean,
    tag: String,
    ec: CalmEmeraldColors,
    onClick: () -> Unit,
    error: String? = null,
    errorTag: String = "",
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(tag),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(12.dp)),
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = ec.secondaryMuted)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = ec.primaryText,
                )
                if (modified && before != null) {
                    Text(
                        text = "Antes: $before",
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = ec.secondaryMuted,
                    )
                }
            }
            if (modified) {
                Spacer(Modifier.width(8.dp))
                EditorChip("Modificado", true, ec)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ec.secondaryMuted,
            )
        }
        if (error != null) {
            Text(
                text = error,
                color = ec.expenseText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = 68.dp, end = 16.dp, bottom = 8.dp)
                    .testTag(errorTag),
            )
        }
    }
}

@Composable
private fun SheetTitle(title: String, ec: CalmEmeraldColors) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = ec.primaryText,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun PickerOptionRow(text: String, selected: Boolean, ec: CalmEmeraldColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = ec.primaryText,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Seleccionada", tint = ec.primaryDeep)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    purpose: AccountSelectorPurpose,
    title: String,
    accounts: List<Account>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AccountSelectorBottomSheet(
        onDismissRequest = onDismiss,
        accounts = accounts,
        selectedAccountId = selectedId,
        purpose = purpose,
        title = title,
        subtitle = AccountSelectorDefaults.subtitle(purpose),
        showNewAccountAction = false,
        onAccountSelected = { onSelect(it.id.value) },
        modifier = Modifier.testTag("editor_account_sheet"),
    )
}

@Composable
private fun CategoryPickerSheet(
    categories: List<CategoryOption>,
    selectedId: String?,
    isIncome: Boolean,
    onSelect: (CategoryOption) -> Unit,
    onDismiss: () -> Unit,
) {
    PastelCategoryPickerBottomSheet(
        categories = categories,
        selectedCategoryId = selectedId,
        onSelect = onSelect,
        onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
        onDismiss = onDismiss,
        directSelectionMode = true,
        allowCreate = false,
        subtitle = if (isIncome) "Selecciona una categoría o subcategoría para clasificar tu ingreso"
        else "Selecciona una categoría o subcategoría para clasificar tu gasto",
        modifier = Modifier.testTag("editor_category_sheet"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditSheet(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    hint: String,
    inputTag: String,
    onDismiss: () -> Unit,
    ec: CalmEmeraldColors,
) {
    KipuBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ec.primaryText,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ec.secondaryMuted)
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ec.primaryDeep,
                    unfocusedBorderColor = ec.borderSubtle,
                    cursorColor = ec.primaryDeep,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(inputTag),
            )
            Text(
                text = "${value.length} caracteres · $hint",
                style = MaterialTheme.typography.bodySmall,
                color = ec.secondaryMuted,
            )
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ec.primaryDeep, contentColor = ec.onPrimaryDeep),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_editor_sheet_done"),
            ) {
                Text("Listo", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun editorChangeCount(s: MovementEditorUiState): Int {
    if (s.isSpecialized) return 0
    val amount = MoneyInputParser.parseMinorUnits(s.amountText)
    return listOf(
        amount != null && amount != s.initialAmountMinor,
        s.occurredAt != s.initialOccurredAt,
        s.selectedSourceAccountId != s.initialSourceAccountId,
        s.selectedDestinationAccountId != s.initialDestinationAccountId,
        s.selectedCategoryId != s.initialCategoryId,
        s.merchantName.trim() != s.initialMerchantName.orEmpty().trim() || s.selectedMerchantId != s.initialMerchantId,
        s.note.trim() != s.initialNote.orEmpty().trim(),
    ).count { it }
}

private val editorDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "PE"))

private fun editorDateLabel(epochMilli: Long): String {
    if (epochMilli <= 0L) return "Seleccionar fecha"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val text = date.format(editorDateFormatter).replace(".", "").lowercase().lowercase()
    return when (date) {
        today -> "Hoy · $text"
        today.minusDays(1) -> "Ayer · $text"
        else -> text
    }
}

private fun editorSubtitle(s: MovementEditorUiState): String {
    val type = when (s.movementType) {
        MovementType.EXPENSE -> "Gasto"
        MovementType.INCOME -> "Ingreso"
        MovementType.TRANSFER -> "Transferencia"
    }
    val date = if (s.initialOccurredAt > 0L) {
        Instant.ofEpochMilli(s.initialOccurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(editorDateFormatter).replace(".", "").lowercase()
    } else null
    return listOfNotNull(type, s.initialMerchantName?.trim()?.takeIf { it.isNotBlank() }, date).joinToString(" · ")
}
