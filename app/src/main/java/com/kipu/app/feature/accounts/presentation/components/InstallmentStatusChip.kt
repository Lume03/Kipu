package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.accounts.domain.model.CardInstallmentStatus
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
fun InstallmentStatusChip(status: CardInstallmentStatus, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val (background, foreground) = when (status) {
        CardInstallmentStatus.PAID -> colors.incomeBg to colors.incomeText
        CardInstallmentStatus.OVERDUE -> colors.expenseBg to colors.expenseText
        CardInstallmentStatus.UPCOMING -> colors.warningBg to colors.warningText
        CardInstallmentStatus.PENDING -> colors.pillTrack to colors.secondaryMuted
    }
    val description = stringResource(R.string.credit_installment_status_description, status.label)
    Surface(color = background, shape = RoundedCornerShape(8.dp),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(status.label, color = foreground, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}
