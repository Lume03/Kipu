package com.kipu.app.feature.movements.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.feature.categories.presentation.components.MerchantAvatar
import com.kipu.app.feature.categories.presentation.components.MovementCategoryIconAvatar
import com.kipu.app.feature.categories.presentation.components.getMerchantVisualProfile
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.feature.movements.domain.model.WalletPaymentChannel
import com.kipu.app.feature.movements.domain.isRefundEligible
import com.kipu.app.feature.recurrences.presentation.RecurrenceLifecycleScreen
import com.kipu.app.feature.recurrences.presentation.RecurrencePresentationViewModel
import com.kipu.app.feature.recurrences.presentation.RecurrencePresentationState
import com.kipu.app.feature.recurrences.presentation.RecurrenceRegistrationScreen
import com.kipu.app.ui.component.KipuEmptyState
import com.kipu.app.ui.component.KipuErrorState
import com.kipu.app.ui.component.KipuOfflineBanner
import com.kipu.app.ui.component.KipuPendingSyncTag
import com.kipu.app.ui.component.KipuSkeletonBlock
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MovementAvatarPresentation
import com.kipu.app.ui.component.loadingSemantics
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.movementAvatarPresentation
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementHistoryRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNewAccount: () -> Unit = {},
    onNavigateToPlans: () -> Unit = {},
    onNavigateToEditor: (String) -> Unit = {},
    viewModel: MovementHistoryViewModel = hiltViewModel(),
    recurrenceViewModel: RecurrencePresentationViewModel? = null,
    prewarmQuickMovement: Boolean = false,
    modifier: Modifier = Modifier,
    savedMessage: Boolean = false,
    onSavedMessageConsumed: () -> Unit = {},
    openRegisterMovement: Boolean = false,
    onConsumeRegisterMovement: () -> Unit = {},
    onDetailDismissed: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recurrenceState by recurrenceViewModel?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(RecurrencePresentationState()) }
    val voidBalances by viewModel.voidBalanceState.collectAsStateWithLifecycle()
    val quickMovementViewModel = if (prewarmQuickMovement) hiltViewModel<QuickMovementViewModel>() else null
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.history_saved_pending)
    val voidedText = stringResource(R.string.history_voided_pending)
    val settingsContentDescription = stringResource(R.string.settings_access_content_description)
    val emeraldColors = rememberCalmEmeraldColors()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val receiptPreferences = remember(context) {
        context.getSharedPreferences("movement_receipts_v1", Context.MODE_PRIVATE)
    }
    val detailItem = uiState.selectedDetail
    var recurrenceSource by remember { mutableStateOf<TransactionItem?>(null) }
    var showRecurrenceList by remember { mutableStateOf(false) }
    var receiptUriText by remember(detailItem?.transaction?.userId, detailItem?.transaction?.id) {
        mutableStateOf(detailItem?.let { row ->
            receiptPreferences.getString(movementReceiptPreferenceKey(row), null)
        })
    }
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val row = detailItem
        if (uri != null && row != null) {
            val isPdf = runCatching { context.contentResolver.getType(uri)?.substringBefore(';') }
                .getOrNull()
                ?.let { it == "application/pdf" } ?: true
            val persisted = isPdf && runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (persisted) {
                receiptPreferences.edit()
                    .putString(movementReceiptPreferenceKey(row), uri.toString())
                    .apply()
                receiptUriText = uri.toString()
                scope.launch { snackbarHostState.showSnackbar("Comprobante PDF guardado en este dispositivo") }
            } else {
                scope.launch { snackbarHostState.showSnackbar("No se pudo guardar el acceso al PDF. Elige otro archivo.") }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 6
        }.collect { nearEnd ->
            if (nearEnd) viewModel.onLoadMore()
        }
    }

    LaunchedEffect(savedMessage) {
        if (savedMessage) {
            onSavedMessageConsumed()
            snackbarHostState.showSnackbar(savedText)
        }
    }

    // Respond to root navigation register movement trigger
    LaunchedEffect(openRegisterMovement) {
        if (openRegisterMovement) {
            viewModel.onOpenRegisterSheet()
            onConsumeRegisterMovement()
        }
    }

    val mostUsedAccountId = uiState.mostUsedAccountId

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.movements_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp,
                            ),
                            color = emeraldColors.primaryText,
                        )

                        // Period context pill
                        val periodLabel = remember(uiState.fromDate, uiState.toDate) {
                            if (uiState.fromDate != null && uiState.toDate != null) {
                                val fmt = SimpleDateFormat("MMM", Locale("es", "PE"))
                                val fromStr = fmt.format(Date(uiState.fromDate!!)).replaceFirstChar { it.uppercase() }
                                val toStr = fmt.format(Date(uiState.toDate!!)).replaceFirstChar { it.uppercase() }
                                if (fromStr.equals(toStr, ignoreCase = true)) fromStr else "$fromStr - $toStr"
                            } else if (uiState.fromDate != null) {
                                SimpleDateFormat("MMM yyyy", Locale("es", "PE")).format(Date(uiState.fromDate!!)).replaceFirstChar { it.uppercase() }
                            } else {
                                "Todos"
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = emeraldColors.surfaceCard,
                            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = periodLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = settingsContentDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = emeraldColors.background),
            )
        },
        containerColor = emeraldColors.background,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("list_movements"),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "history_controls") {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Search Bar
                    // Estado local del campo: conserva el cursor mientras el ViewModel publica el texto de forma asíncrona
                    var searchField by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(uiState.searchQuery)) }
                    LaunchedEffect(uiState.searchQuery) {
                        val published = uiState.searchQuery
                        val external = if (published.isEmpty()) searchField.text.isNotEmpty() else !isTypingSearch(searchField, published)
                        if (external) searchField = androidx.compose.ui.text.input.TextFieldValue(published)
                    }
                    OutlinedTextField(
                        value = searchField,
                        onValueChange = {
                            searchField = it
                            viewModel.onSearchQueryChanged(it.text)
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.movements_search_placeholder),
                                color = emeraldColors.secondaryMuted,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = emeraldColors.secondaryMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChanged("") },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("btn_clear_search")
                                            .semantics { contentDescription = "Limpiar búsqueda" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = emeraldColors.secondaryMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.onToggleAdvancedFilterPanel(true) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_open_filters")
                                        .semantics { contentDescription = "Abrir filtros" }
                                ) {
                                    Icon(
                                        Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        tint = if (uiState.hasActiveAdvancedFilters) emeraldColors.primaryDeep else emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = emeraldColors.surfaceCard,
                            unfocusedContainerColor = emeraldColors.surfaceCard,
                            focusedBorderColor = emeraldColors.primaryDeep,
                            unfocusedBorderColor = emeraldColors.borderSubtle,
                            cursorColor = emeraldColors.primaryDeep,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("input_search_movements"),
                    )

                    // Pendientes de sincronizar (datos ya cargados en la lista; sin detector de red)
                    val pendingSyncCount = uiState.filteredTransactions.values.sumOf { rows ->
                        rows.count { it.transaction.syncStatus == MovementSyncStatus.PENDING || it.transaction.syncStatus == MovementSyncStatus.IN_FLIGHT }
                    }
                    if (pendingSyncCount > 0) {
                        KipuOfflineBanner(
                            title = "Sincronización pendiente.",
                            message = if (pendingSyncCount == 1) {
                                "1 movimiento pendiente de sincronizar; tus saldos ya están actualizados."
                            } else {
                                "$pendingSyncCount movimientos pendientes de sincronizar; tus saldos ya están actualizados."
                            },
                            modifier = Modifier.testTag("banner_pending_sync"),
                        )
                    }

                    // Summary Card for Filtered Query (pre-computed in ViewModel)
                    val totalMovCount = uiState.totalFilteredCount
                    val netByCurrency = uiState.netByCurrency

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "BALANCE NETO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            fontSize = 11.sp,
                                        ),
                                        color = emeraldColors.secondaryMuted,
                                    )
                                    Text(
                                        text = "Resultados filtrados",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                        ),
                                        color = emeraldColors.secondaryMuted,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = emeraldColors.pillTrack,
                                ) {
                                    Text(
                                        text = if (totalMovCount == 1L) "1 mov" else "$totalMovCount movs",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                        ),
                                        color = emeraldColors.secondaryMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val currenciesToShow = remember(netByCurrency) {
                                if (netByCurrency.isEmpty()) listOf("PEN")
                                else {
                                    val list = mutableListOf<String>()
                                    if (netByCurrency.containsKey("PEN")) list.add("PEN")
                                    if (netByCurrency.containsKey("USD")) list.add("USD")
                                    netByCurrency.keys.filter { it != "PEN" && it != "USD" }.forEach { list.add(it) }
                                    list
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                currenciesToShow.forEach { currency ->
                                    val netMinor = netByCurrency[currency] ?: BigInteger.ZERO
                                    val (prefix, formattedAmount) = formatSignedBigIntegerMinor(netMinor)
                                    val symbol = when (currency) {
                                        "PEN" -> "S/"
                                        "USD" -> "$"
                                        else -> currency
                                    }
                                    val netColor = when {
                                        netMinor > BigInteger.ZERO -> emeraldColors.incomeText
                                        netMinor < BigInteger.ZERO -> emeraldColors.expenseText
                                        else -> emeraldColors.primaryText
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (currenciesToShow.size > 1) {
                                            Text(
                                                text = currency,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                ),
                                                color = emeraldColors.secondaryMuted,
                                            )
                                        }
                                        MoneyText(
                                            amount = "$prefix$formattedAmount",
                                            currencySymbol = symbol,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (currenciesToShow.size > 1) 20.sp else 24.sp,
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = netColor,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // System / Access cards
                    MovementAccessCard(
                        uiState,
                        onNavigateToPlans,
                        viewModel::onRevalidateAccess,
                        viewModel::onDismissRecovery,
                        Modifier.fillMaxWidth(),
                    )
                    MovementAppliedFilters(
                        uiState,
                        viewModel::onRemoveFilter,
                        Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                uiState.isLoading -> item(key = "loading") {
                    // Skeleton de la lista (sustituye al spinner): mismas filas que el contenido real
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .loadingSemantics("Cargando movimientos"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(4) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    KipuSkeletonBlock(Modifier.size(40.dp), height = 40.dp, cornerRadius = 12.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        KipuSkeletonBlock(Modifier.fillMaxWidth(0.5f), height = 14.dp)
                                        KipuSkeletonBlock(Modifier.fillMaxWidth(0.3f), height = 10.dp)
                                    }
                                    KipuSkeletonBlock(Modifier.width(64.dp), height = 14.dp)
                                }
                            }
                        }
                    }
                }

                uiState.queryError -> item(key = "read_error") {
                    KipuErrorState(
                        title = stringResource(R.string.history_query_error),
                        message = stringResource(R.string.history_query_error_body),
                        onRetry = viewModel::onRetryHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        retryLabel = stringResource(R.string.history_retry),
                    )
                }

                uiState.filteredTransactions.isEmpty() -> item(key = "empty") {
                    val isSearch = uiState.searchQuery.isNotBlank()
                    val hasFilters = uiState.hasActiveAdvancedFilters || uiState.selectedFilterType != null
                    CalmEmeraldEmptyMovementsState(
                        isSearchActive = isSearch,
                        hasActiveFilters = hasFilters,
                        onClearSearch = { viewModel.onSearchQueryChanged("") },
                        onClearFilters = viewModel::onClearFilters,
                        onRegister = viewModel::onOpenRegisterSheet,
                        searchQuery = uiState.searchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }

                else -> {
                    uiState.filteredTransactions.forEach { (dateHeader, rows) ->
                        item(key = "header_$dateHeader") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = emeraldColors.primaryText,
                                )
                                Text(
                                    text = if (uiState.hasMorePages) "${rows.size} cargados"
                                        else if (rows.size == 1) "1 movimiento" else "${rows.size} movimientos",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                        }

                        itemsIndexed(
                            items = rows,
                            key = { _, item -> "movement_${item.transaction.id}" },
                            contentType = { _, _ -> "movement" },
                        ) { index, item ->
                            val first = index == 0
                            val last = index == rows.lastIndex
                            Card(
                                shape = RoundedCornerShape(
                                    topStart = if (first) 16.dp else 0.dp,
                                    topEnd = if (first) 16.dp else 0.dp,
                                    bottomStart = if (last) 16.dp else 0.dp,
                                    bottomEnd = if (last) 16.dp else 0.dp,
                                ),
                                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = if (last) 10.dp else 0.dp),
                            ) {
                                TransactionRow(
                                    item = item,
                                    onClick = { viewModel.onOpenDetail(item) },
                                    onEditClick = { onNavigateToEditor(item.transaction.id) },
                                    onVoidClick = { viewModel.onSelectTransactionForVoid(item) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoadingNextPage) {
                item(key = "loading_next_page") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = emeraldColors.primaryDeep,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else if (uiState.loadMoreError != null) {
                item(key = "load_more_error") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        uiState.loadMoreError?.let { error ->
                            Text(error, color = emeraldColors.secondaryMuted)
                        }
                        OutlinedButton(onClick = viewModel::onLoadMore) {
                            Text(stringResource(R.string.history_retry))
                        }
                    }
                }
            } else if (!uiState.isLoading && uiState.filteredTransactions.isNotEmpty() && !uiState.hasMorePages) {
                item(key = "end_of_history") {
                    Text(
                        text = "Fin del historial",
                        color = emeraldColors.secondaryMuted,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }

            // Bottom space for floating nav bar
            item(key = "bottom_space") {
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (uiState.showRegisterSheet) {
        QuickMovementBottomSheet(
            onDismissRequest = viewModel::onCloseRegisterSheet,
            viewModel = quickMovementViewModel ?: hiltViewModel(),
            onNavigateToNewAccount = onNavigateToNewAccount,
            mostUsedAccountId = mostUsedAccountId,
            onSaved = { movementType ->
                if (movementType == MovementType.TRANSFER) viewModel.showAllTransactionsAfterTransfer()
            },
            onMessage = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }

    uiState.selectedDetail?.let { item ->
        MovementDetailSheet(
            item,
            uiState.detailRevisions,
            uiState.detailLoading,
            uiState.detailError,
            onDismiss = { viewModel.onCloseDetail(); onDetailDismissed?.invoke() },
            onEdit = { viewModel.onCloseDetail(); onNavigateToEditor(item.transaction.id) },
            onVoid = { viewModel.onCloseDetail(); viewModel.onSelectTransactionForVoid(item) },
            receiptAttached = !receiptUriText.isNullOrBlank(),
            onAttachReceipt = { receiptPicker.launch(arrayOf("application/pdf")) },
            onOpenReceipt = {
                val uri = receiptUriText?.let(Uri::parse)
                if (uri != null) {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Abrir comprobante"))
                    }.onFailure {
                        scope.launch { snackbarHostState.showSnackbar("No se pudo abrir el comprobante PDF") }
                    }
                }
            },
            onRemoveReceipt = {
                receiptPreferences.edit().remove(movementReceiptPreferenceKey(item)).apply()
                receiptUriText = null
            },
            onRegisterRecurrence = {
                recurrenceSource = item
                showRecurrenceList = false
                viewModel.onCloseDetail()
            },
            canRegisterRefund = item.transaction.isRefundEligible(),
            refundRemainingMinor = uiState.detailRefundRemainingMinor ?: 0L,
            refundRejectionReason = uiState.detailRefundRejection,
            onRegisterRefund = {
                viewModel.onCloseDetail()
                viewModel.onOpenRefund(item)
            },
            onVoidRejectedRefund = {
                viewModel.onVoidRejectedRefund(item) { _, message ->
                    if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    uiState.refundFlow.purchaseItem?.takeIf { !uiState.refundFlow.isLoading }?.let { purchase ->
        MovementRefundSheet(
            currency = purchase.transaction.currency,
            remainingMinor = uiState.refundFlow.remainingMinor,
            purchaseOccurredAt = purchase.transaction.occurredAt,
            isSubmitting = uiState.refundFlow.isSubmitting,
            errorMessage = uiState.refundFlow.errorMessage,
            onDismiss = viewModel::onCloseRefund,
            onSubmit = viewModel::onSubmitRefund,
        )
    }

    recurrenceViewModel?.let { recurrenceVm ->
    recurrenceSource?.let { source ->
        Dialog(onDismissRequest = { recurrenceSource = null; recurrenceVm.clearSavedRule() }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                RecurrenceRegistrationScreen(
                    source = source,
                    onDismiss = { recurrenceSource = null; recurrenceVm.clearSavedRule() },
                    onCreate = recurrenceVm::create,
                    isSaving = recurrenceState.isSaving,
                    saveError = recurrenceState.errorMessage,
                    savedRule = recurrenceState.savedRule,
                    onManageRecurrences = { showRecurrenceList = true },
                )
            }
        }
    }

    if (showRecurrenceList) {
        Dialog(onDismissRequest = { showRecurrenceList = false; recurrenceVm.dismissConfirmation() }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                RecurrenceLifecycleScreen(
                    recurrences = recurrenceState.recurrences,
                    isLoading = recurrenceState.isLoading,
                    errorMessage = recurrenceState.errorMessage,
                    onPause = recurrenceVm::pause,
                    onRequestResume = recurrenceVm::requestResume,
                    onCancel = recurrenceVm::cancel,
                    onConfirmResume = recurrenceVm::confirmResume,
                    onDismissConfirmation = recurrenceVm::dismissConfirmation,
                    resumeDate = recurrenceState.resumePreview?.firstFutureDueDate,
                    pendingOperation = recurrenceState.isSaving,
                    onBack = { showRecurrenceList = false; recurrenceVm.dismissConfirmation() },
                )
            }
        }
    }
    }

    if (uiState.selectedTransactionForVoid != null) {
        VoidMovementDialog(
            item = uiState.selectedTransactionForVoid!!,
            onDismissRequest = viewModel::onDismissVoidDialog,
            onConfirmVoid = { reason ->
                viewModel.onConfirmVoid(reason) { success, errorMsg ->
                    if (success) {
                        val msg = errorMsg ?: voidedText
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    } else if (errorMsg != null) {
                        scope.launch { snackbarHostState.showSnackbar(errorMsg) }
                    }
                }
            },
            isVoiding = uiState.isVoiding,
            errorMessage = uiState.voidErrorMessage,
            currentSourceBalanceMinor = voidBalances.sourceMinorUnits,
            currentDestinationBalanceMinor = voidBalances.destinationMinorUnits,
        )
    }

    if (uiState.showAdvancedFilterPanel) {
        val filterCatalogViewModel: MovementFiltersCatalogViewModel = hiltViewModel()
        val filterCatalog by filterCatalogViewModel.state.collectAsStateWithLifecycle()
        MovementFiltersSheet(
            uiState,
            onDismiss = { viewModel.onToggleAdvancedFilterPanel(false) },
            onApply = viewModel::onApplyFilterDraft,
            onVerify = viewModel::onRevalidateAccess,
            onViewPlans = onNavigateToPlans,
            onDismissRecovery = viewModel::onDismissRecovery,
            catalog = filterCatalog,
        )
    }
}

@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit = {},
    onVoidClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onEditClick: () -> Unit = onClick,
) {
    val tx = item.transaction
    val emeraldColors = rememberCalmEmeraldColors()
    val isVoided = tx.status == TransactionStatus.VOIDED
    var showMenu by remember { mutableStateOf(false) }

    val legacyTitle = when (tx.legacyKind) {
        "OPENING" -> "Saldo inicial"
        "ADJUSTMENT" -> "Ajuste de saldo"
        "REVERSAL" -> "Reversión de apertura"
        "CARD_PAYMENT_CASH" -> "Pago de tarjeta"
        else -> null
    }
    val title = when {
        tx.operationKind.equals("CARD_PAYMENT", ignoreCase = true) -> "Pago de tarjeta"
        legacyTitle != null -> legacyTitle
        else -> item.merchantName?.takeIf { it.isNotBlank() }
            ?: item.categoryName?.takeIf { it.isNotBlank() }
            ?: when (tx.type) {
                MovementType.EXPENSE -> "Gasto"
                MovementType.INCOME -> "Ingreso"
                MovementType.TRANSFER -> "Transferencia"
            }
    }

    val cardLabel = item.cardAlias ?: item.cardLastFourDigits?.let { "•••• $it" } ?: "Tarjeta"
    val accountSubtitle = when {
        tx.operationKind.equals("CARD_PURCHASE", ignoreCase = true) -> cardLabel
        tx.operationKind.equals("CARD_PAYMENT", ignoreCase = true) -> "${item.sourceAccountAlias ?: "Cuenta"} → $cardLabel"
        tx.type == MovementType.TRANSFER -> "${item.sourceAccountAlias ?: "Origen"} → ${item.destinationAccountAlias ?: "Destino"}"
        else -> item.sourceAccountAlias ?: "Cuenta"
    }
    val paymentChannel = WalletPaymentChannel.entries.firstOrNull { it.name == tx.paymentChannel }
    val subtitle = paymentChannel?.let { "${it.label} · $accountSubtitle" } ?: accountSubtitle

    val (iconBg, iconTint) = when {
        isVoided -> emeraldColors.pillTrack to emeraldColors.secondaryMuted
        tx.type == MovementType.INCOME -> emeraldColors.incomeBg to emeraldColors.incomeText
        tx.type == MovementType.EXPENSE -> emeraldColors.expenseBg to emeraldColors.expenseText
        tx.type == MovementType.TRANSFER -> emeraldColors.transferBg to emeraldColors.transferBlue
        else -> emeraldColors.pillTrack to emeraldColors.primaryDeep
    }

    val amountColor = if (isVoided) {
        emeraldColors.secondaryMuted
    } else when (tx.type) {
        MovementType.INCOME -> emeraldColors.incomeText
        MovementType.EXPENSE -> emeraldColors.expenseText
        MovementType.TRANSFER -> emeraldColors.transferBlue
    }

    val amountPrefix = when (tx.type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "−"
        MovementType.TRANSFER -> ""
    }

    val currencySymbol = if (tx.currency == "PEN") "S/" else "$"
    val formattedAmount = formatMinorUnits(tx.amountMinor)

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .testTag("tx_row_${tx.id}")
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            TransactionIconAvatar(
                item = item,
                isVoided = isVoided,
                iconBg = iconBg,
                iconTint = iconTint,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle + Badges
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        color = if (isVoided) emeraldColors.secondaryMuted else emeraldColors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isVoided) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = emeraldColors.expenseBg,
                            modifier = Modifier.testTag("tx_voided_badge_${tx.id}"),
                        ) {
                            Text(
                                text = stringResource(R.string.movement_void_status_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = emeraldColors.expenseText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (tx.syncStatus == MovementSyncStatus.PENDING || tx.syncStatus == MovementSyncStatus.IN_FLIGHT) {
                        KipuPendingSyncTag(modifier = Modifier.testTag("tx_pending_tag_${tx.id}"))
                    } else if (tx.syncStatus == MovementSyncStatus.CONFLICT) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = emeraldColors.warningBg,
                        ) {
                            Text(
                                text = "Requiere revisión",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = emeraldColors.warningText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = emeraldColors.secondaryMuted,
                        maxLines = 1,
                        modifier = if (paymentChannel != null) Modifier.testTag("tx_payment_channel_${tx.id}") else Modifier,
                    )
                    if (tx.syncStatus != MovementSyncStatus.PENDING && tx.syncStatus != MovementSyncStatus.IN_FLIGHT) {
                        SyncStatusIcon(status = tx.syncStatus)
                    }
                }
                if (isVoided) {
                    // Anulado = borrado lógico: el movimiento deja de contar en los totales
                    Text(
                        text = "Anulado · no cuenta en tus gastos",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = emeraldColors.secondaryMuted,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("tx_voided_note_${tx.id}"),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            MoneyText(
                amount = "$amountPrefix$formattedAmount",
                currencySymbol = currencySymbol,
                color = amountColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFeatureSettings = "tnum",
                    textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None,
                ),
                modifier = Modifier.testTag("tx_amount_${tx.id}"),
            )

            // Options menu (Editar / Anular)
            if (!isVoided) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tx_menu_${tx.id}")
                            .semantics { contentDescription = "Opciones del movimiento" }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.testTag("tx_dropdown_menu_${tx.id}")
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.movement_action_edit)) },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_edit_${tx.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.movement_action_void)) },
                            onClick = {
                                showMenu = false
                                onVoidClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_void_${tx.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusIcon(
    status: MovementSyncStatus,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    when (status) {
        MovementSyncStatus.SYNCED, MovementSyncStatus.MIGRATED_LOCAL -> {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = if (status == MovementSyncStatus.MIGRATED_LOCAL) {
                    "Movimiento histórico conservado localmente"
                } else {
                    stringResource(R.string.movements_sync_synced)
                },
                tint = emeraldColors.secondaryMuted,
                modifier = modifier.size(13.dp)
            )
        }
        MovementSyncStatus.PENDING, MovementSyncStatus.IN_FLIGHT -> {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = stringResource(R.string.movements_sync_pending),
                tint = emeraldColors.primaryDeep,
                modifier = modifier.size(13.dp)
            )
        }
        MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT -> {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.movements_sync_error),
                tint = emeraldColors.expenseText,
                modifier = modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun CalmEmeraldEmptyMovementsState(
    isSearchActive: Boolean = false,
    hasActiveFilters: Boolean = false,
    onClearSearch: (() -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
    onRegister: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val noResults = isSearchActive || hasActiveFilters

    val title = if (noResults) "Sin resultados" else "Aún no tienes movimientos"
    val message = when {
        isSearchActive && searchQuery.isNotBlank() ->
            "No encontramos movimientos con “${searchQuery.trim()}”. Revisa la ortografía o prueba con otro comercio o categoría."
        isSearchActive -> "No encontramos movimientos que coincidan con tu búsqueda. Revisa la ortografía o prueba con otro comercio o categoría."
        hasActiveFilters -> "Ningún movimiento coincide con los filtros aplicados. Prueba a cambiar o quitar algún filtro."
        else -> "Registra tu primer gasto, ingreso o transferencia y aparecerá aquí con tu saldo actualizado."
    }

    KipuEmptyState(
        title = title,
        message = message,
        modifier = modifier.fillMaxWidth().padding(top = 32.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = if (noResults) emeraldColors.pillTrack else emeraldColors.incomeBg,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (noResults) Icons.Outlined.SearchOff else Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = if (noResults) emeraldColors.secondaryMuted else emeraldColors.primaryDeep,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        },
        action = {
            val actionColors = ButtonDefaults.buttonColors(
                containerColor = emeraldColors.primaryDeep,
                contentColor = emeraldColors.onPrimaryDeep,
            )
            if (!isSearchActive && !hasActiveFilters && onRegister != null) {
                Button(
                    onClick = onRegister,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_empty_register"),
                    colors = actionColors,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Registrar movimiento", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            } else if (isSearchActive && onClearSearch != null) {
                Button(
                    onClick = onClearSearch,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_empty_clear_search")
                        .semantics { contentDescription = "Limpiar búsqueda" },
                    colors = actionColors,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Limpiar búsqueda", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            } else if (hasActiveFilters && onClearFilters != null) {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_empty_clear_filters")
                        .semantics { contentDescription = "Limpiar filtros" },
                    colors = actionColors,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Limpiar filtros", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        },
    )
}

private fun formatSignedBigIntegerMinor(minor: BigInteger): Pair<String, String> {
    val isNegative = minor < BigInteger.ZERO
    val absMinor = if (isNegative) minor.negate() else minor
    val prefix = if (isNegative) "− " else if (minor > BigInteger.ZERO) "+ " else ""
    val bd = BigDecimal(absMinor).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(bd)
    return prefix to formatted
}

private val merchantLogoCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

@Composable
fun TransactionIconAvatar(
    item: TransactionItem,
    isVoided: Boolean,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val tx = item.transaction
    val context = androidx.compose.ui.platform.LocalContext.current

    val drawableId = androidx.compose.runtime.remember(item.merchantLogoKey) {
        val key = item.merchantLogoKey
        if (key != null) {
            merchantLogoCache.getOrPut(key) {
                @Suppress("DiscouragedApi")
                context.resources.getIdentifier(key, "drawable", context.packageName)
            }
        } else 0
    }

    val remoteLogoUrl = item.merchantLogoUrl
    val categoryIcon = item.categoryIcon?.takeIf(String::isNotBlank)
    val avatarPresentation = movementAvatarPresentation(
        merchantAvatarAllowed = !isVoided,
        merchantName = item.merchantName,
        hasUsableMerchantLogo = drawableId != 0 || remoteLogoUrl != null,
        categoryIcon = categoryIcon,
    )

    when (avatarPresentation) {
        MovementAvatarPresentation.MERCHANT_AVATAR -> MerchantAvatar(
            drawableId = drawableId,
            remoteLogoUrl = remoteLogoUrl,
            profile = getMerchantVisualProfile(
                item.merchantName?.takeIf(String::isNotBlank) ?: "Comercio",
                item.merchantBrandColor,
            ),
            size = 40.dp,
            modifier = modifier,
            fallbackContent = categoryIcon?.let { iconKey ->
                {
                    MovementCategoryIconAvatar(
                        iconKey = iconKey,
                        background = iconBg,
                        tint = iconTint,
                        shape = CircleShape,
                        size = 40.dp,
                        iconSize = 20.dp,
                    )
                }
            },
        )
        MovementAvatarPresentation.MERCHANT_INITIALS -> MerchantAvatar(
            drawableId = 0,
            remoteLogoUrl = null,
            profile = getMerchantVisualProfile(item.merchantName.orEmpty(), item.merchantBrandColor),
            size = 40.dp,
            modifier = modifier,
        )
        MovementAvatarPresentation.CATEGORY_ICON -> MovementCategoryIconAvatar(
            iconKey = categoryIcon.orEmpty(),
            background = iconBg,
            tint = iconTint,
            shape = CircleShape,
            size = 40.dp,
            iconSize = 20.dp,
            modifier = modifier,
        )
        MovementAvatarPresentation.TYPE_ICON -> {
            val fallbackIcon = when (tx.type) {
                MovementType.INCOME -> androidx.compose.material.icons.Icons.Rounded.Check
                MovementType.EXPENSE -> androidx.compose.material.icons.Icons.Rounded.Close
                MovementType.TRANSFER -> androidx.compose.material.icons.Icons.Rounded.Sync
            }
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun movementReceiptPreferenceKey(item: TransactionItem): String =
    "${item.transaction.userId}:${item.transaction.id}"

/** El campo local va un paso por delante del ViewModel mientras se escribe; solo se resincroniza ante cambios externos. */
private fun isTypingSearch(field: androidx.compose.ui.text.input.TextFieldValue, published: String): Boolean =
    field.text.startsWith(published) || published.startsWith(field.text)
