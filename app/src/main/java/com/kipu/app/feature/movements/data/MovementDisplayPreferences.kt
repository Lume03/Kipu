package com.kipu.app.feature.movements.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Device-only, owner-scoped history visibility. Never deletes or rewrites financial data. */
@Singleton
class MovementDisplayPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("movement_display", Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0L)
    fun snapshot(owner: String): Set<String> = preferences.getStringSet(owner, emptySet()).orEmpty().toSet()
    fun observe(owner: String) = changes.map { snapshot(owner) }.distinctUntilChanged()
    suspend fun hide(owner: String, transactionId: String) = withContext(Dispatchers.IO) {
        synchronized(preferences) {
            check(preferences.edit().putStringSet(owner, snapshot(owner) + transactionId).commit()) {
                "No se pudo ocultar el movimiento. Puedes reintentar sin volver a anularlo."
            }
            changes.value += 1
        }
    }
}
