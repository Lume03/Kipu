package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.AccountType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.symbol
import com.kipu.app.feature.movements.presentation.AccountSelectionRow
import com.kipu.app.feature.movements.presentation.formatAccountSubtitle
import com.kipu.app.ui.component.formatMinorUnits

typealias CreditCardPaymentHandler = (
    cardId: CardId,
    sourceAccountId: AccountId,
    paymentAmount: Money,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayCardDialog(
    creditCardWithSummary: CreditCardWithSummary,
    eligibleAccounts: List<AccountWithBalance>,
    onPayCreditCard: CreditCardPaymentHandler,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = com.kipu.app.ui.theme.rememberCalmEmeraldColors()
    val card = creditCardWithSummary.card
    val debt = creditCardWithSummary.debt

    val matchingAccounts = remember(eligibleAccounts, card.currency) {
        eligibleCardPaymentAccounts(eligibleAccounts, card.currency)
    }

    var selectedAccount by remember { mutableStateOf(matchingAccounts.firstOrNull()) }
    var amountInput by remember { mutableStateOf(formatMinorUnits(debt.minorUnits)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        header = {
            Text(
                text = "Pagar Tarjeta de Crédito",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${card.alias ?: card.issuer} (•••• ${card.lastFourDigits})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Deuda actual: ${card.currency.symbol()} ${formatMinorUnits(debt.minorUnits)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                // Non-expense explanation
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Este pago reduce tu deuda y tus fondos disponibles como amortización; no suma como gasto operativo.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Text(
                    text = "Cuenta de Origen",
                    style = MaterialTheme.typography.titleSmall,
                )

                if (matchingAccounts.isEmpty()) {
                    Text(
                        text = "No tienes cuentas activas en ${card.currency.name} con saldo suficiente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingAccounts.forEach { acc ->
                            AccountSelectionRow(
                                alias = acc.account.alias,
                                subtitle = formatAccountSubtitle(acc.account),
                                type = acc.account.type,
                                preset = acc.account.preset,
                                balance = acc.balance,
                                isSelected = selectedAccount?.account?.id == acc.account.id,
                                onSelect = { selectedAccount = acc },
                                testTag = "card_payment_account_${acc.account.id.value}",
                            )
                        }
                    }
                }

                Text(
                    text = "Importe a Pagar",
                    style = MaterialTheme.typography.titleSmall,
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        errorMessage = null
                    },
                    label = { Text("Monto (${card.currency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KipuFilterChip(
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = colors.surfaceCard,
                            labelColor = colors.primaryDeep,
                            selectedContainerColor = colors.incomeBg,
                            selectedLabelColor = colors.primaryDeep,
                        ),
                        selected = amountInput == formatMinorUnits(debt.minorUnits),
                        onClick = {
                            amountInput = formatMinorUnits(debt.minorUnits)
                            errorMessage = null
                        },
                        label = { Text("Pagar total ${card.currency.symbol()} ${formatMinorUnits(debt.minorUnits)}") },
                    )
                }
            }
        },
        footer = {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                    onClick = {
                        val minorUnits = parseAmountToMinorUnits(amountInput)
                        if (minorUnits == null || minorUnits <= 0L) {
                            errorMessage = "Ingresa un monto mayor a cero"
                            return@Button
                        }
                        if (minorUnits > debt.minorUnits) {
                            errorMessage = "El monto no puede exceder la deuda actual (${formatMinorUnits(debt.minorUnits)})"
                            return@Button
                        }
                        val sourceAcc = selectedAccount
                        if (sourceAcc == null) {
                            errorMessage = "Selecciona una cuenta de origen"
                            return@Button
                        }
                        if (minorUnits > sourceAcc.balance.minorUnits) {
                            errorMessage = "Fondos insuficientes en la cuenta origen (${formatMinorUnits(sourceAcc.balance.minorUnits)})"
                            return@Button
                        }

                        isSubmitting = true
                        onPayCreditCard(
                            card.id,
                            sourceAcc.account.id,
                            Money(minorUnits, card.currency),
                            onDismiss,
                            { message ->
                                isSubmitting = false
                                errorMessage = message
                            },
                        )
                    },
                    enabled = !isSubmitting && selectedAccount != null && debt.minorUnits > 0L,
                ) {
                    Text(if (isSubmitting) "Procesando..." else "Confirmar Pago")
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar")
                }
            }
        },
        modifier = modifier,
    )
}

internal fun eligibleCardPaymentAccounts(
    accounts: List<AccountWithBalance>,
    cardCurrency: com.kipu.app.core.finance.domain.model.Currency,
): List<AccountWithBalance> = accounts.filter {
    it.account.type in setOf(AccountType.BANK, AccountType.SAVINGS) &&
        it.account.currency == cardCurrency && !it.account.isArchived && it.balance.minorUnits > 0L
}
