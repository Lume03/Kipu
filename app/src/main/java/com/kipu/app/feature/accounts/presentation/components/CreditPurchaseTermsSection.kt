package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.credit_purchase_terms_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = colors.primaryText, modifier = Modifier.semantics { heading() })
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
                            label = { Text(label, fontWeight = FontWeight.SemiBold) }, enabled = enabled,
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.surfaceCard, labelColor = colors.primaryText,
                                selectedContainerColor = colors.primaryDeep, selectedLabelColor = colors.onPrimaryDeep,
                            ),
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
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
    var expanded by rememberSaveable(simulation.cardId.value) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().testTag("credit_purchase_preview").animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = colors.borderSubtle)
        Text(stringResource(R.string.credit_purchase_preview_title).uppercase(), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold, color = colors.primaryDeep)

        // Hero: "6 × S/ 222.58"
        Surface(shape = RoundedCornerShape(16.dp), color = colors.incomeBg, modifier = Modifier.fillMaxWidth()) {
            val amounts = simulation.schedule.map { it.amount }
            val equalPayments = amounts.distinct().size == 1
            val minimum = amounts.minBy { it.minorUnits }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.credit_purchase_payment_amount), style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!equalPayments) Text(stringResource(R.string.credit_purchase_installments_from, simulation.installmentsCount),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
                    else if (simulation.installmentsCount > 1) Text("${simulation.installmentsCount} ×",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
                    MoneyText(if (equalPayments) first.amount else minimum, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                }
                if (!equalPayments) {
                    var start = 0
                    while (start < amounts.size) {
                        var end = start
                        while (end + 1 < amounts.size && amounts[end + 1] == amounts[start]) end++
                        val label = when {
                            start == 0 && end == 0 -> stringResource(R.string.credit_purchase_first_installment)
                            start == 1 && end == amounts.lastIndex -> stringResource(R.string.credit_purchase_following_installments)
                            start == end -> stringResource(R.string.credit_purchase_installment_number, start + 1)
                            else -> stringResource(R.string.credit_purchase_installment_range, start + 1, end + 1)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                            MoneyText(amounts[start], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.secondaryMuted)
                        }
                        start = end + 1
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewStat(stringResource(R.string.credit_purchase_interest_total), simulation.totalInterest, Modifier.weight(1f))
            PreviewStat(stringResource(R.string.credit_purchase_total), simulation.totalFinanced, Modifier.weight(1f))
        }

        // Rate
        simulation.appliedTeaBps?.let { tea ->
            val source = stringResource(when (simulation.terms.rateSource) {
                PurchaseRateSource.PERSONAL -> R.string.credit_purchase_rate_personal
                PurchaseRateSource.REFERENCE -> R.string.credit_purchase_rate_reference
                else -> R.string.credit_purchase_rate_user
            })
            val tem = simulation.appliedTemPercentage?.let {
                " · " + stringResource(R.string.credit_purchase_applied_tem, String.format(Locale.forLanguageTag("es-PE"), "%.4f", it))
            }.orEmpty()
            Text(stringResource(R.string.credit_purchase_applied_tea, BigDecimal.valueOf(tea.toLong(), 2).toPlainString()) + tem,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.primaryText)
            Text(source, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            if (simulation.terms.rateSource == PurchaseRateSource.REFERENCE) Surface(shape = RoundedCornerShape(10.dp), color = colors.warningBg) {
                Text(stringResource(R.string.credit_purchase_reference_estimate), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = colors.warningText)
            }
        } ?: Text(stringResource(R.string.credit_purchase_zero_rates), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)

        // Cycle timeline: closing -> first due
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            first.billingClosingDate?.let {
                CyclePoint(stringResource(R.string.credit_purchase_closing, it.format(formatter)), Icons.Outlined.EventNote, Modifier.weight(1f))
                Box(Modifier.width(20.dp).height(2.dp).background(colors.borderSubtle))
            }
            CyclePoint(stringResource(R.string.credit_purchase_first_due, first.dueDate.format(formatter)), Icons.Outlined.Event, Modifier.weight(1f))
        }
        Text(stringResource(R.string.credit_purchase_cycle_validation), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)

        val scheduleState = stringResource(if (expanded) R.string.credit_purchase_schedule_expanded else R.string.credit_purchase_schedule_collapsed)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = scheduleState }) {
            Text(stringResource(if (expanded) R.string.credit_purchase_hide_schedule else R.string.credit_purchase_show_schedule),
                color = colors.primaryDeep, fontWeight = FontWeight.Bold)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.primaryDeep)
        }
        if (expanded) Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.borderSubtle), color = colors.surfaceCard) {
            Column {
                simulation.schedule.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = colors.borderSubtle)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.installmentNumber}/${simulation.installmentsCount}", Modifier.width(48.dp),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.primaryDeep)
                        Text(item.dueDate.format(formatter), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                        MoneyText(item.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.primaryText)
                    }
                }
            }
        }
        Text(stringResource(R.string.credit_purchase_terms_confirmation), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
    }
}

@Composable
private fun PreviewStat(label: String, amount: Money, modifier: Modifier) {
    val colors = rememberCalmEmeraldColors()
    Surface(shape = RoundedCornerShape(14.dp), color = colors.pillTrack, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
            MoneyText(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.primaryText)
        }
    }
}

@Composable
private fun CyclePoint(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val colors = rememberCalmEmeraldColors()
    Surface(shape = RoundedCornerShape(12.dp), color = colors.transferBg, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = colors.transferBlue, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = colors.primaryText)
        }
    }
}

@Composable
internal fun CreditInstallmentAmountRow(label: String, amount: Money) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        MoneyText(amount, fontWeight = FontWeight.SemiBold)
    }
}
