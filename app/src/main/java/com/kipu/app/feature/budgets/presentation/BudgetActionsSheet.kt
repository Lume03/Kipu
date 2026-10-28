package com.kipu.app.feature.budgets.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kipu.app.ui.component.PlanningAction
import com.kipu.app.ui.component.PlanningActionTone
import com.kipu.app.ui.component.PlanningActionsSheet

@Composable
internal fun BudgetActionsSheet(
    name: String,
    active: Boolean,
    onDismiss: () -> Unit,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    summary: @Composable () -> Unit = {},
) {
    PlanningActionsSheet(name, onDismiss, buildList {
        if (active) {
            add(PlanningAction("Editar", "Nombre, límite, periodo y alertas", Icons.Default.Edit, onEdit))
            add(PlanningAction("Finalizar", "Conserva el historial; detiene renovaciones y alertas", Icons.Default.Flag, onFinish, PlanningActionTone.WARNING))
        }
        add(PlanningAction("Eliminar", "No afecta movimientos ni cuentas", Icons.Default.Delete, onDelete,
            PlanningActionTone.DESTRUCTIVE, tag = "budget_sheet_delete"))
    }, Modifier.testTag("budget_actions_sheet"), summary)
}
