package com.kipu.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.kipu.app.core.sync.SyncModule
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** One shared visual pattern: compact label in lists, notice and optional actions in details. */
@Composable
fun UnsyncedNotice(modifier: Modifier = Modifier, compact: Boolean = false,
    rejectedCreation: Boolean = false, busy: Boolean = false,
    onRetry: (() -> Unit)? = null, onDiscard: (() -> Unit)? = null, error: String? = null) {
    val colors = rememberCalmEmeraldColors()
    if (compact) {
        Text("No sincronizado", modifier, style = MaterialTheme.typography.labelSmall, color = colors.warningText)
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlanningNotice("No sincronizado", if (rejectedCreation) "El servidor rechazó el alta. No se incluye en los saldos."
                else "Hay cambios locales que requieren revisión.")
            onRetry?.let { TextButton(onClick = it, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") } }
            onDiscard?.let { TextButton(onClick = it, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (rejectedCreation) "Eliminar" else "Usar la versión del servidor")
            } }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Presentation binding only; all data work belongs to the ViewModel/repository. */
@Composable
fun EntitySyncNotice(module: SyncModule, entityId: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (LocalInspectionMode.current) return
    val viewModel: SyncRecoveryViewModel = hiltViewModel()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val issue = issues.firstOrNull { it.module == module && (module == SyncModule.PROFILE || it.entityId == entityId) } ?: return
    UnsyncedNotice(modifier, compact, issue.rejectedCreation, busy,
        onRetry = if (issue.retryable) ({ viewModel.recover(issue, false) }) else null,
        onDiscard = if (!issue.retryable) ({ viewModel.recover(issue, true) }) else null, error = error)
}

/** Rejected creations are separate from the normal instrument list and its totals. */
@Composable
fun RejectedInstrumentList(onOpen: (String, Boolean) -> Unit) {
    if (LocalInspectionMode.current) return
    val viewModel: SyncRecoveryViewModel = hiltViewModel()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val rejected = issues.filter { it.rejectedCreation && it.module in setOf(SyncModule.ACCOUNT, SyncModule.CARD) }
    if (rejected.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Requieren revisión", style = MaterialTheme.typography.titleSmall)
        rejected.forEach { issue ->
            TextButton(onClick = { onOpen(issue.entityId, issue.module == SyncModule.CARD) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(issue.title, style = MaterialTheme.typography.bodyMedium)
                    UnsyncedNotice(compact = true)
                }
            }
        }
    }
}
