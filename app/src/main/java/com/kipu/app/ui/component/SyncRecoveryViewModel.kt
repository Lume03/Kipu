package com.kipu.app.ui.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.sync.SyncRecoveryRepository
import com.kipu.app.core.sync.SyncIssue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SyncRecoveryViewModel @Inject constructor(
    private val repository: SyncRecoveryRepository,
    private val session: SessionCoordinator,
) : ViewModel() {
    val issues = session.localAccess.map { (it as? LocalAccess.Available)?.userId }.distinctUntilChanged()
        .flatMapLatest { if (it == null) flowOf(emptyList()) else repository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun recover(issue: SyncIssue, discard: Boolean) {
        if (_busy.value) return
        val owner = (session.localAccess.value as? LocalAccess.Available)?.userId ?: return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val changed = repository.recover(owner, issue, discard)
                if ((session.localAccess.value as? LocalAccess.Available)?.userId == owner && !changed)
                    _error.value = "No se pudo completar. Tus cambios se conservan; vuelve a intentarlo con conexión."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                if ((session.localAccess.value as? LocalAccess.Available)?.userId == owner)
                    _error.value = "No se pudo completar. Tus cambios se conservan."
            } finally { _busy.value = false }
        }
    }
}
