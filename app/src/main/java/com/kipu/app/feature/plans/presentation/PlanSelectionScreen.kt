package com.kipu.app.feature.plans.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.ui.theme.KipuTheme

@Composable
fun PlanSelectionScreen(
    state: PlanSelectionUiState,
    onOptionSelected: (CommercialOption) -> Unit,
    onConfirm: () -> Unit,
    onContinueFree: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
) {
    val isFree = state.selectedOption == CommercialOption.FREE
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.plan_preference_notice), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.error != null) Text(stringResource(R.string.plan_confirmation_failed), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = if (isFree) onContinueFree else onConfirm,
                    enabled = !state.isConfirming,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("plan-confirm"),
                ) {
                    if (state.isConfirming) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (state.isConfirming) R.string.plan_confirming else if (isFree) R.string.plan_continue_free else R.string.plan_confirm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.plan_back)) }
            } else Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plan_selection_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.plan_selection_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlanSegments(listOf(R.string.plan_tab_free, R.string.plan_tab_premium), if (isFree) 0 else 1, !state.isConfirming) {
                onOptionSelected(if (it == 0) CommercialOption.FREE else state.premiumOption)
            }
            if (isFree) FreePlanCard(state.freeLimits, !state.isConfirming) { onOptionSelected(state.premiumOption) }
            else PremiumPlanCard(state.selectedOption, !state.isConfirming, onOptionSelected)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlanSegments(options: List<Int>, selectedIndex: Int, enabled: Boolean, onSelected: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
            options.forEachIndexed { index, title ->
                val selected = selectedIndex == index
                Surface(
                    modifier = Modifier.weight(1f).testTag(when (title) {
                        R.string.plan_tab_annual -> "plan-option-ANNUAL"
                        R.string.plan_tab_monthly -> "plan-option-MONTHLY"
                        R.string.plan_tab_lifetime -> "plan-option-LIFETIME"
                        R.string.plan_tab_free -> "plan-tab-FREE"
                        else -> "plan-tab-PREMIUM"
                    }).selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelected(index) }),
                    color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                ) {
                    Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(title), fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumPlanCard(option: CommercialOption, enabled: Boolean, onSelected: (CommercialOption) -> Unit) {
    val options = listOf(CommercialOption.MONTHLY, CommercialOption.ANNUAL, CommercialOption.LIFETIME)
    val price = when (option) {
        CommercialOption.MONTHLY -> R.string.plan_display_monthly_price
        CommercialOption.LIFETIME -> R.string.plan_display_lifetime_price
        else -> R.string.plan_display_annual_price
    }
    val period = when (option) {
        CommercialOption.MONTHLY -> R.string.plan_period_monthly
        CommercialOption.LIFETIME -> R.string.plan_period_lifetime
        else -> R.string.plan_period_annual
    }
    Card(Modifier.fillMaxWidth().testTag("plan-premium-card"), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PlanSegments(listOf(R.string.plan_tab_monthly, R.string.plan_tab_annual, R.string.plan_tab_lifetime), options.indexOf(option), enabled) { onSelected(options[it]) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(price), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("plan-price-${option.name}"))
                    Text(stringResource(period), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (option == CommercialOption.ANNUAL) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.plan_display_savings), Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            PremiumFeature(Icons.Outlined.AllInclusive, R.string.plan_premium_unlimited)
            PremiumFeature(Icons.Outlined.Bolt, R.string.plan_premium_capture)
            PremiumFeature(Icons.Outlined.CreditCard, R.string.plan_premium_accounts)
            Text(stringResource(when (option) {
                CommercialOption.MONTHLY -> R.string.plan_display_monthly_trial
                CommercialOption.LIFETIME -> R.string.plan_display_lifetime_note
                else -> R.string.plan_display_annual_trial
            }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PremiumFeature(icon: ImageVector, @StringRes title: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureIcon(icon)
        Text(stringResource(title), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FeatureIcon(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun FreePlanCard(limits: FreePlanLimits, enabled: Boolean, onPremium: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("plan-free-card"), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plan_display_free_price), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.plan_without_card), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(stringResource(R.string.plan_forever), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()
            FreeFeature(Icons.Outlined.CreditCard, R.string.plan_limit_accounts, R.string.plan_limit_accounts_detail, limits.instruments)
            FreeFeature(Icons.Outlined.Sell, R.string.plan_limit_categories, R.string.plan_limit_categories_detail, limits.customCategories)
            FreeFeature(Icons.AutoMirrored.Outlined.ReceiptLong, R.string.plan_limit_debts, R.string.plan_limit_active, limits.debts)
            FreeFeature(Icons.Outlined.TrackChanges, R.string.plan_limit_goals, R.string.plan_limit_active, limits.goals)
            FreeFeature(Icons.Outlined.PieChart, R.string.plan_limit_budgets, R.string.plan_limit_monthly, limits.budgets)
            FreeFeature(Icons.Outlined.Edit, R.string.plan_manual, R.string.plan_manual_detail, null)
        }
        HorizontalDivider()
        TextButton(onClick = onPremium, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.LockOpen, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.plan_remove_limits), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun FreeFeature(icon: ImageVector, @StringRes title: Int, @StringRes subtitle: Int, limit: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FeatureIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (limit != null) {
            Text(stringResource(R.string.plan_up_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(limit.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.plan_included_content_description), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (limit != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlanSelectionScreenPreview() {
    KipuTheme { PlanSelectionScreen(PlanSelectionUiState(), {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FreePlanSelectionScreenPreview() {
    KipuTheme { PlanSelectionScreen(PlanSelectionUiState(selectedOption = CommercialOption.FREE), {}, {}, {}) }
}
