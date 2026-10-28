package com.kipu.app.feature.goals.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class GoalDisplayState(val paused: Set<String> = emptySet(), val removed: Set<String> = emptySet())

/** Owner-scoped device preferences; financial goals and their audit events remain untouched. */
@Singleton
class GoalDisplayPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("goal_display", Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0L)
    fun observe(owner: String) = changes.map { read(owner) }.distinctUntilChanged()
    fun snapshot(owner: String) = read(owner)
    private fun read(owner: String) = GoalDisplayState(
        preferences.getStringSet("$owner:paused", emptySet()).orEmpty().toSet(),
        preferences.getStringSet("$owner:removed", emptySet()).orEmpty().toSet(),
    )
    suspend fun pause(owner: String, goal: String, paused: Boolean) = update(owner) {
        it.copy(paused = if (paused) it.paused + goal else it.paused - goal)
    }
    suspend fun remove(owner: String, goal: String) = update(owner) { it.copy(removed = it.removed + goal, paused = it.paused - goal) }
    private suspend fun update(owner: String, change: (GoalDisplayState) -> GoalDisplayState) = withContext(Dispatchers.IO) {
        synchronized(preferences) {
            val state = change(read(owner))
            check(preferences.edit().putStringSet("$owner:paused", state.paused)
                .putStringSet("$owner:removed", state.removed).commit()) { "No se pudo guardar la organización de metas" }
            changes.value += 1
        }
    }
}
