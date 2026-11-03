package com.kipu.app.feature.capture.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.capture.data.local.RoomCaptureCorrelation
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class CaptureMergeViewModel @Inject constructor(
    private val correlation: RoomCaptureCorrelation,
    private val session: SessionCoordinator,
) : ViewModel() {
    var target by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    fun load(owner: String, root: String) { target = null; viewModelScope.launch {
        target = if ((session.localAccess.value as? LocalAccess.Available)?.userId == owner)
            correlation.suggestion(owner, root) else null
    } }
    fun separate(owner: String, root: String) { viewModelScope.launch {
        if ((session.localAccess.value as? LocalAccess.Available)?.userId != owner) return@launch
        correlation.separate(owner, root); target = null
    } }
    fun merge(owner: String, root: String, onMerged: () -> Unit) { viewModelScope.launch {
        if (busy || (session.localAccess.value as? LocalAccess.Available)?.userId != owner) return@launch
        val destination = target ?: return@launch
        busy = true
        try { if (correlation.merge(owner, root, destination)) { target = null; onMerged() } else load(owner, root) } finally { busy = false }
    } }
}

@Composable
fun CaptureMergeSuggestion(owner: String, root: String, onMerged: () -> Unit = {},
    viewModel: CaptureMergeViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    LaunchedEffect(owner, root) { viewModel.load(owner, root) }
    val target = viewModel.target ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Posible coincidencia", style = MaterialTheme.typography.titleSmall)
            Text("Coinciden monto, moneda, dirección, contraparte y hora. Revisa antes de unir las evidencias.")
            Button(onClick = { viewModel.merge(owner, root, onMerged) }, enabled = !viewModel.busy) {
                Text("Unir con movimiento ${target.substringAfter(':')}")
            }
            TextButton(onClick = { viewModel.separate(owner, root) }, enabled = !viewModel.busy) { Text("Mantener separado") }
        }
    }
}
