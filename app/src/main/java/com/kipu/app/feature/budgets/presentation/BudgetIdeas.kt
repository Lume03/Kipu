package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.PastelTone
import com.kipu.app.ui.theme.at
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.theme.rememberPastelTones
import java.text.Normalizer
import java.util.Locale

/**
 * Starting points offered when there are no budgets yet. Choosing one only prefills the creation form with a
 * name, a suggested monthly limit and the matching expense category; nothing is saved until the person confirms.
 */
internal enum class BudgetIdea(
    val key: String,
    val title: String,
    val limitMajor: Int,
    val budgetName: String,
    private val categoryKeyword: String,
    val icon: ImageVector,
    private val toneIndex: Int,
) {
    FOOD("food", "Alimentación y supermercado", 500, "Alimentación", "alimenta", Icons.Default.Restaurant, 5),
    TRANSPORT("transport", "Transporte y gasolina", 250, "Transporte", "transporte", Icons.Default.DirectionsCar, 1),
    LEISURE("leisure", "Ocio y salidas", 200, "Ocio", "ocio", Icons.Default.SportsEsports, 3);

    val suggestion: String get() = "S/ $limitMajor / mes sugerido"

    fun tone(tones: List<PastelTone>) = tones.at(toneIndex)

    /** Prefilled form values; the category is only chosen when one of the person's categories clearly matches. */
    fun toInput(categories: List<BudgetCategoryOption>): BudgetFormInput {
        val match = categories.filter { normalize(it.name).contains(categoryKeyword) }
            .let { found -> found.firstOrNull { it.parentId == null } ?: found.firstOrNull() }
        return BudgetFormInput(
            name = budgetName,
            limitText = limitMajor.toString(),
            scopeKind = if (match != null) BudgetScopeKind.CATEGORY else BudgetScopeKind.TOTAL,
            categoryId = match?.id,
            iconId = match?.iconId,
        )
    }

    companion object {
        fun fromKey(key: String?): BudgetIdea? = entries.firstOrNull { it.key == key }
        private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "").lowercase(Locale.ROOT)
    }
}

@Composable
internal fun BudgetIdeasSection(onPick: (BudgetIdea) -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val tones = rememberPastelTones()
    Column(modifier.fillMaxWidth().testTag("budget_ideas"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(16.dp))
            Text(
                "Ideas para empezar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = colors.secondaryMuted, modifier = Modifier.semantics { heading() },
            )
        }
        BudgetIdea.entries.forEach { idea ->
            val tone = idea.tone(tones)
            Surface(
                onClick = { onPick(idea) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("budget_idea_${idea.key}"),
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(color = tone.tile, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(idea.icon, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(idea.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                        Text(idea.suggestion, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.primaryDeep)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp), color = colors.incomeBg, border = BorderStroke(1.dp, colors.incomeBorder),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("+ Añadir", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.incomeText)
                        }
                    }
                }
            }
        }
    }
}
