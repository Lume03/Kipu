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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.accounts.domain.model.CardInstallment
import com.kipu.app.feature.accounts.domain.model.CardInstallmentsSummary
import com.kipu.app.feature.accounts.domain.model.CardInstallmentStatus
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.format.DateTimeFormatter

data class CardInstallmentsUiState(
    val summary: CardInstallmentsSummary? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@Composable
fun CardInstallmentsSection(
    cardId: String,
    state: CardInstallmentsUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    var filterName by rememberSaveable(cardId) { mutableStateOf("ALL") }
    var grouped by rememberSaveable(cardId) { mutableStateOf(true) }
    val selectedStatus = CardInstallmentStatus.entries.firstOrNull { it.name == filterName }
    val groupLabel = stringResource(R.string.credit_installments_group_by_status)
    Column(modifier.fillMaxWidth().testTag("card_installments_section"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.credit_installments_title), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, color = colors.primaryText, modifier = Modifier.semantics { heading() })
        when {
            state.isLoading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.credit_installments_loading), color = colors.secondaryMuted)
            }
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.credit_installments_retry)) }
            }
            state.summary != null -> {
                val summary = state.summary
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderSubtle)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreditInstallmentAmountRow(stringResource(R.string.credit_installments_outstanding), summary.outstandingTotal)
                        CreditInstallmentAmountRow(stringResource(R.string.credit_installments_upcoming_total), summary.upcomingTotal)
                        CreditInstallmentAmountRow(stringResource(R.string.credit_installments_overdue_total), summary.overdueTotal)
                        CreditInstallmentAmountRow(stringResource(R.string.credit_installments_paid_total), summary.paidTotal)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<CardInstallmentStatus?>(null) + CardInstallmentStatus.entries).forEach { status ->
                        val label = stringResource(statusFilterLabel(status))
                        val description = stringResource(R.string.credit_installments_filter_description, label)
                        FilterChip(selected = selectedStatus == status,
                            onClick = { filterName = status?.name ?: "ALL" }, label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description }
                                .testTag("card_installments_filter_${status?.name ?: "ALL"}"))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(groupLabel, Modifier.weight(1f).padding(vertical = 12.dp), color = colors.secondaryMuted)
                    Switch(checked = grouped, onCheckedChange = { grouped = it }, modifier = Modifier.semantics { contentDescription = groupLabel })
                }
                val rows = summary.installments.filter { selectedStatus == null || it.status == selectedStatus }
                if (rows.isEmpty()) Text(stringResource(if (summary.installments.isEmpty()) R.string.credit_installments_empty
                    else R.string.credit_installments_filter_empty), color = colors.secondaryMuted)
                else if (grouped) {
                    listOf(CardInstallmentStatus.OVERDUE, CardInstallmentStatus.UPCOMING, CardInstallmentStatus.PENDING, CardInstallmentStatus.PAID).forEach { status ->
                        val group = rows.filter { it.status == status }
                        if (group.isNotEmpty()) {
                            Text(stringResource(statusFilterLabel(status)), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                            group.forEach { item -> key(item.id) { CardInstallmentRow(item) } }
                        }
                    }
                } else rows.forEach { item -> key(item.id) { CardInstallmentRow(item) } }
            }
            else -> Text(stringResource(R.string.credit_installments_empty), color = colors.secondaryMuted)
        }
    }
}

@Composable
fun CardInstallmentRow(item: CardInstallment, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Surface(modifier = modifier.fillMaxWidth().testTag("card_installment_${item.id}"),
        shape = RoundedCornerShape(14.dp), color = colors.surfaceCard, border = BorderStroke(1.dp, colors.borderSubtle)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.merchant?.takeIf(String::isNotBlank) ?: stringResource(R.string.credit_installments_purchase_fallback),
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = colors.primaryText)
                InstallmentStatusChip(item.status)
            }
            item.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted) }
            Text(stringResource(R.string.credit_installment_number, item.installmentNumber, item.installmentsCount), color = colors.primaryText)
            Text(stringResource(R.string.credit_installment_due, item.dueDate.format(formatter)), style = MaterialTheme.typography.bodySmall)
            item.billingClosingDate?.let { Text(stringResource(R.string.credit_purchase_closing, it.format(formatter)),
                style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted) }
            CreditInstallmentAmountRow(stringResource(R.string.credit_installment_total_label), item.total)
            CreditInstallmentAmountRow(stringResource(R.string.credit_installment_outstanding_label), item.outstanding)
        }
    }
}

private fun statusFilterLabel(status: CardInstallmentStatus?): Int = when (status) {
    null -> R.string.credit_installments_all
    CardInstallmentStatus.PENDING -> R.string.credit_installments_pending
    CardInstallmentStatus.UPCOMING -> R.string.credit_installments_upcoming
    CardInstallmentStatus.OVERDUE -> R.string.credit_installments_overdue
    CardInstallmentStatus.PAID -> R.string.credit_installments_paid
}
