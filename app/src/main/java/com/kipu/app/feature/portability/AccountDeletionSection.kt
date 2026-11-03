package com.kipu.app.feature.portability

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountDeletionSection(viewModel: AccountDeletionViewModel, onCompleted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LaunchedEffect(state.request?.local, state.busy) {
        if (state.request?.local == DeletionLocal.CLEARED && !state.busy) onCompleted()
    }
    OutlinedButton(onClick = viewModel::open, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
    }
    state.request?.takeUnless { it.local == DeletionLocal.CLEARED }?.let { request ->
        Text(remoteDescription(request.remote))
        Text(localDescription(request.local))
    }
    if (state.step != DeletionStep.CLOSED) {
        var password by remember(state.step) { mutableStateOf("") }
        var email by remember(state.step) { mutableStateOf("") }
        var acceptsLoss by remember(state.step) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!state.busy) viewModel.cancel() },
            title = { Text(when (state.step) {
                DeletionStep.REAUTH -> "Verifica tu identidad"
                DeletionStep.CONFIRM -> "Confirmar eliminación"
                DeletionStep.STATUS -> "Estado de eliminación"
                else -> "Eliminar mi cuenta"
            }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    when (state.step) {
                        DeletionStep.SUMMARY -> {
                            Text("Se eliminarán tu perfil, movimientos, cuentas, tarjetas, metas, deudas, presupuestos y demás datos personales de Kipu. Esta acción no se puede deshacer.")
                            Text("Hay ${state.pending} cambios locales sin sincronizar. Al completarse la eliminación remota también se borrarán de este dispositivo. Puedes cancelar y exportar una copia antes de continuar.")
                            Text("Eliminar tu cuenta NO cancela automáticamente la suscripción de Google Play. Abre Play Store > Pagos y suscripciones > Suscripciones para gestionarla.")
                            TextButton(onClick = { uri.openUri("https://play.google.com/store/account/subscriptions") }) { Text("Gestionar suscripción en Google Play") }
                        }
                        DeletionStep.REAUTH -> {
                            Text("Introduce la contraseña del correo con el que inicias sesión en Kipu. Necesitas conexión a Internet.")
                            if (viewModel.recoveryMode) OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo de la cuenta") }, singleLine = true, enabled = !state.busy)
                            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña actual") },
                                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy)
                        }
                        DeletionStep.CONFIRM -> {
                            Text("La eliminación se iniciará únicamente al pulsar Eliminar definitivamente. Los cambios locales no sincronizados también se perderán cuando finalice.")
                            Row {
                                Checkbox(checked = acceptsLoss, onCheckedChange = { acceptsLoss = it }, enabled = !state.busy)
                                Text("Entiendo que perderé mis datos y confirmo que quiero eliminar mi cuenta.", modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        DeletionStep.STATUS -> {
                            state.request?.let { Text(remoteDescription(it.remote)); Text(localDescription(it.local)) }
                            Text("Si la respuesta se interrumpe, la solicitud queda guardada. Reintentar consulta y continúa la misma eliminación. Cerrar este diálogo no revierte una solicitud ya enviada.")
                        }
                        else -> Unit
                    }
                    state.message?.let { Text(it) }
                }
            },
            confirmButton = {
                when (state.step) {
                    DeletionStep.SUMMARY -> TextButton(onClick = viewModel::continueToReauthentication, enabled = !state.busy) { Text("Continuar") }
                    DeletionStep.REAUTH -> TextButton(onClick = { viewModel.reauthenticate(password, email); password = "" }, enabled = !state.busy && password.isNotEmpty() && (!viewModel.recoveryMode || email.isNotBlank())) { Text("Verificar") }
                    DeletionStep.CONFIRM -> TextButton(onClick = viewModel::confirm, enabled = !state.busy && acceptsLoss) { Text("Eliminar definitivamente", color = MaterialTheme.colorScheme.error) }
                    DeletionStep.STATUS -> Column {
                        TextButton(onClick = viewModel::retry, enabled = !state.busy) { Text("Consultar y reintentar") }
                        if (state.request?.remote != DeletionRemote.COMPLETED) TextButton(onClick = viewModel::continueToReauthentication, enabled = !state.busy) { Text("Volver a verificar identidad") }
                    }
                    else -> Unit
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancel, enabled = !state.busy) { Text(if (state.step == DeletionStep.STATUS) "Cerrar" else "Cancelar") } },
        )
    }
}

private fun remoteDescription(status: DeletionRemote) = when (status) {
    DeletionRemote.PENDING -> "Servidor: eliminación pendiente; Auth puede requerir un reintento."
    DeletionRemote.ERROR -> "Servidor: resultado no confirmado o error reintentable. No se afirma que haya terminado."
    DeletionRemote.COMPLETED -> "Servidor: cuenta y datos eliminados."
}
private fun localDescription(status: DeletionLocal) = when (status) {
    DeletionLocal.RETAINED -> "Dispositivo: los datos locales se conservan, incluidos los no sincronizados."
    DeletionLocal.PENDING -> "Dispositivo: limpieza pendiente. Reintenta para completarla."
    DeletionLocal.SESSION_PENDING -> "Dispositivo: datos borrados; el cierre de sesión sigue pendiente. Reintenta."
    DeletionLocal.CLEARED -> "Dispositivo: datos y sesión eliminados."
}
