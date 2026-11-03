package com.kipu.app.feature.portability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AccountDeletionViewModel @Inject constructor(
    store: DataStoreDeletionRequests,
    private val gateway: RemoteAccountDeletion,
    cleanup: LocalAccountDeletionCleanup,
    session: SessionCoordinator,
    pending: PendingLocalChanges,
) : ViewModel() {
    val recoveryMode = session.currentOwner == null
    private var recoveryOwner: String? = null
    private val deletion = AccountDeletion(store, gateway, cleanup, { session.currentOwner?.verifiedUserId ?: recoveryOwner }, {
        if (session.currentOwner?.verifiedUserId == it) pending(it).total else 0
    })
    val state = deletion.state
    init { viewModelScope.launch {
        if (recoveryMode) recoveryOwner = store.unfinished().firstOrNull()?.owner
        deletion.load()
    } }
    fun open() { viewModelScope.launch { deletion.open() } }
    fun cancel() = deletion.cancel()
    fun continueToReauthentication() = deletion.continueToReauthentication()
    fun reauthenticate(password: String, email: String = "") { gateway.recoveryEmail(email); viewModelScope.launch { deletion.reauthenticate(password) } }
    fun confirm() { viewModelScope.launch { deletion.confirm() } }
    fun retry() { viewModelScope.launch { deletion.retry() } }
}
