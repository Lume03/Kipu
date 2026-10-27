package com.kipu.app.feature.movements.presentation

import com.kipu.app.ui.theme.KipuModalSystemBars
import com.kipu.app.ui.theme.kipuBottomSheetProperties

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ArrowUpward
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.KipuOfflineBanner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.ContentCopy
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.MoneyText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMovementBottomSheet(
    onDismissRequest: () -> Unit,
    onMessage: (String) -> Unit = {},
    onSaved: (MovementType) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: QuickMovementViewModel = hiltViewModel(),
    merchantPickerViewModel: MerchantPickerViewModel = hiltViewModel(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onNavigateToNewAccount: () -> Unit = {},
    mostUsedAccountId: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val merchantPickerState by merchantPickerViewModel.uiState.collectAsStateWithLifecycle()
    var showMerchantPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val emeraldColors = rememberCalmEmeraldColors()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshAutomaticTime()
                kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickMovementUiEvent.TransactionSaved -> {
                    onSaved(event.movementType)
                    onDismissRequest()
                    onMessage(event.message)
                }
                is QuickMovementUiEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    ModalBottomSheet(
        properties = kipuBottomSheetProperties(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = emeraldColors.surfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        },
        modifier = modifier.testTag("quick_movement_sheet"),
    ) {
        KipuModalSystemBars()
        QuickMovementContent(
            uiState = uiState,
            onTypeSelected = viewModel::onTypeSelected,
            onAmountChanged = viewModel::onAmountChanged,
            onSourceAccountSelected = viewModel::onSourceAccountSelected,
            onSourceCardSelected = viewModel::onSourceCardSelected,
            onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
            onDestinationCardSelected = viewModel::onDestinationCardSelected,
            onCategorySelected = viewModel::onCategorySelected,
            onOpenMerchantPicker = { showMerchantPicker = true },
            onClearMerchant = {
                merchantPickerViewModel.clearSelection()
                viewModel.onMerchantCleared()
            },
            onNoteChanged = viewModel::onNoteChanged,
            onOpenDatePicker = { showDatePicker = true },
            onToggleMoreDetails = viewModel::onToggleMoreDetails,
            onSave = viewModel::onSave,
            onClose = onDismissRequest,
            accountBalances = uiState.accountBalances,
            creditCardDebts = uiState.creditCardDebts,
            creditCardAvailableCredits = uiState.creditCardAvailableCredits,
            mostUsedAccountId = mostUsedAccountId,
            onNavigateToNewAccount = onNavigateToNewAccount,
            onCreateSubcategory = viewModel::createCategoryOrSubcategory,
            isCreatingSubcategory = uiState.isCreatingSubcategory,
            categoryCreationError = uiState.categoryCreationError,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    if (uiState.isSaving) {
        SavingMovementDialog()
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = toUtcDateMillis(uiState.occurredAt),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { selectedDateMillis ->
                            viewModel.onOccurredAtChanged(
                                datePickerMillisToLocalInstant(selectedDateMillis, uiState.occurredAt),
                            )
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Listo", color = emeraldColors.primaryDeep)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Cancelar", color = emeraldColors.secondaryMuted)
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showMerchantPicker) {
        MerchantPickerBottomSheet(
            state = merchantPickerState,
            onQueryChange = merchantPickerViewModel::onQueryChanged,
            onSelectMerchant = { merchant ->
                merchantPickerViewModel.selectMerchant(merchant)
                viewModel.onMerchantSelected(merchant)
            },
            onSetProvisionalText = { name ->
                merchantPickerViewModel.setProvisionalText(name)
                viewModel.onMerchantProvisionalText(name)
            },
            onClearSelection = {
                merchantPickerViewModel.clearSelection()
                viewModel.onMerchantCleared()
            },
            onDismiss = { showMerchantPicker = false },
        )
    }

    if (uiState.showDuplicateWarning) {
        DuplicateWarningDialog(
            onConfirm = viewModel::onConfirmDuplicate,
            onDismiss = viewModel::onDismissDuplicate,
            existing = uiState.duplicateExistingTransaction,
            proposed = uiState.pendingCommandForDuplicate,
            accountAlias = { id -> uiState.availableAccounts.firstOrNull { it.id.value == id }?.alias },
            proposedMerchant = uiState.merchantName,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMovementContent(
    uiState: QuickMovementUiState,
    onTypeSelected: (MovementType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onSourceAccountSelected: (String) -> Unit,
    onSourceCardSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onCategorySelected: (CategoryOption) -> Unit,
    onOpenMerchantPicker: () -> Unit,
    onClearMerchant: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onToggleMoreDetails: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    accountBalances: Map<String, Money>,
    creditCardDebts: Map<String, Money>,
    creditCardAvailableCredits: Map<String, Money>,
    mostUsedAccountId: String?,
    onNavigateToNewAccount: () -> Unit,
    onCreateSubcategory: suspend (String?, String, String, String, Boolean) -> Result<CategoryOption>,
    isCreatingSubcategory: Boolean,
    categoryCreationError: String?,
    modifier: Modifier = Modifier,
    onDestinationCardSelected: (String) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val emeraldColors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val animDuration = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showSourceAccountPicker by remember { mutableStateOf(false) }
    var showDestinationAccountPicker by remember { mutableStateOf(false) }
    var sourceAccountPickerCurrency by remember { mutableStateOf<String?>(null) }
    var showOptionalFields by remember { mutableStateOf(false) }
    val offline = rememberNetworkOffline()
    val balanceMasked = LocalBalanceMasked.current

    // Separate field validation errors from general transient errors
    val fieldErrors = remember(uiState.amountError, uiState.accountError, uiState.categoryError, uiState.destinationAccountError) {
        listOfNotNull(
            uiState.amountError,
            uiState.accountError,
            uiState.categoryError,
            uiState.destinationAccountError,
        ).distinct()
    }
    val hasFieldErrors = fieldErrors.isNotEmpty()
    val fieldErrorCount = fieldErrors.size
    val generalError = uiState.generalError
    val hasGeneralError = !generalError.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.movement_register_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = emeraldColors.primaryText,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (hasFieldErrors) {
                    Surface(
                        shape = CircleShape,
                        color = emeraldColors.expenseBg,
                        border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                    ) {
                        Text(
                            text = if (fieldErrorCount == 1) "1 error" else "$fieldErrorCount errores",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.expenseText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Cerrar formulario" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = emeraldColors.secondaryMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Active validation or error banner
        if (hasFieldErrors || hasGeneralError) {
            val bannerMessage = if (hasFieldErrors) {
                fieldErrors.first()
            } else {
                generalError ?: "Corrige los datos requeridos"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = emeraldColors.expenseBg,
                border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = emeraldColors.expenseText,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = bannerMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = emeraldColors.expenseText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (offline) {
                KipuOfflineBanner(message = "Puedes registrar movimientos. Se sincronizarán al recuperar la conexión.")
            }
            // 3 Standalone Type Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalmEmeraldTypeTab(
                    type = MovementType.EXPENSE,
                    label = stringResource(R.string.movement_type_expense),
                    selected = uiState.type == MovementType.EXPENSE,
                    onSelect = { onTypeSelected(MovementType.EXPENSE) },
                    modifier = Modifier.weight(1f),
                )
                CalmEmeraldTypeTab(
                    type = MovementType.INCOME,
                    label = stringResource(R.string.movement_type_income),
                    selected = uiState.type == MovementType.INCOME,
                    onSelect = { onTypeSelected(MovementType.INCOME) },
                    modifier = Modifier.weight(1f),
                )
                CalmEmeraldTypeTab(
                    type = MovementType.TRANSFER,
                    label = stringResource(R.string.movement_type_transfer),
                    selected = uiState.type == MovementType.TRANSFER,
                    onSelect = { onTypeSelected(MovementType.TRANSFER) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Hero Type-Tinted Amount Card
            val (heroBg, heroBorder, heroText) = when (uiState.type) {
                MovementType.EXPENSE -> Triple(emeraldColors.expenseBg, emeraldColors.expenseBorder, emeraldColors.expenseText)
                MovementType.INCOME -> Triple(emeraldColors.incomeBg, emeraldColors.incomeBorder, emeraldColors.incomeText)
                MovementType.TRANSFER -> Triple(emeraldColors.transferBg, emeraldColors.transferBorder, emeraldColors.primaryText)
            }
            val effectiveBorder = if (uiState.amountError != null) emeraldColors.expenseText else heroBorder

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = heroBg),
                border = BorderStroke(1.dp, effectiveBorder),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = when (uiState.type) {
                                MovementType.EXPENSE -> "MONTO DEL GASTO"
                                MovementType.INCOME -> "MONTO DEL INGRESO"
                                MovementType.TRANSFER -> "MONTO A TRANSFERIR"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                            ),
                            color = when (uiState.type) {
                                MovementType.EXPENSE -> emeraldColors.expenseText
                                MovementType.INCOME -> emeraldColors.incomeText
                                MovementType.TRANSFER -> emeraldColors.transferBlue
                            },
                        )

                        // Segmented Currency Selector: PEN / USD
                        val hasPen = remember(uiState.availableAccounts, uiState.availableCreditCards, uiState.type) {
                            uiState.availableAccounts.any {
                                it.type != com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY &&
                                    it.currency.name == "PEN"
                            } ||
                                (uiState.type == MovementType.EXPENSE && uiState.availableCreditCards.any { it.currency.name == "PEN" })
                        }
                        val hasUsd = remember(uiState.availableAccounts, uiState.availableCreditCards, uiState.type) {
                            uiState.availableAccounts.any {
                                it.type != com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY &&
                                    it.currency.name == "USD"
                            } ||
                                (uiState.type == MovementType.EXPENSE && uiState.availableCreditCards.any { it.currency.name == "USD" })
                        }
                        val isPen = uiState.currency == "PEN"
                        val isUsd = uiState.currency == "USD"

                        if (hasPen && hasUsd) {
                            FlowRow(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(emeraldColors.pillTrack)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                CurrencyCapsuleTab(
                                    label = "PEN (S/)",
                                    selected = isPen,
                                    onSelect = {
                                        sourceAccountPickerCurrency = "PEN"
                                        showSourceAccountPicker = true
                                    },
                                    modifier = Modifier.testTag("currency_pen"),
                                )

                                CurrencyCapsuleTab(
                                    label = "USD ($)",
                                    selected = isUsd,
                                    onSelect = {
                                        sourceAccountPickerCurrency = "USD"
                                        showSourceAccountPicker = true
                                    },
                                    modifier = Modifier.testTag("currency_usd"),
                                )
                            }
                        } else {
                            Text(
                                text = if (hasUsd) "USD ($)" else "PEN (S/)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = emeraldColors.secondaryMuted,
                                modifier = Modifier
                                    .testTag("currency_single_option")
                                    .semantics { contentDescription = "Moneda ${if (hasUsd) "USD" else "PEN"}" },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val signPrefix = when (uiState.type) {
                        MovementType.EXPENSE -> "− "
                        MovementType.INCOME -> "+ "
                        MovementType.TRANSFER -> ""
                    }
                    val currSymbol = if (uiState.currency == "PEN") "S/ " else "$ "

                    OutlinedTextField(
                        value = uiState.amountText,
                        onValueChange = onAmountChanged,
                        placeholder = {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldColors.secondaryMuted,
                                )
                            )
                        },
                        prefix = {
                            Text(
                                "$signPrefix$currSymbol",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = heroText,
                                )
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                            color = heroText,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.amountError != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .testTag("input_amount")
                            .semantics { contentDescription = "Monto de la transacción" },
                    )

                    if (uiState.amountError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = emeraldColors.expenseText,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = uiState.amountError,
                                color = emeraldColors.expenseText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Selection Rows: Accounts
            val selectedSourceAccount = uiState.availableAccounts.firstOrNull {
                it.id.value == uiState.selectedSourceAccountId
            }
            val selectedSourceCard = uiState.availableCreditCards.firstOrNull {
                it.id.value == uiState.selectedSourceCardId
            }

            val (sourceIcon, sourceIconBg, sourceIconTint) = remember(selectedSourceAccount, selectedSourceCard, emeraldColors.isDark) {
                val icon = when {
                    selectedSourceCard != null -> Icons.Default.CreditCard
                    selectedSourceAccount != null -> when (selectedSourceAccount.type) {
                        com.kipu.app.feature.accounts.domain.model.AccountType.CASH -> Icons.Default.Payments
                        com.kipu.app.feature.accounts.domain.model.AccountType.DIGITAL_WALLET -> Icons.Default.AccountBalanceWallet
                        com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS,
                        com.kipu.app.feature.accounts.domain.model.AccountType.BANK -> Icons.Default.AccountBalance
                        com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY -> Icons.Default.CreditCard
                    }
                    else -> Icons.Default.AccountBalanceWallet
                }
                val bg = if (emeraldColors.isDark) Color(0xFF134E4A) else Color(0xFFE6F7F3)
                val tint = if (emeraldColors.isDark) Color(0xFF5EEAD4) else Color(0xFF075E52)
                Triple(icon, bg, tint)
            }

            val sourceTitle = selectedSourceAccount?.alias
                ?: selectedSourceCard?.alias
                ?: selectedSourceCard?.let { "${it.issuer} ${it.network.name}" }
                ?: "Seleccionar cuenta"
            val sourceSubtitle = selectedSourceAccount?.let { account ->
                val balance = accountBalances[account.linkedAccountId?.value ?: account.id.value]?.let { if (balanceMasked) "••••" else formatMoneyInline(it) } ?: "no disponible"
                if (uiState.accountError != null) "Saldo: $balance · Insuficiente"
                else "${accountTypeLabel(account.type)} ${account.currency.name} · Saldo $balance"
            } ?: selectedSourceCard?.let { card ->
                val debt = creditCardDebts[card.id.value]?.let { " · Deuda ${if (balanceMasked) "••••" else formatMoneyInline(it)}" }.orEmpty()
                "Crédito · •••• ${card.lastFourDigits}$debt"
            } ?: "Elige dónde registrar este movimiento"

            val sourceBrandName = if (selectedSourceAccount?.type == com.kipu.app.feature.accounts.domain.model.AccountType.CASH) null
                else selectedSourceAccount?.preset?.name ?: selectedSourceCard?.issuer ?: sourceTitle
            // Account selection row (or transfer grouped card)
            if (uiState.type == MovementType.TRANSFER) {
                // Grouped Transfer Source & Destination
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Source
                        TransferAccountRow(
                            brandName = sourceBrandName,
                            label = "Cuenta origen / Desde",
                            value = sourceTitle,
                            supportingText = sourceSubtitle,
                            icon = sourceIcon,
                            iconBg = sourceIconBg,
                            iconTint = sourceIconTint,
                            onClick = { showSourceAccountPicker = true },
                            modifier = Modifier.testTag("selector_source_account"),
                        )

                        // Divider with Down Arrow Circle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = emeraldColors.transferBg,
                                border = BorderStroke(1.dp, emeraldColors.transferBorder),
                                modifier = Modifier.size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = emeraldColors.transferBlue,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        // Destination
                        val eligibleDestinationAccounts = remember(uiState.availableAccounts, uiState.selectedSourceAccountId, uiState.currency) {
                            uiState.availableAccounts.filter { account ->
                                account.id.value != uiState.selectedSourceAccountId && account.currency.name == uiState.currency
                            }
                        }
                        val destinationCard = uiState.availableCreditCards.firstOrNull { it.id.value == uiState.selectedDestinationCardId }
                        val hasDestinations = eligibleDestinationAccounts.isNotEmpty() || uiState.availableCreditCards.any { it.currency.name == uiState.currency }
                        val selectedDestination = eligibleDestinationAccounts.firstOrNull {
                            it.id.value == uiState.selectedDestinationAccountId
                        }

                        TransferAccountRow(
                            label = "Cuenta destino / Hacia",
                            brandName = destinationCard?.issuer ?: selectedDestination?.preset?.name ?: selectedDestination?.alias,
                            value = destinationCard?.alias ?: destinationCard?.let { "${it.issuer} •••• ${it.lastFourDigits}" } ?: selectedDestination?.alias
                                ?: if (!hasDestinations) "Sin cuenta destino disponible" else "Seleccionar cuenta destino",
                            supportingText = destinationCard?.let { "Pago de tarjeta · Reduce la deuda" } ?: selectedDestination?.let { "${accountTypeLabel(it.type)} · ${it.currency.name}" }
                                ?: if (!hasDestinations) "Se requiere otra cuenta distinta en ${uiState.currency}" else "Elige la cuenta que recibirá el monto",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconBg = if (emeraldColors.isDark) Color(0xFF1E3A8A) else Color(0xFFEFF6FF),
                            iconTint = emeraldColors.transferBlue,
                            onClick = { showDestinationAccountPicker = true },
                            modifier = Modifier.testTag("selector_destination_account"),
                        )

                        if (!hasDestinations) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onNavigateToNewAccount,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_create_destination_account"),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("+ Crear otra cuenta en ${uiState.currency}")
                            }
                        }
                    }
                }

                Text(
                    text = if (uiState.selectedDestinationCardId != null)
                        "Pago de tarjeta: reduce el saldo de origen y la deuda. No es un gasto nuevo."
                    else "Mueve dinero entre tus cuentas. No cambia tu patrimonio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = emeraldColors.secondaryMuted,
                )
            } else {
                CalmEmeraldSelectionRow(
                    brandName = sourceBrandName,
                    icon = sourceIcon,
                    iconContainer = sourceIconBg,
                    iconTint = sourceIconTint,
                    label = if (uiState.type == MovementType.INCOME) "Cuenta de destino" else "Cuenta de cargo",
                    value = sourceTitle,
                    supportingText = sourceSubtitle,
                    onClick = { showSourceAccountPicker = true },
                    isError = uiState.accountError != null,
                    errorMessage = uiState.accountError,
                    trailingActionText = if (uiState.accountError != null) "Cambiar cuenta ⇄" else null,
                    onTrailingActionClick = { showSourceAccountPicker = true },
                    modifier = Modifier.testTag("selector_source_account"),
                )
            }

            if (selectedSourceCard != null && uiState.type == MovementType.EXPENSE) {
                Text("Este gasto suma a la deuda de tu tarjeta. No mueve dinero propio.",
                    color = emeraldColors.secondaryMuted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("credit_purchase_impact"))
            }
            uiState.budgetImpacts.forEach { impact ->
                val exceeded = impact.projected.isExceeded
                Surface(shape = RoundedCornerShape(14.dp),
                    color = if (exceeded) emeraldColors.warningBg else emeraldColors.incomeBg,
                    modifier = Modifier.fillMaxWidth().testTag("budget_impact")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (exceeded) "Este gasto te pasa del presupuesto" else "Impacto en presupuesto",
                            fontWeight = FontWeight.SemiBold, color = emeraldColors.primaryText)
                        Text("${impact.name}: ${impact.consumption.percentWhole}% → ${impact.projected.percentWhole}%",
                            color = emeraldColors.primaryText)
                        if (exceeded) {
                            val excess = if (balanceMasked) "••••" else
                                "${if (impact.currency == "PEN") "S/" else "$"} ${formatMinorUnits(-impact.projected.remainingMinor)}"
                            Text("Te pasarás en total $excess", color = emeraldColors.warningText)
                            Text("El gasto se registra igual: un presupuesto es planificación y no bloquea movimientos.",
                                color = emeraldColors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Category Row (for Expense and Income)
            AnimatedVisibility(
                visible = uiState.type != MovementType.TRANSFER,
                enter = fadeIn(tween(animDuration)) + expandVertically(tween(animDuration)),
                exit = fadeOut(tween(animDuration)) + shrinkVertically(tween(animDuration)),
            ) {
                val hasCategory = !uiState.selectedCategoryName.isNullOrBlank()
                val (catIcon, catBg, catTint) = if (hasCategory) {
                    val colorsPair = categoryPastelColors(uiState.selectedCategoryName.orEmpty())
                    Triple(
                        resolveCategoryIcon(uiState.selectedCategoryIcon ?: "label"),
                        colorsPair.first,
                        colorsPair.second,
                    )
                } else {
                    val bg = if (emeraldColors.isDark) Color(0xFF2E1065) else Color(0xFFF3E8FF)
                    val tint = if (emeraldColors.isDark) Color(0xFFC4B5FD) else Color(0xFF7E22CE)
                    Triple(Icons.Default.Label, bg, tint)
                }

                CalmEmeraldSelectionRow(
                    icon = catIcon,
                    iconContainer = catBg,
                    iconTint = catTint,
                    label = if (uiState.type == MovementType.EXPENSE) "Categoría *" else "Categoría (opcional)",
                    value = uiState.selectedCategoryName ?: "Sin categoría seleccionada",
                    supportingText = when {
                        uiState.categoryError != null -> "Este campo es obligatorio"
                        uiState.selectedCategoryName != null -> "Toca para cambiar"
                        uiState.type == MovementType.INCOME -> "Opcional"
                        else -> "Selecciona una categoría requerida"
                    },
                    onClick = { showCategoryPicker = true },
                    isError = uiState.categoryError != null,
                    errorMessage = uiState.categoryError,
                    modifier = Modifier.testTag("category_picker_open"),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = showOptionalFields,
                        role = Role.Button,
                        onValueChange = { showOptionalFields = it },
                    )
                    .semantics {
                        stateDescription = if (showOptionalFields) "Expandido" else "Contraído"
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Más detalles (opcional)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = emeraldColors.secondaryMuted,
                    )
                    if (!showOptionalFields && uiState.merchantName.isNotBlank()) {
                        Text(
                            text = uiState.merchantName,
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
                Icon(
                    imageVector = if (showOptionalFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = emeraldColors.secondaryMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = showOptionalFields,
                enter = fadeIn(tween(animDuration)) + expandVertically(tween(animDuration)),
                exit = fadeOut(tween(animDuration)) + shrinkVertically(tween(animDuration)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Keep these optional controls in one visibility group so resizing the sheet
                    // (for example, when the keyboard opens) cannot hide the merchant selector.
                    val (dateBg, dateTint) = if (emeraldColors.isDark) {
                        Color(0xFF082F49) to Color(0xFF7DD3FC)
                    } else {
                        Color(0xFFE0F2FE) to Color(0xFF0284C7)
                    }
                    val formattedDate = remember(uiState.occurredAt) { formatTransactionDate(uiState.occurredAt) }

                    CalmEmeraldSelectionRow(
                        icon = Icons.Default.CalendarToday,
                        iconContainer = dateBg,
                        iconTint = dateTint,
                        label = "Fecha y hora",
                        value = formattedDate,
                        supportingText = "Toca para cambiar la fecha",
                        onClick = onOpenDatePicker,
                        modifier = Modifier.testTag("selector_occurred_at"),
                    )

                    if (uiState.type == MovementType.EXPENSE) {
                        val (merchantBg, merchantTint) = if (emeraldColors.isDark) {
                            Color(0xFF451A03) to Color(0xFFFDBA74)
                        } else {
                            Color(0xFFFEF3C7) to emeraldColors.warningText
                        }
                        val hasMerchant = uiState.merchantName.isNotBlank()

                        CalmEmeraldSelectionRow(
                            icon = Icons.Default.Storefront,
                            iconContainer = merchantBg,
                            iconTint = merchantTint,
                            label = "Comercio o pagador (opcional)",
                            value = if (hasMerchant) uiState.merchantName else "Agregar comercio",
                            supportingText = if (uiState.selectedMerchantId != null) "Comercio del catálogo" else "Puedes escribir un nombre libre",
                            onClick = onOpenMerchantPicker,
                            modifier = Modifier.testTag("input_merchant"),
                            trailingContent = if (hasMerchant) {
                                {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = onClearMerchant,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("btn_clear_merchant")
                                                .semantics { contentDescription = "Limpiar comercio" },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                tint = emeraldColors.secondaryMuted,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = onOpenMerchantPicker,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .semantics { contentDescription = "Editar comercio" },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = emeraldColors.secondaryMuted,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            if (uiState.selectedDestinationCardId == null) {
                // Collapsible Notes Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = uiState.isMoreDetailsExpanded,
                            role = Role.Button,
                            onValueChange = { onToggleMoreDetails() },
                        )
                        .semantics {
                            stateDescription = if (uiState.isMoreDetailsExpanded) "Expandido" else "Contraído"
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (uiState.type == MovementType.TRANSFER) "+ Nota o motivo (opcional)" else "+ Nota (opcional)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = emeraldColors.primaryDeep,
                    )
                    Icon(
                        imageVector = if (uiState.isMoreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = emeraldColors.primaryDeep,
                        modifier = Modifier.size(20.dp),
                    )
                }

                AnimatedVisibility(
                    visible = uiState.isMoreDetailsExpanded,
                    enter = fadeIn(tween(animDuration)) + expandVertically(tween(animDuration)),
                    exit = fadeOut(tween(animDuration)) + shrinkVertically(tween(animDuration)),
                ) {
                    OutlinedTextField(
                        value = uiState.note,
                        onValueChange = onNoteChanged,
                        placeholder = { Text(stringResource(R.string.movement_note_placeholder), color = emeraldColors.secondaryMuted) },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = emeraldColors.primaryDeep,
                            unfocusedBorderColor = emeraldColors.borderSubtle,
                            focusedContainerColor = emeraldColors.surfaceCard,
                            unfocusedContainerColor = emeraldColors.surfaceCard,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_note"),
                    )
                }
            } else {
                Text("El pago de tarjeta no admite notas.", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sticky Pinned Footer Save Button
        val saveButtonText = when (uiState.type) {
            MovementType.EXPENSE -> "Guardar gasto"
            MovementType.INCOME -> "Guardar ingreso"
            MovementType.TRANSFER -> if (uiState.selectedDestinationCardId != null) "Confirmar pago de tarjeta" else "Confirmar transferencia"
        }

        if (hasFieldErrors) {
            Button(
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = if (emeraldColors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    disabledContentColor = emeraldColors.secondaryMuted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_save_transaction"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Corregir datos para guardar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = emeraldColors.primaryDeep,
                    contentColor = emeraldColors.onPrimaryDeep,
                    disabledContainerColor = emeraldColors.primaryDeep.copy(alpha = 0.5f),
                    disabledContentColor = emeraldColors.onPrimaryDeep.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_save_transaction")
                    .semantics {
                        contentDescription = if (uiState.isSaving) "Guardando transacción" else (if (hasGeneralError) "Reintentar guardar" else saveButtonText)
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = emeraldColors.onPrimaryDeep,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.movement_saving),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    } else {
                        Text(
                            text = if (hasGeneralError) "Reintentar guardar" else saveButtonText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        PastelCategoryPickerBottomSheet(
            categories = uiState.availableCategories,
            selectedCategoryId = uiState.selectedCategoryId,
            onSelect = { category ->
                onCategorySelected(category)
                showCategoryPicker = false
            },
            onCreateCategory = onCreateSubcategory,
            onDismiss = { showCategoryPicker = false },
            isCreating = isCreatingSubcategory,
            errorMessage = categoryCreationError,
            directSelectionMode = true,
            title = "Elegir categoría",
            subtitle = if (uiState.type == MovementType.INCOME)
                "Selecciona una categoría o subcategoría para clasificar tu ingreso"
            else "Selecciona una categoría o subcategoría para clasificar tu gasto",
        )
    }

    if (showSourceAccountPicker) {
        val sourceAccounts = uiState.availableAccounts.filter { account ->
            sourceAccountPickerCurrency == null || account.currency.name == sourceAccountPickerCurrency
        }
        val sourceCards = uiState.availableCreditCards.filter { card ->
            sourceAccountPickerCurrency == null || card.currency.name == sourceAccountPickerCurrency
        }
        AccountSelectorBottomSheet(
            onDismissRequest = {
                showSourceAccountPicker = false
                sourceAccountPickerCurrency = null
            },
            accounts = sourceAccounts,
            creditCards = sourceCards,
            selectedAccountId = uiState.selectedSourceAccountId?.takeIf { selectedId ->
                sourceAccounts.any { it.id.value == selectedId }
            },
            selectedCardId = uiState.selectedSourceCardId?.takeIf { selectedId ->
                sourceCards.any { it.id.value == selectedId }
            },
            purpose = when (uiState.type) {
                MovementType.EXPENSE -> AccountSelectorPurpose.EXPENSE_SOURCE
                MovementType.INCOME -> AccountSelectorPurpose.INCOME_DESTINATION
                MovementType.TRANSFER -> AccountSelectorPurpose.TRANSFER_SOURCE
            },
            balancesLoaded = uiState.balancesLoaded,
            allowCreditCards = uiState.type == MovementType.EXPENSE,
            accountBalances = accountBalances,
            cardDebts = creditCardDebts,
            cardAvailableCredits = creditCardAvailableCredits,
            mostUsedAccountId = mostUsedAccountId,
            title = if (uiState.type == MovementType.INCOME) "Seleccionar cuenta destino" else "Seleccionar cuenta",
            onAccountIdSelected = onSourceAccountSelected,
            onCardIdSelected = onSourceCardSelected,
            subtitle = sourceAccountPickerCurrency?.let { "Elige un instrumento en $it" }
                ?: if (uiState.type == MovementType.INCOME) "Elige dónde recibir el ingreso" else "Elige de dónde sale el movimiento",
            onNewAccountClick = {
                showSourceAccountPicker = false
                sourceAccountPickerCurrency = null
                onNavigateToNewAccount()
            },
        )
    }

    if (showDestinationAccountPicker) {
        val eligibleDestinationAccounts = uiState.availableAccounts.filter { account ->
            account.id.value != uiState.selectedSourceAccountId && account.currency.name == uiState.currency
        }
        AccountSelectorBottomSheet(
            onDismissRequest = { showDestinationAccountPicker = false },
            accounts = eligibleDestinationAccounts,
            selectedAccountId = uiState.selectedDestinationAccountId,
            creditCards = uiState.availableCreditCards.filter { it.currency.name == uiState.currency },
            selectedCardId = uiState.selectedDestinationCardId,
            onCardIdSelected = onDestinationCardSelected,
            purpose = AccountSelectorPurpose.TRANSFER_DESTINATION,
            allowCreditCards = true,
            balancesLoaded = uiState.balancesLoaded,
            cardDebts = creditCardDebts,
            cardAvailableCredits = creditCardAvailableCredits,
            accountBalances = accountBalances,
            title = stringResource(R.string.movement_destination_account),
            subtitle = "Elige una cuenta o una tarjeta para pagar su deuda",
            onAccountIdSelected = onDestinationAccountSelected,
            onNewAccountClick = {
                showDestinationAccountPicker = false
                onNavigateToNewAccount()
            },
        )
    }
}

@Composable
private fun CalmEmeraldTypeTab(
    type: MovementType,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val animDuration = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis

    val (bg, text, border) = when (type) {
        MovementType.EXPENSE -> Triple(emeraldColors.expenseBg, emeraldColors.expenseText, emeraldColors.expenseBorder)
        MovementType.INCOME -> Triple(emeraldColors.incomeBg, emeraldColors.incomeText, emeraldColors.incomeBorder)
        MovementType.TRANSFER -> Triple(emeraldColors.transferBg, emeraldColors.transferBlue, emeraldColors.transferBorder)
    }
    val unselectedBg = emeraldColors.pillTrack
    val unselectedBorder = emeraldColors.borderSubtle
    val unselectedText = emeraldColors.secondaryMuted

    val animatedBg by animateColorAsState(
        targetValue = if (selected) bg else unselectedBg,
        animationSpec = tween(durationMillis = animDuration),
        label = "tabBg",
    )
    val animatedText by animateColorAsState(
        targetValue = if (selected) text else unselectedText,
        animationSpec = tween(durationMillis = animDuration),
        label = "tabText",
    )
    val icon = when (type) {
        MovementType.EXPENSE -> Icons.Default.ArrowUpward
        MovementType.INCOME -> Icons.Default.ArrowDownward
        MovementType.TRANSFER -> Icons.Default.SwapHoriz
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = animatedBg,
        border = if (selected) BorderStroke(1.5.dp, border) else BorderStroke(1.dp, unselectedBorder),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Tab,
                onClick = onSelect,
            )
            .semantics { this.selected = selected }
            .testTag("tab_${type.name.lowercase()}"),
    ) {
        if (LocalDensity.current.fontScale > 1.15f) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).heightIn(min = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = animatedText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp), color = animatedText, textAlign = TextAlign.Center)
            }
        } else {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = animatedText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    color = animatedText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CurrencyCapsuleTab(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val bgColor = when {
        selected -> emeraldColors.surfaceCard
        else -> Color.Transparent
    }
    val textColor = when {
        selected -> emeraldColors.primaryText
        else -> emeraldColors.secondaryMuted
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier
            .clickable(onClick = onSelect)
            .heightIn(min = 48.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = textColor,
            )
        }
    }
}

@Composable
private fun CalmEmeraldSelectionRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    label: String,
    value: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingActionText: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    brandName: String? = null,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val borderColor = if (isError) emeraldColors.expenseText else emeraldColors.borderSubtle

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = "$label. $value. $supportingText"
                role = Role.Button
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (brandName != null) BrandLogo(KipuBrand.from(brandName), brandName, null, size = 38.dp)
                    else Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isError) emeraldColors.expenseText else emeraldColors.secondaryMuted,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = emeraldColors.primaryText,
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isError) emeraldColors.expenseText else emeraldColors.secondaryMuted,
                    )
                }

                if (trailingContent != null) {
                    trailingContent()
                } else if (trailingActionText != null && onTrailingActionClick != null) {
                    TextButton(
                        onClick = onTrailingActionClick,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = trailingActionText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.expenseText,
                        )
                    }
                } else {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = emeraldColors.secondaryMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (isError && errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "▲ $errorMessage",
                    style = MaterialTheme.typography.labelSmall,
                    color = emeraldColors.expenseText,
                )
            }
        }
    }
}

@Composable
private fun TransferAccountRow(
    label: String,
    value: String,
    supportingText: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brandName: String? = null,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            if (brandName != null) BrandLogo(KipuBrand.from(brandName), brandName, null, size = 36.dp)
            else Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = emeraldColors.secondaryMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = emeraldColors.primaryText,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = emeraldColors.secondaryMuted,
            )
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = emeraldColors.secondaryMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun categoryPastelColors(categoryName: String): Pair<Color, Color> {
    val name = categoryName.lowercase(Locale.ROOT)
    val dark = rememberCalmEmeraldColors().isDark
    return when {
        "aliment" in name || "mercado" in name || "comida" in name || "restauran" in name ->
            if (dark) Color(0xFF7C2D12) to Color(0xFFFDBA74) else Color(0xFFFFEDD5) to Color(0xFF9A3412)
        "suscrip" in name || "stream" in name || "entreten" in name ->
            if (dark) Color(0xFF4C1D95) to Color(0xFFC4B5FD) else Color(0xFFEDE9FE) to Color(0xFF5B21B6)
        "transp" in name || "movil" in name || "auto" in name || "gas" in name ->
            if (dark) Color(0xFF0C4A6E) to Color(0xFF7DD3FC) else Color(0xFFE0F2FE) to Color(0xFF075985)
        "salud" in name || "cuidado" in name || "medic" in name || "farmac" in name ->
            if (dark) Color(0xFF881337) to Color(0xFFFDA4AF) else Color(0xFFFFE4E6) to Color(0xFF9F1239)
        "hogar" in name || "servicio" in name || "casa" in name || "luz" in name || "agua" in name ->
            if (dark) Color(0xFF78350F) to Color(0xFFFCD34D) else Color(0xFFFEF3C7) to Color(0xFF92400E)
        "educ" in name || "libro" in name || "curso" in name ->
            if (dark) Color(0xFF064E3B) to Color(0xFF6EE7B7) else Color(0xFFD1FAE5) to Color(0xFF065F46)
        "compr" in name || "ropa" in name || "tienda" in name ->
            if (dark) Color(0xFF831843) to Color(0xFFF472B6) else Color(0xFFFCE7F3) to Color(0xFF9D174D)
        else ->
            if (dark) Color(0xFF1E293B) to Color(0xFF94A3B8) else Color(0xFFF1F5F9) to Color(0xFF475569)
    }
}

private fun formatMoneyInline(money: Money): String {
    val currency = when (money.currency.name) {
        "PEN" -> "S/"
        "USD" -> "$"
        else -> money.currency.name
    }
    return "$currency ${formatMinorUnits(money.minorUnits)}"
}

private fun formatTransactionDate(timestamp: Long): String {
    val date = Date(timestamp)
    val locale = Locale("es", "PE")
    val dateCalendar = java.util.Calendar.getInstance().apply { time = date }
    val nowCalendar = java.util.Calendar.getInstance()
    val isToday = dateCalendar.get(java.util.Calendar.YEAR) == nowCalendar.get(java.util.Calendar.YEAR) &&
        dateCalendar.get(java.util.Calendar.DAY_OF_YEAR) == nowCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    val timeText = SimpleDateFormat("hh:mm a", Locale.US).format(date)
    return if (isToday) {
        "Hoy · $timeText"
    } else {
        val dateText = SimpleDateFormat("d MMM yyyy", locale).format(date)
        "$dateText · $timeText"
    }
}

private fun accountTypeLabel(type: com.kipu.app.feature.accounts.domain.model.AccountType): String = when (type) {
    com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS -> "Ahorros"
    com.kipu.app.feature.accounts.domain.model.AccountType.BANK -> "Cuenta bancaria"
    com.kipu.app.feature.accounts.domain.model.AccountType.DIGITAL_WALLET -> "Billetera"
    com.kipu.app.feature.accounts.domain.model.AccountType.CASH -> "Efectivo"
    com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

/** M28: a bottom sheet over the register sheet, so the form stays visible behind the scrim. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    existing: com.kipu.app.feature.movements.domain.model.Transaction? = null,
    proposed: com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand? = null,
    accountAlias: (String?) -> String? = { null },
    proposedMerchant: String? = null,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = emeraldColors.background,
        modifier = modifier.testTag("dialog_duplicate_warning"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(emeraldColors.warningBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = emeraldColors.warningText, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = "¿Es una compra distinta?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = emeraldColors.primaryText,
                )
            }
            Text(
                "Ya registraste un gasto parecido hace poco. Dos compras reales iguales son posibles; solo queremos evitar descontarlo dos veces.",
                style = MaterialTheme.typography.bodyMedium,
                color = emeraldColors.secondaryMuted,
            )
            existing?.let {
                DuplicateComparisonCard(
                    label = "YA REGISTRADO · ${formatTransactionDate(it.occurredAt).uppercase(Locale("es", "PE"))}",
                    title = listOfNotNull(it.merchantProvisionalText?.takeIf(String::isNotBlank), accountAlias(it.sourceAccountId))
                        .joinToString(" · ").ifBlank { "Gasto" },
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    highlighted = true,
                )
            }
            proposed?.let {
                DuplicateComparisonCard(
                    label = "LO QUE VAS A GUARDAR",
                    title = listOfNotNull(
                        (it.merchantProvisionalText ?: proposedMerchant)?.takeIf(String::isNotBlank),
                        accountAlias(it.sourceAccountId),
                    ).joinToString(" · ").ifBlank { "Gasto" },
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    highlighted = false,
                )
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = emeraldColors.primaryDeep,
                    contentColor = emeraldColors.onPrimaryDeep,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("btn_confirm_duplicate"),
            ) {
                Text("Sí, es otra compra · guardar", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = emeraldColors.surfaceCard,
                    contentColor = emeraldColors.primaryText,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("btn_cancel_duplicate"),
            ) {
                Text("No, volver sin guardar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DuplicateComparisonCard(
    label: String,
    title: String,
    amountMinor: Long,
    currency: String,
    highlighted: Boolean,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(16.dp)
    val dashColor = emeraldColors.borderSubtle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(emeraldColors.surfaceCard)
            .then(
                if (highlighted) {
                    Modifier.border(BorderStroke(1.dp, emeraldColors.warningBorder), shape)
                } else {
                    Modifier.drawBehind {
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            color = dashColor,
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                            ),
                        )
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                color = if (highlighted) emeraldColors.warningText else emeraldColors.secondaryMuted,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = emeraldColors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(
            amount = "−${formatMinorUnits(amountMinor)}",
            currencySymbol = if (currency == "USD") "$" else "S/",
            color = emeraldColors.expenseText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
internal fun SavingMovementDialog() {
    val emeraldColors = rememberCalmEmeraldColors()
    AlertDialog(
        onDismissRequest = {},
        icon = {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Guardando movimiento" },
                color = emeraldColors.primaryDeep,
                strokeWidth = 2.5.dp,
            )
        },
        title = {
            Text(
                text = "Guardando movimiento",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "Estamos guardando el movimiento y preparando su sincronización.",
                style = MaterialTheme.typography.bodyMedium,
                color = emeraldColors.secondaryMuted,
            )
        },
        confirmButton = {},
        containerColor = emeraldColors.surfaceCard,
        modifier = Modifier.testTag("dialog_saving_movement"),
    )
}

private fun toUtcDateMillis(occurredAt: Long): Long {
    val zone = ZoneId.systemDefault()
    val localDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun datePickerMillisToLocalInstant(selectedDateMillis: Long, occurredAt: Long): Long {
    val zone = ZoneId.systemDefault()
    val selectedDate = Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val existingLocalTime = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
    return selectedDate.atTime(existingLocalTime).atZone(zone).toInstant().toEpochMilli()
}

@Composable
internal fun rememberNetworkOffline(): Boolean {
    val context = LocalContext.current
    val connectivity = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun connected(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    var offline by remember { mutableStateOf(!connected()) }
    DisposableEffect(connectivity) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { offline = !connected() }
            override fun onLost(network: Network) { offline = !connected() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { offline = !connected() }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }
    return offline
}
