package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Observes the local profile only while the verified owner can access their data. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardProfileViewModel @Inject constructor(
    sessionCoordinator: SessionCoordinator,
    repository: ProfilePreferencesRepository,
) : ViewModel() {
    val displayName = sessionCoordinator.localAccess.flatMapLatest { access ->
        val userId = (access as? LocalAccess.Available)?.userId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (userId == null) flowOf<String?>(null)
        else repository.observeProfile(userId)
            .map { profile -> profile?.displayName?.trim()?.takeIf { it.isNotEmpty() } }
            .onStart { emit(null) }
            .catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), null)
}

internal fun dashboardInitials(displayName: String?): String? = displayName
    ?.trim()
    ?.split(Regex("\\s+"))
    ?.filter { it.isNotEmpty() }
    ?.take(2)
    ?.joinToString("") { it.take(1).uppercase(java.util.Locale.ROOT) }
    ?.takeIf { it.isNotEmpty() }
