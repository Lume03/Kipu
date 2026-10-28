package com.kipu.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.rememberPlanningColors

@Composable
fun PlanningNotice(title: String, message: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val colors = rememberPlanningColors()
    val foreground = if (isError) colors.expenseText else colors.primaryDeep
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = if (isError) colors.expenseBg else colors.incomeBg,
        border = BorderStroke(1.dp, if (isError) colors.expenseBorder else colors.incomeBorder),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (isError) Icons.Outlined.WarningAmber else Icons.Outlined.Info, null,
                tint = foreground, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = foreground)
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.primaryText)
            }
        }
    }
}
