package com.kipu.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.rememberPlanningColors

enum class PlanningActionTone { PRIMARY, NEUTRAL, WARNING, DESTRUCTIVE }

data class PlanningAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tone: PlanningActionTone = PlanningActionTone.NEUTRAL,
    val enabled: Boolean = true,
    val tag: String = "",
)

/** Shared visual contract for the M6, budget 6 and D11 action sheets. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PlanningActionsSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: List<PlanningAction>,
    modifier: Modifier = Modifier,
    summary: @Composable () -> Unit = {},
) {
    val colors = rememberPlanningColors()
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surfaceCard,
        footer = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp).testTag("planning_actions_cancel"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.pillTrack, contentColor = colors.primaryText),
            ) { Text("Cancelar") }
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                summary()
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.borderSubtle)
            actions.forEach { action ->
                val (foreground, background) = when (action.tone) {
                    PlanningActionTone.PRIMARY -> colors.primaryDeep to colors.incomeBg
                    PlanningActionTone.NEUTRAL -> colors.secondaryMuted to colors.pillTrack
                    PlanningActionTone.WARNING -> colors.warningText to colors.warningBg
                    PlanningActionTone.DESTRUCTIVE -> colors.expenseText to colors.expenseBg
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(action.tag)
                        .clickable(enabled = action.enabled, role = Role.Button) { onDismiss(); action.onClick() }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = background, modifier = Modifier.size(40.dp)) {
                        Icon(action.icon, null, tint = foreground.copy(alpha = if (action.enabled) 1f else 0.38f), modifier = Modifier.padding(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(action.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                            color = (if (action.tone == PlanningActionTone.DESTRUCTIVE) foreground else colors.primaryText)
                                .copy(alpha = if (action.enabled) 1f else 0.38f))
                        Text(action.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryMuted.copy(alpha = if (action.enabled) 1f else 0.38f))
                    }
                }
            }
        }
    }
}
