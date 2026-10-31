package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Financing
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.PurchaseRateSource
import com.kipu.app.feature.accounts.domain.model.PurchaseType
import com.kipu.app.ui.component.KipuSegmentedControl
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.util.Locale

/** Shared controls for a declared purchase; calculation and validation are supplied by the caller. */
@Composable
fun CreditPurchaseTermsSection(
    draft: CreditPurchaseDraft,
    validation: CreditPurchaseValidation,
    simulation: InstallmentSimulation?,
    onDraftChanged: (CreditPurchaseDraft) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    referenceLoading: Boolean = false,
    previewError: String? = null,
) {
    val colors = rememberCalmEmeraldColors()
    val directLabel = stringResource(R.string.credit_purchase_direct)
    val installmentsLabel = stringResource(R.string.credit_purchase_installments)
    val typeLabel = stringResource(R.string.credit_purchase_type_label)
    val freeLabel = stringResource(R.string.credit_purchase_interest_free)
    val interestLabel = stringResource(R.string.credit_purchase_with_interest)
    val financingLabel = stringResource(R.string.credit_purchase_financing_label)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("credit_purchase_terms"),
        color = colors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.credit_purchase_terms_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = colors.primaryText, modifier = Modifier.semantics { heading() })
            KipuSegmentedControl(
                options = PurchaseType.entries, selected = draft.purchaseType,
                onSelect = { onDraftChanged(draft.copy(purchaseType = it)) },
                label = { if (it == PurchaseType.DIRECT) directLabel else installmentsLabel },
                enabled = enabled, modifier = Modifier.semantics { contentDescription = typeLabel },
                tag = { "credit_purchase_type_${it.name}" },
            )
            if (draft.purchaseType == PurchaseType.INSTALLMENTS) {
                KipuSegmentedControl(
                    options = listOf(Financing.INTEREST_FREE, Financing.WITH_INTEREST), selected = draft.financing,
                    onSelect = { onDraftChanged(draft.copy(financing = it)) },
                    label = { if (it == Financing.INTEREST_FREE) freeLabel else interestLabel },
                    enabled = enabled, modifier = Modifier.semantics { contentDescription = financingLabel },
                    tag = { "credit_purchase_financing_${it.name}" },
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12, 24).forEach { count ->
                        val label = stringResource(R.string.credit_purchase_count_option, count)
                        FilterChip(selected = draft.installmentsText == count.toString(),
                            onClick = { onDraftChanged(draft.copy(installmentsText = count.toString())) },
                            label = { Text(label) }, enabled = enabled,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label })
                    }
                }
                OutlinedTextField(
                    value = draft.installmentsText,
                    onValueChange = { onDraftChanged(draft.copy(installmentsText = it.filter(Char::isDigit).take(2))) },
                    label = { Text(stringResource(R.string.credit_purchase_count_label)) },
                    supportingText = { Text(validation.installmentsError ?: stringResource(R.string.credit_purchase_count_hint)) },
                    isError = validation.installmentsError != null, singleLine = true, enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("credit_purchase_installments_count"),
                )
                if (draft.financing == Financing.WITH_INTEREST) {
                    val rateDescription = stringResource(when (draft.rateSource) {
                        PurchaseRateSource.PERSONAL -> R.string.credit_purchase_rate_personal
                        PurchaseRateSource.REFERENCE -> R.string.credit_purchase_rate_reference
                        PurchaseRateSource.USER_ENTERED -> R.string.credit_purchase_rate_user
                        null -> if (referenceLoading) R.string.credit_purchase_rate_loading else R.string.credit_purchase_rate_missing
                    })
                    OutlinedTextField(
                        value = draft.teaPercentText,
                        onValueChange = { text -> onDraftChanged(draft.copy(
                            teaPercentText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(10),
                            rateSource = PurchaseRateSource.USER_ENTERED,
                        )) },
                        label = { Text(stringResource(R.string.credit_purchase_tea_label)) },
                        supportingText = { Text(validation.teaError ?: rateDescription) },
                        isError = validation.teaError != null, singleLine = true, enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("credit_purchase_tea"),
                    )
                    if (validation.teaError != null) Text(rateDescription,
                        style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                }
            }
            if (simulation != null) CreditPurchasePreview(simulation)
            else Text(previewError ?: validation.installmentsError ?: validation.teaError
                ?: stringResource(R.string.credit_purchase_preview_amount_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (previewError != null) MaterialTheme.colorScheme.error else colors.secondaryMuted)
        }
    }
}

/** The displayed amounts and dates come directly from the domain simulation. */
@Composable
fun CreditPurchasePreview(simulation: InstallmentSimulation, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val first = simulation.schedule.first()
    val last = simulation.schedule.last()
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var expanded by rememberSaveable(simulation.cardId.value) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().testTag("credit_purchase_preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = colors.borderSubtle)
        Text(stringResource(R.string.credit_purchase_preview_title), fontWeight = FontWeight.SemiBold, color = colors.primaryText)
        CreditInstallmentAmountRow(stringResource(R.string.credit_purchase_payment_amount), first.amount)
        if (last.amount != first.amount) CreditInstallmentAmountRow(stringResource(R.string.credit_purchase_last_payment), last.amount)
        CreditInstallmentAmountRow(stringResource(R.string.credit_purchase_interest_total), simulation.totalInterest)
        CreditInstallmentAmountRow(stringResource(R.string.credit_purchase_total), simulation.totalFinanced)
        simulation.appliedTeaBps?.let { tea ->
            val source = stringResource(when (simulation.terms.rateSource) {
                PurchaseRateSource.PERSONAL -> R.string.credit_purchase_rate_personal
                PurchaseRateSource.REFERENCE -> R.string.credit_purchase_rate_reference
                else -> R.string.credit_purchase_rate_user
            })
            Text(stringResource(R.string.credit_purchase_applied_tea, BigDecimal.valueOf(tea.toLong(), 2).toPlainString()),
                style = MaterialTheme.typography.bodySmall)
            simulation.appliedTemPercentage?.let { tem ->
                Text(stringResource(R.string.credit_purchase_applied_tem, String.format(Locale.forLanguageTag("es-PE"), "%.4f", tem)),
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(source, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            if (simulation.terms.rateSource == PurchaseRateSource.REFERENCE) Text(
                stringResource(R.string.credit_purchase_reference_estimate), style = MaterialTheme.typography.bodySmall, color = colors.warningText)
        } ?: Text(stringResource(R.string.credit_purchase_zero_rates), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        first.billingClosingDate?.let { Text(stringResource(R.string.credit_purchase_closing, it.format(formatter)), style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.credit_purchase_first_due, first.dueDate.format(formatter)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.credit_purchase_cycle_validation), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        val scheduleState = stringResource(if (expanded) R.string.credit_purchase_schedule_expanded else R.string.credit_purchase_schedule_collapsed)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = scheduleState }) {
            Text(stringResource(if (expanded) R.string.credit_purchase_hide_schedule else R.string.credit_purchase_show_schedule))
        }
        if (expanded) simulation.schedule.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CreditInstallmentAmountRow(stringResource(R.string.credit_installment_number, item.installmentNumber, simulation.installmentsCount), item.amount)
                Text(stringResource(R.string.credit_installment_due, item.dueDate.format(formatter)), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
        }
        Text(stringResource(R.string.credit_purchase_terms_confirmation), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
    }
}

@Composable
internal fun CreditInstallmentAmountRow(label: String, amount: Money) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        MoneyText(amount, fontWeight = FontWeight.SemiBold)
    }
}
