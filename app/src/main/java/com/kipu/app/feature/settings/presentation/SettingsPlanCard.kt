package com.kipu.app.feature.settings.presentation

import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SettingsPlan { FREE, PREMIUM, DEVELOPER }

/** Account plan display only; capability checks continue to require a verified offline grant. */
data class SettingsPlanCard(
    val plan: SettingsPlan = SettingsPlan.FREE,
    val expiresAt: Instant? = null,
) {
    val clickable: Boolean get() = plan != SettingsPlan.DEVELOPER
    val title: String get() = when (plan) {
        SettingsPlan.FREE -> "Plan Free"
        SettingsPlan.PREMIUM -> "Plan Premium"
        SettingsPlan.DEVELOPER -> "Plan Desarrollador"
    }
    val subtitle: String get() = when (plan) {
        SettingsPlan.FREE -> "Ver qué incluye y mejorar a Premium"
        SettingsPlan.DEVELOPER -> "Todas las funciones Premium, sin vencimiento"
        SettingsPlan.PREMIUM -> expiresAt?.let {
            "Vigencia hasta el ${DATE_FORMAT.format(it.atZone(ZoneId.systemDefault()))}"
        } ?: "Acceso Premium sin vencimiento"
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))

        fun from(access: FeatureAccessCacheEntity?): SettingsPlanCard = when {
            access?.source == "SIGNED_DEVELOPER_GRANT" -> SettingsPlanCard(SettingsPlan.DEVELOPER)
            access?.effectiveTier == "PREMIUM" -> SettingsPlanCard(SettingsPlan.PREMIUM, access.entitlementExpiresAt)
            else -> SettingsPlanCard()
        }
    }
}
