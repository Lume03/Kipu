package com.kipu.app.feature.budgets.presentation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local presentation preferences; never part of budget accounting or remote commands. */
@Singleton
class BudgetAppearanceStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("budget_appearance_v1", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    fun icon(ownerId: String, budgetId: String): String? = preferences.getString("$ownerId:$budgetId:icon", null)

    fun saveIcon(ownerId: String, budgetId: String, iconId: String?) {
        preferences.edit().putString("$ownerId:$budgetId:icon", iconId).apply()
        revision.value += 1
    }
}
