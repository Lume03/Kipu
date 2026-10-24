package com.kipu.app.feature.budgets.presentation

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Propietario con acceso local disponible, o null si no hay sesion utilizable. */
internal fun SessionCoordinator.ownerIds(): Flow<String?> =
    localAccess.map { (it as? LocalAccess.Available)?.userId }.distinctUntilChanged()

internal const val SESSION_REQUIRED_MESSAGE = "Inicia sesión para ver tus presupuestos."
