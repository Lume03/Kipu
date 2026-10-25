package com.kipu.app.feature.debts.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.PlanningAction
import com.kipu.app.ui.component.PlanningActionsSheet
import com.kipu.app.ui.component.PlanningActionTone

@Composable
internal fun DebtActionsSheet(
    debt: DebtSummary,
    onDismiss: () -> Unit,
    onDetail: () -> Unit,
    onSettle: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onManage: () -> Unit,
) {
    val payable = debt.obligationType == DebtObligationType.PAYABLE
    PlanningActionsSheet(debt.counterpartyName, onDismiss, buildList {
        if (debt.status == DebtLifecycleStatus.ACTIVE && debt.remainingPrincipalMinor > 0L) {
            add(PlanningAction(if (payable) "Registrar pago" else "Registrar cobro", "Capital e interés por separado",
                Icons.Default.Add, onSettle, PlanningActionTone.PRIMARY, tag = "debt_action_settle"))
        }
        add(PlanningAction("Ver detalle", "Saldo, cuotas e historial", Icons.Default.Visibility, onDetail, tag = "debt_action_detail"))
        add(PlanningAction("Ver historial", "Todos los pagos y ajustes registrados", Icons.Default.History, onHistory, tag = "debt_action_history"))
        if (debt.status == DebtLifecycleStatus.ACTIVE) {
            add(PlanningAction("Editar", "Nombre, vencimiento y nota", Icons.Default.Edit, onEdit, tag = "debt_action_edit"))
            add(PlanningAction("Cerrar deuda", "Resolver el saldo pendiente", Icons.Default.Check, onClose, PlanningActionTone.PRIMARY, tag = "debt_action_close"))
        }
        add(PlanningAction("Eliminar deuda", "Solo sin operaciones relacionadas; conserva tu dinero", Icons.Default.Delete, onManage,
            PlanningActionTone.DESTRUCTIVE, tag = "debt_action_manage"))
    }, Modifier.testTag("debt_actions_sheet"), summary = {
        MoneyText(debt.remainingPrincipalMinor, currency = Currency.fromCode(debt.currencyCode))
    })
}
