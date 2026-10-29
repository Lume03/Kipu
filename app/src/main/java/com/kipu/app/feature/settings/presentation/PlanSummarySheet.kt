package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.R
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanSummarySheet(
    plan: SettingsPlanCard,
    onDismiss: () -> Unit,
    onViewOffers: (() -> Unit)?,
) {
    KipuBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(plan.titleRes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(plan.subtitleRes, plan.formattedExpiry), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.plan_summary_includes), style = MaterialTheme.typography.titleMedium)
            if (plan.plan == SettingsPlan.FREE) {
                val limits = FreePlanLimits()
                Text(stringResource(R.string.plan_summary_instruments, limits.instruments))
                Text(stringResource(R.string.plan_summary_categories, limits.customCategories))
                Text(stringResource(R.string.plan_summary_planning, limits.debts, limits.goals, limits.budgets))
                Text(stringResource(R.string.plan_summary_movements))
            } else {
                Text(stringResource(R.string.plan_summary_premium))
                Text(stringResource(R.string.plan_summary_unlimited))
            }
            onViewOffers?.let { navigate ->
                KipuPrimaryButton(
                    text = stringResource(R.string.plan_choose_action),
                    onClick = { onDismiss(); navigate() },
                    minimumHeight = 48.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    textStyle = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.plan_preference_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
