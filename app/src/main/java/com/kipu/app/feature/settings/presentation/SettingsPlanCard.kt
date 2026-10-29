package com.kipu.app.feature.settings.presentation

import androidx.annotation.StringRes
import com.kipu.app.R
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
    val titleRes: Int
        @StringRes get() = when (plan) {
        SettingsPlan.FREE -> R.string.plan_free_title
        SettingsPlan.PREMIUM -> R.string.plan_settings_premium_title
        SettingsPlan.DEVELOPER -> R.string.plan_settings_developer_title
    }
    val subtitleRes: Int
        @StringRes get() = when (plan) {
        SettingsPlan.FREE -> R.string.plan_settings_free_subtitle
        SettingsPlan.DEVELOPER -> R.string.plan_settings_developer_subtitle
        SettingsPlan.PREMIUM -> if (expiresAt != null) R.string.plan_settings_expires else R.string.plan_settings_no_expiry
    }
    val formattedExpiry: String get() = expiresAt?.let { DATE_FORMAT.format(it.atZone(ZoneId.systemDefault())) }.orEmpty()

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))

        fun from(access: FeatureAccessCacheEntity?): SettingsPlanCard = when {
            access?.source == "SIGNED_DEVELOPER_GRANT" -> SettingsPlanCard(SettingsPlan.DEVELOPER)
            access?.effectiveTier == "PREMIUM" -> SettingsPlanCard(SettingsPlan.PREMIUM, access.entitlementExpiresAt)
            else -> SettingsPlanCard()
        }
    }
}
