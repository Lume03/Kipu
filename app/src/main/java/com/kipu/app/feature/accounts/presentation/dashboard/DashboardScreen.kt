package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card as DomainCard
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.DashboardUiState
import com.kipu.app.feature.accounts.presentation.FinancialTerminology
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import com.kipu.app.feature.home.presentation.NetWorthCard
import com.kipu.app.feature.home.presentation.NetWorthUiState
import com.kipu.app.feature.movements.presentation.QuickMovementBottomSheet
import com.kipu.app.feature.notifications.presentation.UnreadNotificationBadge
import com.kipu.app.ui.component.KipuErrorState
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AccountsViewModel,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    onAccountClick: (String) -> Unit = {},
    onCardClick: (String) -> Unit = onAccountClick,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    feedbackMessage: String? = null,
    onFeedbackConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigateToPlans: () -> Unit = {},
    onNavigateToMovements: () -> Unit = {},
    onToggleMasked: (() -> Unit)? = null,
    openRegisterMovement: Boolean = false,
    onConsumeRegisterMovement: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    netWorthState: NetWorthUiState? = null,
    onRetryNetWorth: () -> Unit = {},
    onNavigateToCalendar: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val instruments by viewModel.instrumentsUiState.collectAsStateWithLifecycle()
    val creditNotifications by viewModel.creditNotifications.collectAsStateWithLifecycle()
    var showQuotaSelection by remember { mutableStateOf(false) }
    var showRegisterMovementSheet by rememberSaveable { mutableStateOf(false) }
    var isArchivedExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val vmMasked by viewModel.isMasked.collectAsStateWithLifecycle()
    val isMasked = vmMasked || LocalBalanceMasked.current
    val emeraldColors = rememberCalmEmeraldColors()

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onFeedbackConsumed()
        }
    }
    LaunchedEffect(viewModel) { viewModel.refreshCreditUtilizationNotifications() }

    // Respond to root navigation register movement trigger
    LaunchedEffect(openRegisterMovement) {
        if (openRegisterMovement) {
            showRegisterMovementSheet = true
            onConsumeRegisterMovement()
        }
    }

    val toggleMasked: () -> Unit = { onToggleMasked?.invoke() ?: viewModel.toggleMasked() }

    CompositionLocalProvider(LocalBalanceMasked provides isMasked) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                DashboardTopBar(
                    unreadNotificationCount = unreadNotificationCount,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToSettings = onNavigateToSettings,
                )
            },
            containerColor = emeraldColors.background,
            modifier = modifier,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    state.isLoading || instruments.isLoading -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState(), enabled = false)
                            .padding(horizontal = 16.dp),
                    ) {
                        DashboardLoadingSkeleton()
                    }
                    // H8: nothing is shown as zero when the source failed; retry re-reads it.
                    state.errorMessage != null -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        KipuErrorState(
                            title = "No pudimos cargar tu dinero",
                            message = "Tus datos están a salvo en tu cuenta. Revisa tu conexión e inténtalo de nuevo. " +
                                "Mientras tanto no mostramos cifras para no confundirte con ceros.",
                            onRetry = {
                                viewModel.retryDashboard()
                                onRetryNetWorth()
                            },
                            modifier = Modifier.padding(top = 20.dp).testTag(DASHBOARD_ERROR_TAG),
                        )
                    }
                    else -> DashboardContent(
                        data = state.dashboardData,
                        instruments = instruments,
                        creditNotifications = creditNotifications,
                        isMasked = isMasked,
                        isFirstUse = shouldShowEmptyDashboard(state, instruments),
                        isArchivedExpanded = isArchivedExpanded,
                        onToggleArchived = { isArchivedExpanded = !isArchivedExpanded },
                        onToggleMasked = toggleMasked,
                        onAccountClick = onAccountClick,
                        onCardClick = onCardClick,
                        onManageQuota = { showQuotaSelection = true },
                        onNavigateToPlans = onNavigateToPlans,
                        onNavigateToNewAccount = onNavigateToNewAccount,
                        onNavigateToNewCard = onNavigateToNewCard,
                        netWorthState = netWorthState,
                        onRetryNetWorth = onRetryNetWorth,
                        onNavigateToCalendar = onNavigateToCalendar,
                    )
                }
            }
        }
    }

    if (showQuotaSelection) {
        val accounts = instruments.activeAccounts.filter { it.isComputableForQuota }
        val cards = instruments.activeCards.filterIsInstance<CreditCard>()
        InstrumentQuotaSelectionDialog(
            accounts = accounts,
            cards = cards,
            selectedIds = instruments.selectedFreeInstrumentIds,
            maxQuota = instruments.maxFreeQuota,
            onDismiss = { showQuotaSelection = false },
            onSave = { ids ->
                viewModel.saveFreeInstrumentSelection(ids)
                showQuotaSelection = false
            },
        )
    }

    if (showRegisterMovementSheet) {
        QuickMovementBottomSheet(
            onDismissRequest = { showRegisterMovementSheet = false },
            onNavigateToNewAccount = onNavigateToNewAccount,
            onSaved = { _ ->
                showRegisterMovementSheet = false
            },
            onMessage = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    unreadNotificationCount: Int,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val settingsContentDescription = stringResource(R.string.settings_access_content_description)
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = emeraldColors.incomeBg,
                    border = BorderStroke(1.5.dp, emeraldColors.incomeBorder),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "MC",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                            color = emeraldColors.primaryDeep,
                        )
                    }
                }
                Column {
                    Text(
                        text = "Bienvenido",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = emeraldColors.secondaryMuted,
                    )
                    Text(
                        text = "Mi dinero",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp),
                        color = emeraldColors.primaryText,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            // White circular buttons; each keeps a 48dp touch target.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateToNotifications),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = emeraldColors.surfaceCard,
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        UnreadNotificationBadge(
                            unreadCount = unreadNotificationCount,
                            onClick = onNavigateToNotifications,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(48.dp).semantics { contentDescription = settingsContentDescription },
            ) {
                Surface(
                    shape = CircleShape,
                    color = emeraldColors.surfaceCard,
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = emeraldColors.primaryText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = emeraldColors.background),
    )
}

internal fun shouldShowEmptyDashboard(
    state: DashboardUiState,
    instruments: InstrumentsUiState,
): Boolean {
    val data = state.dashboardData
    return data != null && !state.isLoading && !instruments.isLoading && state.errorMessage == null &&
        data.liquidAccounts.isEmpty() &&
        data.creditCards.isEmpty() &&
        instruments.activeAccounts.isEmpty() &&
        instruments.activeCards.isEmpty() &&
        instruments.archivedAccounts.isEmpty() &&
        instruments.archivedCards.isEmpty()
}

/** H5: first use. Same layout as the dashboard, with an explicit first step instead of empty lists. */
@Composable
internal fun EmptyDashboardContent(
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    onNavigateToGoals: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigateToPlans: () -> Unit = {},
    onNavigateToCalendar: (() -> Unit)? = null,
) {
    DashboardContent(
        data = null,
        instruments = InstrumentsUiState(isLoading = false),
        creditNotifications = emptyList(),
        isMasked = LocalBalanceMasked.current,
        isFirstUse = true,
        isArchivedExpanded = false,
        onToggleArchived = {},
        onToggleMasked = {},
        onAccountClick = {},
        onCardClick = {},
        onManageQuota = {},
        onNavigateToPlans = onNavigateToPlans,
        onNavigateToNewAccount = onNavigateToNewAccount,
        onNavigateToNewCard = onNavigateToNewCard,
        modifier = modifier,
        onNavigateToCalendar = onNavigateToCalendar,
    )
}

/** H1–H6: hero, net worth, calendar, plan, accounts with their wallets, credit cards and archive. */
@Composable
private fun DashboardContent(
    data: FinancialDashboardData?,
    instruments: InstrumentsUiState,
    creditNotifications: List<CreditUtilizationNotification>,
    isMasked: Boolean,
    isFirstUse: Boolean,
    isArchivedExpanded: Boolean,
    onToggleArchived: () -> Unit,
    onToggleMasked: () -> Unit,
    onAccountClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onManageQuota: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    modifier: Modifier = Modifier,
    netWorthState: NetWorthUiState? = null,
    onRetryNetWorth: () -> Unit = {},
    onNavigateToCalendar: (() -> Unit)? = null,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val computableCount = data?.activeComputableCount ?: instruments.activeComputableCount
    val maxQuota = data?.maxFreeQuota ?: instruments.maxFreeQuota

    val liquidAccounts = data?.liquidAccounts.orEmpty()
    val activeCreditCards = data?.creditCards.orEmpty().filter { !it.card.isArchived }
    val groups = remember(liquidAccounts) { groupAccountsWithWallets(liquidAccounts) }
    val hasWallets = groups.any { it.wallets.isNotEmpty() }

    // Affiliated wallets share their account's balance, so they never add to these figures.
    val bankByCurrency = remember(liquidAccounts) {
        sumByCurrency(liquidAccounts) { it.account.type != AccountType.CASH && it.account.linkedAccountId == null }
    }
    val cashByCurrency = remember(liquidAccounts) {
        sumByCurrency(liquidAccounts) { it.account.type == AccountType.CASH }
    }
    val hasUsd = (data?.totalUsd != null && data.totalUsd.minorUnits != 0L) ||
        liquidAccounts.any { it.balance.currency == Currency.USD } ||
        instruments.activeAccounts.any { it.currency == Currency.USD }

    val archivedRows = remember(data, instruments) {
        val archivedWithDebt = data?.creditCards.orEmpty().filter { it.card.isArchived && it.debt.minorUnits > 0L }
        instruments.archivedAccounts.map { account ->
            val issuer = AccountPreset.normalizedFor(account.type, account.preset)
                ?.takeIf { it != AccountPreset.CASH && it != AccountPreset.GENERIC }?.defaultName
            ArchivedRow(
                id = account.id.value,
                title = account.alias,
                subtitle = listOfNotNull(issuer, account.type.dashboardLabel(), account.currency.name).joinToString(" · "),
                isCard = false,
            )
        } + archivedWithDebt.map { item ->
            ArchivedRow(
                id = item.card.id.value,
                title = item.card.alias ?: "${item.card.issuer} ${item.card.network}",
                subtitle = "${item.card.issuer} · ${item.card.network} •••• ${item.card.lastFourDigits}",
                debt = item.debt,
                isCard = true,
            )
        } + instruments.archivedCards.filter { card -> archivedWithDebt.none { it.card.id == card.id } }.map { card ->
            ArchivedRow(
                id = card.id.value,
                title = card.alias ?: "${card.issuer} ${card.network}",
                subtitle = "${card.issuer} · ${card.network} •••• ${card.lastFourDigits}",
                isCard = true,
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "dashboard_hero") {
            DashboardHero(
                totalPen = data?.totalPen ?: Money(0L, Currency.PEN),
                totalUsd = if (hasUsd) data?.totalUsd ?: Money(0L, Currency.USD) else null,
                bankByCurrency = bankByCurrency,
                cashByCurrency = cashByCurrency,
                isMasked = isMasked,
                onToggleMasked = onToggleMasked,
                modifier = if (isFirstUse) Modifier.testTag("dashboard_empty_balance") else Modifier,
            )
        }

        // HU-39: patrimonio sits under the hero; credit lines and reserves never add to it.
        if (netWorthState != null && !isFirstUse) {
            item(key = "net_worth_summary") {
                NetWorthCard(state = netWorthState, onRetry = onRetryNetWorth)
            }
        }
        if (onNavigateToCalendar != null) {
            item(key = "calendar_entry") { DashboardCalendarEntryCard(onClick = onNavigateToCalendar) }
        }

        // Quota reference is secondary to money; effective entitlement is validated on save.
        if (maxQuota > 0) {
            item(key = "quota_strip") {
                DashboardPlanCard(used = computableCount, max = maxQuota, onClick = onNavigateToPlans)
            }
        }

        if (creditNotifications.isNotEmpty()) {
            items(creditNotifications, key = { it.id }) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCardClick(notification.cardId) },
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.warningBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, emeraldColors.warningBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            notification.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = emeraldColors.warningText,
                        )
                        Text(notification.body, style = MaterialTheme.typography.bodySmall, color = emeraldColors.primaryText)
                    }
                }
            }
        }

        item(key = "accounts_section_header") {
            DashboardSectionHeader(
                title = "Cuentas y efectivo",
                count = accountsCountLabel(groups),
                addLabel = "+ Cuenta",
                onAdd = onNavigateToNewAccount,
                manageLabel = "Gestionar",
                onManage = onManageQuota,
                showManage = !isFirstUse,
            )
        }
        if (groups.isEmpty()) {
            item(key = "accounts_empty") { DashboardNoAccountsCard(onAddAccount = onNavigateToNewAccount) }
        } else {
            items(groups, key = { it.account.account.id.value }) { group ->
                DashboardAccountGroupCard(group = group, isMasked = isMasked, onAccountClick = onAccountClick)
            }
            if (hasWallets) {
                item(key = "wallets_footnote") { DashboardWalletFootnote() }
            }
        }

        item(key = "cards_section_header") {
            Column {
                DashboardSectionHeader(
                    title = "Tarjetas de crédito",
                    count = "${activeCreditCards.size} ${if (activeCreditCards.size == 1) "activa" else "activas"}",
                    addLabel = "+ Tarjeta",
                    onAdd = onNavigateToNewCard,
                    manageLabel = "Ver detalle",
                    onManage = { activeCreditCards.firstOrNull()?.let { onCardClick(it.card.id.value) } },
                    showManage = activeCreditCards.isNotEmpty(),
                    addTag = if (isFirstUse) "dashboard_empty_add_card" else null,
                )
                Text(
                    text = FinancialTerminology.AVAILABLE_CREDIT_NOT_OWN_MONEY,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = emeraldColors.secondaryMuted,
                )
            }
        }
        if (activeCreditCards.isEmpty()) {
            item(key = "cards_empty") { DashboardNoCardsNotice() }
        } else {
            items(activeCreditCards, key = { it.card.id.value }) { creditItem ->
                DashboardCreditCardRow(item = creditItem, onClick = { onCardClick(creditItem.card.id.value) })
            }
        }

        if (archivedRows.isNotEmpty()) {
            item(key = "archived_section") {
                DashboardArchivedSection(
                    rows = archivedRows,
                    expanded = isArchivedExpanded,
                    onToggle = onToggleArchived,
                    onOpen = { row -> if (row.isCard) onCardClick(row.id) else onAccountClick(row.id) },
                )
            }
        }

        // Bottom space above floating nav bar
        item(key = "bottom_space") {
            Spacer(modifier = Modifier.height(72.dp).testTag("dashboard_bottom_space"))
        }
    }
}

@Composable
private fun InstrumentQuotaSelectionDialog(
    accounts: List<Account>,
    cards: List<DomainCard>,
    selectedIds: Set<String>,
    maxQuota: Int,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val allIds = remember(accounts, cards) {
        (accounts.map { it.id.value } + cards.map { it.id.value }).toSet()
    }
    val selected = remember(allIds, selectedIds) {
        mutableStateListOf<String>().apply {
            val existing = selectedIds.intersect(allIds)
            addAll(if (existing.isNotEmpty()) existing else allIds.take(maxQuota))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Instrumentos disponibles",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = emeraldColors.primaryText,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Elige hasta $maxQuota. Los demás conservarán su historial y saldos, pero no podrán usarse mientras superes el límite Free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emeraldColors.secondaryMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))
                accounts.forEach { account ->
                    val id = account.id.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = id in selected,
                            onCheckedChange = { checked ->
                                if (checked && selected.size < maxQuota) selected.add(id)
                                else if (!checked) selected.remove(id)
                            },
                        )
                        Text(account.alias, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = emeraldColors.primaryText)
                    }
                }
                cards.forEach { card ->
                    val id = card.id.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = id in selected,
                            onCheckedChange = { checked ->
                                if (checked && selected.size < maxQuota) selected.add(id)
                                else if (!checked) selected.remove(id)
                            },
                        )
                        Text(
                            card.alias ?: "${card.issuer} •••• ${card.lastFourDigits}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = emeraldColors.primaryText,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toSet()) }) {
                Text("Guardar", fontWeight = FontWeight.Bold, color = emeraldColors.primaryDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = emeraldColors.secondaryMuted)
            }
        },
    )
}
