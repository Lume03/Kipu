package com.kipu.app.feature.movements.presentation

import com.kipu.app.ui.theme.KipuModalSystemBars
import com.kipu.app.ui.theme.kipuBottomSheetProperties

import com.kipu.app.ui.theme.readableContentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberKipuColors

// Design tokens per docs/design/KIPU_UI_PASTEL_PROMPTS_AND_REFERENCE.md Reference A R3
// and the "Selector de cuenta (global)" board of the live canvas.
private val AndeanTealPrimary: Color @Composable get() = rememberKipuColors().primary
private val SurfaceRow: Color @Composable get() = rememberKipuColors().surface
private val SurfaceSelectedMint: Color @Composable get() = rememberKipuColors().selectedSurface
private val BorderNeutralSlate: Color @Composable get() = rememberKipuColors().border
private val TextPrimarySlate: Color @Composable get() = rememberKipuColors().inkPrimary
private val TextSecondarySlate: Color @Composable get() = rememberKipuColors().inkSecondary
private val TextPillInactive: Color @Composable get() = rememberKipuColors().inkSecondary
private val DragHandleSlate: Color @Composable get() = rememberKipuColors().dragHandle
private val MintBadgeContainer: Color @Composable get() = rememberKipuColors().positiveContainer
private val MintBadgeContent: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF86EFAC) else Color(0xFF166534)
private val CoralDebtText: Color @Composable get() = rememberKipuColors().debt

/**
 * What the selector is choosing. It never replaces the caller's own filtering (currency,
 * "different from the source"), it only enforces the instrument rules every flow shares:
 * income never lands on a credit card and a card picked as transfer destination is a card payment.
 */
enum class AccountSelectorPurpose {
    /** Legacy behavior: [AccountSelectorBottomSheet]'s `allowCreditCards` decides. */
    GENERIC,

    /** Expense: accounts, cash and credit cards. */
    EXPENSE_SOURCE,

    /** Income: accounts and cash only; credit cards are never offered. */
    INCOME_DESTINATION,

    /** Transfer origin: own money only (accounts and cash). */
    TRANSFER_SOURCE,

    /** Transfer destination: accounts, cash and credit cards (registered as a card payment). */
    TRANSFER_DESTINATION,

    /** Premium history filter: multi-select over accounts and cards, no "+ Nueva cuenta". */
    FILTER,
}

/** Copy and card rules per [AccountSelectorPurpose], so every caller shows the same wording. */
object AccountSelectorDefaults {
    fun title(purpose: AccountSelectorPurpose): String = when (purpose) {
        AccountSelectorPurpose.INCOME_DESTINATION -> "Seleccionar cuenta destino"
        AccountSelectorPurpose.TRANSFER_DESTINATION -> "Seleccionar cuenta destino"
        AccountSelectorPurpose.FILTER -> "Filtrar por cuenta o tarjeta"
        else -> "Seleccionar cuenta"
    }

    fun subtitle(purpose: AccountSelectorPurpose): String = when (purpose) {
        AccountSelectorPurpose.EXPENSE_SOURCE -> "Elige de dónde se descuenta el gasto"
        AccountSelectorPurpose.INCOME_DESTINATION -> "Elige dónde recibir el ingreso"
        AccountSelectorPurpose.TRANSFER_SOURCE -> "Elige desde dónde sale el dinero"
        AccountSelectorPurpose.TRANSFER_DESTINATION -> "Elige a dónde llega el dinero"
        AccountSelectorPurpose.FILTER -> "Puedes elegir varias"
        AccountSelectorPurpose.GENERIC -> "Selecciona la cuenta para este gasto"
    }

    /** Whether credit cards may appear for [purpose]; GENERIC defers to the caller's flag. */
    fun allowsCreditCards(purpose: AccountSelectorPurpose, callerAllows: Boolean): Boolean = when (purpose) {
        AccountSelectorPurpose.GENERIC -> callerAllows
        AccountSelectorPurpose.INCOME_DESTINATION,
        AccountSelectorPurpose.TRANSFER_SOURCE -> false
        AccountSelectorPurpose.EXPENSE_SOURCE,
        AccountSelectorPurpose.TRANSFER_DESTINATION,
        AccountSelectorPurpose.FILTER -> callerAllows
    }
}

private enum class InstrumentFilter {
    ALL,
    ACCOUNTS,
    CARDS,
    CASH,
}

private sealed interface SelectorItem {
    data class AccountItem(val account: Account) : SelectorItem
    data class CardItem(val card: CreditCard) : SelectorItem
}

/**
 * Global account/card selector shared by Registrar movimiento, Editar movimiento and the Premium
 * history filters.
 *
 * Filters Todas / Cuentas / Tarjetas / Efectivo with live counts (the Tarjetas pill disappears when
 * cards are not allowed), brand logo rows with balance (masked through [LocalBalanceMasked]; a
 * missing balance is never shown as S/ 0.00), affiliated Yape/Plin wallets labelled as sharing the
 * linked account balance, and a temporary selection confirmed with "Listo".
 *
 * Context:
 * - [purpose] enforces card rules (income never offers cards; transfer destination presents cards
 *   as a card payment) and, with [AccountSelectorDefaults], gives the copy per flow.
 * - [multiSelect] (or [AccountSelectorPurpose.FILTER]) toggles rows and reports both id sets
 *   through [onSelectionConfirmed]; "Listo" stays enabled so an empty set clears the filter.
 * - [balancesLoaded] tells a balance that is still loading ("Consultando saldo") apart from one
 *   that is not available ("Saldo no disponible").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelectorBottomSheet(
    onDismissRequest: () -> Unit,
    accounts: List<Account>,
    modifier: Modifier = Modifier,
    creditCards: List<CreditCard> = emptyList(),
    selectedAccountId: String? = null,
    selectedCardId: String? = null,
    allowCreditCards: Boolean = true,
    mostUsedAccountId: String? = null,
    accountBalances: Map<String, Money> = emptyMap(),
    cardDebts: Map<String, Money> = emptyMap(),
    cardAvailableCredits: Map<String, Money> = emptyMap(),
    title: String = "Seleccionar cuenta",
    subtitle: String = "Selecciona la cuenta para este gasto",
    onAccountSelected: (Account) -> Unit = {},
    onCardSelected: (CreditCard) -> Unit = {},
    onAccountIdSelected: ((String) -> Unit)? = null,
    onCardIdSelected: ((String) -> Unit)? = null,
    onNewAccountClick: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    purpose: AccountSelectorPurpose = AccountSelectorPurpose.GENERIC,
    multiSelect: Boolean = purpose == AccountSelectorPurpose.FILTER,
    selectedAccountIds: Set<String> = emptySet(),
    selectedCardIds: Set<String> = emptySet(),
    onSelectionConfirmed: ((accountIds: Set<String>, cardIds: Set<String>) -> Unit)? = null,
    balancesLoaded: Boolean = false,
    showNewAccountAction: Boolean = purpose != AccountSelectorPurpose.FILTER,
    confirmLabel: String = "Listo",
) {
    var tempSelectedAccountId by remember(selectedAccountId) { mutableStateOf(selectedAccountId) }
    var tempSelectedCardId by remember(selectedCardId) { mutableStateOf(selectedCardId) }
    var tempAccountIds by remember(selectedAccountIds) { mutableStateOf(selectedAccountIds) }
    var tempCardIds by remember(selectedCardIds) { mutableStateOf(selectedCardIds) }
    var activeFilter by remember { mutableStateOf(InstrumentFilter.ALL) }
    val cardsAllowed = AccountSelectorDefaults.allowsCreditCards(purpose, allowCreditCards)
    val cardsArePaymentTargets = purpose == AccountSelectorPurpose.TRANSFER_DESTINATION

    // Derive counts directly from live data
    val validAccounts = remember(accounts) {
        accounts.filter { it.type != AccountType.CREDIT_LIABILITY }
    }
    val accountsById = remember(accounts) { accounts.associateBy { it.id.value } }
    val accountsOnly = remember(validAccounts) {
        validAccounts.filter { it.type != AccountType.CASH }
    }
    val cashAccounts = remember(validAccounts) {
        validAccounts.filter { it.type == AccountType.CASH }
    }
    val cards = remember(creditCards, cardsAllowed) {
        if (cardsAllowed) creditCards else emptyList()
    }

    val accountsCount = accountsOnly.size
    val cashCount = cashAccounts.size
    val cardsCount = cards.size
    val totalCount = accountsCount + cashCount + cardsCount

    val displayedItems: List<SelectorItem> = remember(activeFilter, accountsOnly, cashAccounts, cards) {
        when (activeFilter) {
            InstrumentFilter.ALL -> {
                accountsOnly.map { SelectorItem.AccountItem(it) } +
                    cards.map { SelectorItem.CardItem(it) } +
                    cashAccounts.map { SelectorItem.AccountItem(it) }
            }
            InstrumentFilter.ACCOUNTS -> {
                accountsOnly.map { SelectorItem.AccountItem(it) }
            }
            InstrumentFilter.CARDS -> {
                cards.map { SelectorItem.CardItem(it) }
            }
            InstrumentFilter.CASH -> {
                cashAccounts.map { SelectorItem.AccountItem(it) }
            }
        }
    }

    ModalBottomSheet(
        properties = kipuBottomSheetProperties(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = rememberKipuColors().surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(DragHandleSlate)
                    .testTag("sheet_drag_handle"),
            )
        },
        modifier = modifier.testTag("account_selector_sheet"),
    ) {
        KipuModalSystemBars()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            // Header: Title, Subtitle, and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimarySlate,
                        ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = TextSecondarySlate,
                        ),
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_close_account_selector"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondarySlate,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Filter Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill(
                    label = "Todas",
                    count = totalCount,
                    selected = activeFilter == InstrumentFilter.ALL,
                    onClick = { activeFilter = InstrumentFilter.ALL },
                    testTag = "chip_filter_all",
                )

                FilterPill(
                    label = "Cuentas",
                    count = accountsCount,
                    selected = activeFilter == InstrumentFilter.ACCOUNTS,
                    onClick = { activeFilter = InstrumentFilter.ACCOUNTS },
                    testTag = "chip_filter_accounts",
                )

                if (cardsAllowed) {
                    FilterPill(
                        label = "Tarjetas",
                        count = cardsCount,
                        selected = activeFilter == InstrumentFilter.CARDS,
                        onClick = { activeFilter = InstrumentFilter.CARDS },
                        testTag = "chip_filter_cards",
                    )
                }

                FilterPill(
                    label = "Efectivo",
                    count = cashCount,
                    selected = activeFilter == InstrumentFilter.CASH,
                    onClick = { activeFilter = InstrumentFilter.CASH },
                    testTag = "chip_filter_cash",
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Financial Instruments List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("list_selectable_instruments"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (displayedItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No hay instrumentos disponibles en esta categoría",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondarySlate,
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(
                        items = displayedItems,
                        key = { item ->
                            when (item) {
                                is SelectorItem.AccountItem -> "account_${item.account.id.value}"
                                is SelectorItem.CardItem -> "card_${item.card.id.value}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is SelectorItem.AccountItem -> {
                                val account = item.account
                                val linkedAccount = account.linkedAccountId?.let { accountsById[it.value] }
                                // An affiliated wallet has no balance of its own: it shows the linked account's.
                                val balance = account.linkedAccountId?.let { accountBalances[it.value] }
                                    ?: accountBalances[account.id.value]
                                val isSelected = if (multiSelect) {
                                    account.id.value in tempAccountIds
                                } else {
                                    tempSelectedAccountId == account.id.value
                                }
                                val isMostUsed = mostUsedAccountId != null && account.id.value == mostUsedAccountId

                                AccountItemRow(
                                    account = account,
                                    linkedAccount = linkedAccount,
                                    balance = balance,
                                    balancesLoaded = balancesLoaded,
                                    isSelected = isSelected,
                                    isMostUsed = isMostUsed,
                                    multiSelect = multiSelect,
                                    onSelect = {
                                        if (multiSelect) {
                                            tempAccountIds = tempAccountIds.toggle(account.id.value)
                                        } else {
                                            tempSelectedAccountId = account.id.value
                                            tempSelectedCardId = null
                                        }
                                    },
                                )
                            }
                            is SelectorItem.CardItem -> {
                                val card = item.card
                                val debt = cardDebts[card.id.value]
                                val availableCredit = cardAvailableCredits[card.id.value]
                                val isSelected = if (multiSelect) {
                                    card.id.value in tempCardIds
                                } else {
                                    tempSelectedCardId == card.id.value
                                }

                                CreditCardItemRow(
                                    card = card,
                                    debt = debt,
                                    availableCredit = availableCredit,
                                    isSelected = isSelected,
                                    multiSelect = multiSelect,
                                    asPaymentTarget = cardsArePaymentTargets,
                                    onSelect = {
                                        if (multiSelect) {
                                            tempCardIds = tempCardIds.toggle(card.id.value)
                                        } else {
                                            tempSelectedAccountId = null
                                            tempSelectedCardId = card.id.value
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showNewAccountAction) {
                    OutlinedButton(
                        onClick = onNewAccountClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("btn_new_account"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, AndeanTealPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AndeanTealPrimary,
                        ),
                    ) {
                        Text(
                            text = "+ Nueva cuenta",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                        )
                    }
                }

                Button(
                    onClick = {
                        if (multiSelect) {
                            // Drop ids that the current rules hide (e.g. cards when not allowed).
                            val visibleCardIds = cards.map { it.id.value }.toSet()
                            onSelectionConfirmed?.invoke(tempAccountIds, tempCardIds.filter { it in visibleCardIds }.toSet())
                            onDismissRequest()
                            return@Button
                        }
                        val confirmedAccountId = tempSelectedAccountId
                        val confirmedCardId = tempSelectedCardId

                        if (confirmedAccountId != null) {
                            val selectedAcc = accounts.find { it.id.value == confirmedAccountId }
                            if (selectedAcc != null) {
                                onAccountSelected(selectedAcc)
                            }
                            onAccountIdSelected?.invoke(confirmedAccountId)
                            onSelectionConfirmed?.invoke(setOf(confirmedAccountId), emptySet())
                            onDismissRequest()
                        } else if (confirmedCardId != null) {
                            val selectedCrd = creditCards.find { it.id.value == confirmedCardId }
                            if (selectedCrd != null) {
                                onCardSelected(selectedCrd)
                            }
                            onCardIdSelected?.invoke(confirmedCardId)
                            onSelectionConfirmed?.invoke(emptySet(), setOf(confirmedCardId))
                            onDismissRequest()
                        }
                    },
                    enabled = multiSelect || tempSelectedAccountId != null || tempSelectedCardId != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .testTag("btn_confirm_selection"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AndeanTealPrimary,
                        contentColor = rememberKipuColors().onPrimary,
                        disabledContainerColor = AndeanTealPrimary.copy(alpha = 0.38f),
                        disabledContentColor = rememberKipuColors().onPrimary.copy(alpha = 0.60f),
                    ),
                ) {
                    Text(
                        text = if (multiSelect && (tempAccountIds.size + tempCardIds.size) > 0) {
                            "$confirmLabel (${tempAccountIds.size + tempCardIds.count { id -> cards.any { it.id.value == id } }})"
                        } else {
                            confirmLabel
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

@Composable
private fun FilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
) {
    val background = if (selected) AndeanTealPrimary else SurfaceRow
    val contentColor = if (selected) rememberKipuColors().onPrimary else TextPillInactive
    val border = if (selected) null else BorderStroke(1.5.dp, BorderNeutralSlate)
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .background(background)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label ($count)",
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        )
    }
}

@Composable
private fun AccountItemRow(
    account: Account,
    linkedAccount: Account?,
    balance: Money?,
    balancesLoaded: Boolean,
    isSelected: Boolean,
    isMostUsed: Boolean,
    multiSelect: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masked = LocalBalanceMasked.current
    val isAffiliated = account.linkedAccountId != null
    val subtitle = formatAccountSubtitle(account, linkedAccount)
    val missingBalanceText = if (balancesLoaded) "Saldo no disponible" else "Consultando saldo"
    val statusText = if (isAffiliated) "Saldo compartido" else "Disponible"

    val contentDesc = buildString {
        append(account.alias)
        append(". ")
        append(subtitle)
        if (isMostUsed) append(". Más usada.")
        if (balance != null) {
            append(if (isAffiliated) ". Saldo compartido con la cuenta afiliada: " else ". Saldo disponible: ")
            if (masked) {
                append("Monto oculto")
            } else {
                append(balance.currency.symbol())
                append(" ")
                append(formatMinorUnits(balance.minorUnits))
            }
        } else {
            append(". ")
            append(missingBalanceText)
        }
        append(if (isSelected) ". Seleccionada" else ". No seleccionada")
    }

    SelectableInstrumentSurface(
        isSelected = isSelected,
        multiSelect = multiSelect,
        onSelect = onSelect,
        contentDesc = contentDesc,
        testTag = "account_item_${account.id.value}",
        modifier = modifier,
    ) {
        AccountBadge(account = account)

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = account.alias,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimarySlate,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (isMostUsed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MintBadgeContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("badge_most_used_${account.id.value}"),
                    ) {
                        Text(
                            text = "Más usada",
                            color = MintBadgeContent,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondarySlate,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            if (balance != null) {
                MoneyText(
                    money = balance,
                    color = TextPrimarySlate,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    ),
                )
                Text(
                    text = statusText,
                    color = if (isAffiliated) TextSecondarySlate else MintBadgeContent,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                )
            } else {
                Text(
                    text = missingBalanceText,
                    color = TextSecondarySlate,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(88.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        SelectionIndicator(selected = isSelected, multiSelect = multiSelect)
    }
}

@Composable
private fun SelectableInstrumentSurface(
    isSelected: Boolean,
    multiSelect: Boolean,
    onSelect: () -> Unit,
    contentDesc: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val background = if (isSelected) SurfaceSelectedMint else SurfaceRow
    val borderColor = if (isSelected) AndeanTealPrimary else BorderNeutralSlate
    val shape = RoundedCornerShape(18.dp)
    val selection = if (multiSelect) {
        Modifier.toggleable(value = isSelected, onValueChange = { onSelect() }, role = Role.Checkbox)
    } else {
        Modifier.selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, borderColor), shape)
            .then(selection)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        color = background,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun CreditCardItemRow(
    card: CreditCard,
    debt: Money?,
    availableCredit: Money?,
    isSelected: Boolean,
    multiSelect: Boolean,
    asPaymentTarget: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masked = LocalBalanceMasked.current
    val cardTitle = card.alias?.takeIf { it.isNotBlank() } ?: "${card.issuer} ${networkLabel(card.network)}"
    val cardSubtitle = formatCardSubtitle(card, asPaymentTarget)
    fun spoken(money: Money?, missing: String) = when {
        money == null -> missing
        masked -> "Monto oculto"
        else -> "${card.currency.symbol()} ${formatMinorUnits(money.minorUnits)}"
    }

    val contentDesc = buildString {
        append(cardTitle)
        append(". ")
        append(cardSubtitle)
        append(". Crédito disponible, no es dinero propio: ")
        append(spoken(availableCredit, "no cargado"))
        append(". Deuda: ")
        append(spoken(debt, "no cargada"))
        append(if (isSelected) ". Seleccionada" else ". No seleccionada")
    }

    SelectableInstrumentSurface(
        isSelected = isSelected,
        multiSelect = multiSelect,
        onSelect = onSelect,
        contentDesc = contentDesc,
        testTag = "card_item_${card.id.value}",
        modifier = modifier,
    ) {
        BrandLogo(
            brand = KipuBrand.from(card.issuer) ?: KipuBrand.from(card.preset?.name),
            fallbackName = card.issuer,
            contentDescription = null,
            size = 44.dp,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cardTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimarySlate,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = cardSubtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondarySlate,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Créd. disp.: ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondarySlate,
                        fontSize = 11.sp,
                    ),
                )
                if (availableCredit != null) {
                    MoneyText(
                        money = availableCredit,
                        color = TextPrimarySlate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        ),
                    )
                } else {
                    Text("No disponible", color = TextSecondarySlate, style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Usado: ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CoralDebtText,
                        fontSize = 11.sp,
                    ),
                )
                if (debt != null) {
                    MoneyText(
                        money = debt,
                        color = CoralDebtText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                    )
                } else {
                    Text("No disponible", color = CoralDebtText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        SelectionIndicator(selected = isSelected, multiSelect = multiSelect)
    }
}

/** Bank and wallet rows use the shared brand logo; cash keeps the plain icon. */
@Composable
internal fun AccountBadge(
    account: Account,
    modifier: Modifier = Modifier,
) {
    if (account.type == AccountType.CASH) {
        val container = rememberKipuColors().positiveContainer
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = null,
                tint = readableContentColor(container),
                modifier = Modifier.size(24.dp),
            )
        }
        return
    }
    val brand = account.preset
        ?.takeIf { it != AccountPreset.GENERIC && it != AccountPreset.CASH }
        ?.let { KipuBrand.from(it.name) }
        ?: KipuBrand.from(account.alias)
    BrandLogo(
        brand = brand,
        fallbackName = account.alias,
        contentDescription = null,
        modifier = modifier,
        size = 44.dp,
    )
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    multiSelect: Boolean,
    modifier: Modifier = Modifier,
) {
    if (multiSelect) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier = modifier
                .size(24.dp)
                .clip(shape)
                .then(
                    if (selected) Modifier.background(AndeanTealPrimary)
                    else Modifier.border(BorderStroke(2.dp, DragHandleSlate), shape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = rememberKipuColors().onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    } else {
        // Radio ring as in the reference: a thick teal ring when selected.
        Box(
            modifier = modifier
                .size(24.dp)
                .border(
                    BorderStroke(if (selected) 7.dp else 2.dp, if (selected) AndeanTealPrimary else DragHandleSlate),
                    CircleShape,
                ),
        )
    }
}

private fun networkLabel(network: CardNetwork): String = when (network) {
    CardNetwork.VISA -> "VISA"
    CardNetwork.MASTERCARD -> "Mastercard"
    CardNetwork.AMEX -> "Amex"
    CardNetwork.DINERS -> "Diners"
    CardNetwork.OTHER -> "Tarjeta"
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.SAVINGS -> "Ahorros"
    AccountType.BANK -> "Cuenta bancaria"
    AccountType.DIGITAL_WALLET -> "Billetera digital"
    AccountType.CASH -> "Efectivo"
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

internal fun formatAccountSubtitle(account: Account, linkedAccount: Account? = null): String {
    if (account.type == AccountType.CASH) {
        return "Billetera física · ${account.currency.name}"
    }
    val institution = account.preset?.defaultName
    if (account.linkedAccountId != null) {
        // Affiliated Yape/Plin: same money as the bank account, never a balance of its own.
        val prefix = institution?.takeIf { it != "Otro" }?.let { "$it · " }.orEmpty()
        return linkedAccount?.let { "${prefix}Comparte saldo con ${it.alias}" }
            ?: "${prefix}Saldo compartido · ${account.currency.name}"
    }
    val typeName = accountTypeLabel(account.type)
    val currency = account.currency.name
    return if (institution != null && institution != "Otro") {
        "$institution · $typeName $currency"
    } else {
        "$typeName · $currency"
    }
}

internal fun formatCardSubtitle(card: CreditCard, asPaymentTarget: Boolean = false): String {
    return if (asPaymentTarget) {
        "Pago de tarjeta · •••• ${card.lastFourDigits}"
    } else {
        "Crédito · •••• ${card.lastFourDigits}"
    }
}
