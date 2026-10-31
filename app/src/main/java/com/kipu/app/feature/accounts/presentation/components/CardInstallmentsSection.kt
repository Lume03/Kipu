package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CardInstallment
import com.kipu.app.feature.accounts.domain.model.CardInstallmentStatus
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.feature.accounts.domain.model.CardInstallmentsSummary
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CardInstallmentsUiState(
    val summary: CardInstallmentsSummary? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

private val GROUP_ORDER = listOf(
    CardInstallmentStatus.OVERDUE,
    CardInstallmentStatus.UPCOMING,
    CardInstallmentStatus.PENDING,
    CardInstallmentStatus.PAID,
)

private val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))

@Composable
fun CardInstallmentsSection(
    cardId: String,
    state: CardInstallmentsUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    var filterName by rememberSaveable(cardId) { mutableStateOf("ALL") }
    val selectedStatus = CardInstallmentStatus.entries.firstOrNull { it.name == filterName }
    Column(
        modifier.fillMaxWidth().testTag("card_installments_section").animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.credit_installments_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colors.primaryText,
            modifier = Modifier.semantics { heading() },
        )
        when {
            state.isLoading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primaryDeep, trackColor = colors.pillTrack)
                Text(stringResource(R.string.credit_installments_loading), color = colors.secondaryMuted)
            }
            state.error != null -> {
                Text(state.error, color = colors.expenseText)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.credit_installments_retry), color = colors.primaryDeep) }
            }
            state.summary != null && state.summary.installments.isEmpty() -> EmptyInstallments(colors)
            state.summary != null -> {
                val summary = state.summary
                InstallmentsSummaryCard(summary, colors)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (listOf<CardInstallmentStatus?>(null) + GROUP_ORDER).forEach { status ->
                        val label = stringResource(statusFilterLabel(status))
                        val count = if (status == null) summary.installments.size else summary.installments.count { it.status == status }
                        val description = stringResource(R.string.credit_installments_filter_description, label)
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { filterName = status?.name ?: "ALL" },
                            label = { Text("$label · $count", fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.surfaceCard,
                                labelColor = colors.primaryText,
                                selectedContainerColor = colors.primaryDeep,
                                selectedLabelColor = colors.onPrimaryDeep,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedStatus == status,
                                borderColor = colors.borderSubtle,
                                selectedBorderColor = colors.primaryDeep,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = description }
                                .testTag("card_installments_filter_${status?.name ?: "ALL"}"),
                        )
                    }
                }
                val rows = summary.installments.filter { selectedStatus == null || it.status == selectedStatus }
                when {
                    rows.isEmpty() -> Text(stringResource(R.string.credit_installments_filter_empty), color = colors.secondaryMuted)
                    // "Todas" groups by status (most urgent first); a single filter is already one group.
                    selectedStatus == null -> GROUP_ORDER.forEach { status ->
                        val group = rows.filter { it.status == status }.sortedBy { it.dueDate }
                        if (group.isNotEmpty()) {
                            Text(
                                stringResource(statusFilterLabel(status)).uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = status.style(colors).content,
                                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                            )
                            group.forEach { item -> key(item.id) { CardInstallmentRow(item) } }
                        }
                    }
                    else -> rows.sortedBy { it.dueDate }.forEach { item -> key(item.id) { CardInstallmentRow(item) } }
                }
            }
            else -> EmptyInstallments(colors)
        }
    }
}

@Composable
private fun InstallmentsSummaryCard(summary: CardInstallmentsSummary, colors: CalmEmeraldColors) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.credit_installments_outstanding),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.secondaryMuted,
                )
                MoneyText(
                    summary.outstandingTotal,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primaryText,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStat(R.string.credit_installments_upcoming_total, summary.upcomingTotal, CardInstallmentStatus.UPCOMING, colors, Modifier.weight(1f))
                SummaryStat(R.string.credit_installments_overdue_total, summary.overdueTotal, CardInstallmentStatus.OVERDUE, colors, Modifier.weight(1f))
                SummaryStat(R.string.credit_installments_paid_total, summary.paidTotal, CardInstallmentStatus.PAID, colors, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryStat(label: Int, amount: Money, status: CardInstallmentStatus, colors: CalmEmeraldColors, modifier: Modifier) {
    val style = status.style(colors)
    Surface(shape = RoundedCornerShape(14.dp), color = style.container, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(style.icon, contentDescription = null, tint = style.content, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MoneyText(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.primaryText)
        }
    }
}

@Composable
fun CardInstallmentRow(item: CardInstallment, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val style = item.status.style(colors)
    val progress = if (item.total.minorUnits <= 0L) 1f
    else ((item.total.minorUnits - item.outstanding.minorUnits).toFloat() / item.total.minorUnits).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("card_installment_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, if (item.status == CardInstallmentStatus.OVERDUE) colors.expenseBorder else colors.borderSubtle),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(style.container, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        "${item.installmentNumber}/${item.installmentsCount}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = style.content,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.merchant?.takeIf(String::isNotBlank) ?: stringResource(R.string.credit_installments_purchase_fallback),
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.credit_installment_due, item.dueDate.format(DUE_FORMAT)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyText(
                        if (item.status == CardInstallmentStatus.PAID) item.total else item.outstanding,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primaryText,
                    )
                    InstallmentStatusChip(item.status)
                }
            }
            if (item.status != CardInstallmentStatus.PAID && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = style.accent,
                    trackColor = colors.pillTrack,
                    strokeCap = StrokeCap.Round,
                )
            }
            val closingText = item.billingClosingDate?.let { stringResource(R.string.credit_purchase_closing, it.format(DUE_FORMAT)) }
            val details = listOfNotNull(item.description?.takeIf(String::isNotBlank), closingText)
            if (details.isNotEmpty() || item.interest.minorUnits > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        details.joinToString(" · "),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.status != CardInstallmentStatus.PAID) {
                        Text(stringResource(R.string.credit_installment_total_label) + " ", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                        MoneyText(item.total, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInstallments(colors: CalmEmeraldColors) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(colors.incomeBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(20.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.credit_installments_empty), color = colors.secondaryMuted, style = MaterialTheme.typography.bodyMedium)
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
