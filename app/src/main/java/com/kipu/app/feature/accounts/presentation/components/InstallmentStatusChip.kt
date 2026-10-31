package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.accounts.domain.model.CardInstallmentStatus
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

internal data class InstallmentStatusStyle(
    val container: Color,
    val content: Color,
    val accent: Color,
    val icon: ImageVector,
)

internal fun CardInstallmentStatus.style(colors: CalmEmeraldColors): InstallmentStatusStyle = when (this) {
    CardInstallmentStatus.PAID -> InstallmentStatusStyle(colors.incomeBg, colors.incomeText, colors.incomeEmerald, Icons.Outlined.CheckCircle)
    CardInstallmentStatus.OVERDUE -> InstallmentStatusStyle(colors.expenseBg, colors.expenseText, colors.expenseCoral, Icons.Outlined.ErrorOutline)
    CardInstallmentStatus.UPCOMING -> InstallmentStatusStyle(colors.warningBg, colors.warningText, colors.warningAmber, Icons.Outlined.Schedule)
    CardInstallmentStatus.PENDING -> InstallmentStatusStyle(colors.pillTrack, colors.secondaryMuted, colors.secondaryMuted, Icons.Outlined.HourglassEmpty)
}

/** Status pill: icon + label, so state never relies on colour alone. */
@Composable
fun InstallmentStatusChip(status: CardInstallmentStatus, modifier: Modifier = Modifier) {
    val style = status.style(rememberCalmEmeraldColors())
    val description = stringResource(R.string.credit_installment_status_description, status.label)
    Surface(
        color = style.container,
        shape = RoundedCornerShape(50),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(style.icon, contentDescription = null, tint = style.content, modifier = Modifier.size(14.dp))
            Text(
                status.label,
                color = style.content,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
