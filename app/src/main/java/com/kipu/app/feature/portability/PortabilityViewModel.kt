package com.kipu.app.feature.portability

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PortabilityState(
    val busy: Boolean = true,
    val pending: PendingLocalSummary? = null,
    val message: String? = null,
    val restore: RestoreResult? = null,
)

@HiltViewModel
class PortabilityViewModel @Inject constructor(
    private val session: SessionCoordinator,
    private val pendingChanges: PendingLocalChanges,
    private val store: PortabilityStore,
    private val restoreData: RestoreUserData,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PortabilityState())
    val state = mutableState.asStateFlow()
    private val owner = session.currentOwner?.verifiedUserId

    init { refresh() }

    private fun checkedOwner(): String = requireNotNull(owner).also {
        check(session.currentOwner?.verifiedUserId == it)
    }
    fun refresh() = runOperation {
        mutableState.value = mutableState.value.copy(pending = pendingChanges(checkedOwner()))
    }

    fun export(uri: Uri, json: Boolean) = runOperation {
        val currentOwner = checkedOwner()
        mutableState.value = mutableState.value.copy(pending = pendingChanges(currentOwner))
        val content = if (json) store.json(currentOwner) else store.csv(currentOwner)
        withContext(Dispatchers.IO) {
            checkedOwner()
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
                it.write(content)
            }
        }
        checkedOwner()
        mutableState.value = mutableState.value.copy(message = "Exportación guardada.")
    }

    fun restore(retryFailed: Boolean = false) = runOperation {
        val currentOwner = checkedOwner()
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivity != null && connectivity.activeNetwork == null) {
            mutableState.value = mutableState.value.copy(message = "Sin conexión. La restauración necesita Internet; puedes exportar tus datos locales.")
            return@runOperation
        }
        mutableState.value = mutableState.value.copy(pending = pendingChanges(currentOwner))
        val result = restoreData(currentOwner, if (retryFailed) mutableState.value.restore else null)
        checkedOwner()
        mutableState.value = mutableState.value.copy(restore = result, message = when (result.status) {
            RestoreStatus.COMPLETED -> "Restauración completada."
            RestoreStatus.PARTIAL -> "Restauración parcial. Reintenta los módulos fallidos."
            RestoreStatus.ERROR -> "No se completó la restauración. Comprueba tu conexión y reintenta."
        })
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (mutableState.value.busy && mutableState.value.pending != null) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(restore = null, message = "No se pudo completar. Comprueba tu conexión y vuelve a intentarlo.")
            } finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
}
