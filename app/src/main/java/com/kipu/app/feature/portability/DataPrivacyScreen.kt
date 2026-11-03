package com.kipu.app.feature.portability

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPrivacyScreen(
    viewModel: PortabilityViewModel,
    onNavigateBack: () -> Unit,
    accountDeletion: @Composable (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf<String?>(null) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let { uri -> viewModel.export(uri, false) }
    }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let { uri -> viewModel.export(uri, true) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Datos y privacidad") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tus datos están bajo tu control", style = MaterialTheme.typography.titleMedium)
                    Text("Exporta una copia local o recupera los datos de tu cuenta sincronizados en Kipu. La exportación funciona sin conexión; la restauración necesita Internet.")
                    state.pending?.let { pending ->
                        Text("Cambios locales sin sincronizar: ${pending.total}. Estos cambios no están disponibles en el servidor.")
                        pending.byModule.filterValues { it > 0 }.forEach { (module, count) -> Text("${pendingModuleName(module)}: $count", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Button(onClick = { confirmation = "csv" }, enabled = !state.busy && state.pending != null, modifier = Modifier.fillMaxWidth()) { Text("Exportar movimientos en CSV") }
            OutlinedButton(onClick = { confirmation = "json" }, enabled = !state.busy && state.pending != null, modifier = Modifier.fillMaxWidth()) { Text("Exportar todos mis datos en JSON") }
            OutlinedButton(onClick = { confirmation = "restore" }, enabled = !state.busy && state.pending != null, modifier = Modifier.fillMaxWidth()) { Text("Restaurar mis datos") }
            state.restore?.let { result ->
                result.modules.forEach { (name, success) -> Text("$name: ${if (success) "completado" else "fallido, reintentable"}") }
                if (result.status != RestoreStatus.COMPLETED) TextButton(onClick = { viewModel.restore(true) }, enabled = !state.busy) { Text("Reintentar solo los fallidos") }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (!state.busy) TextButton(onClick = viewModel::refresh) { Text("Actualizar pendientes") }
            HorizontalDivider()
            Text("La suscripción se gestiona en Google Play. Restaurar datos no restaura ni concede una suscripción Premium.", style = MaterialTheme.typography.bodyMedium)
            // Integration slot for HU-06 part B; account deletion has its own confirmation and reauthentication.
            accountDeletion?.invoke()
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(if (action == "restore") "Restaurar mis datos" else "Exportar mis datos") },
            text = { Text("Hay ${state.pending?.total ?: 0} cambios locales sin sincronizar. No están en el servidor. La restauración recupera únicamente datos sincronizados; los cambios locales pendientes se conservan.") },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                when (action) { "csv" -> csv.launch("kipu-movimientos.csv"); "json" -> json.launch("kipu-datos.json"); else -> viewModel.restore() }
            }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } },
        )
    }
}

private fun pendingModuleName(queue: String): String = when (queue) {
    "sync_outbox" -> "Preferencias de plan"
    "profile_preference_outbox" -> "Perfil"
    "instrument_sync_outbox" -> "Cuentas y tarjetas"
    "category_sync_outbox" -> "Categorías y comercios"
    "movement_outbox" -> "Movimientos"
    "debt_command_outbox" -> "Deudas"
    "goal_command_outbox" -> "Metas"
    "budget_sync_outbox" -> "Presupuestos"
    "recurrence_command_outbox" -> "Recurrencias"
    else -> "Notificaciones"
}
