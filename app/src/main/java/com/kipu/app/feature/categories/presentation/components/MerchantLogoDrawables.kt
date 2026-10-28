package com.kipu.app.feature.categories.presentation.components

import androidx.annotation.DrawableRes
import com.kipu.app.R

/**
 * Bundled Priority A merchant logos keyed by `merchant_services.logo_key`.
 *
 * Static references keep R8 resource shrinking from removing them; a lookup by name
 * (`Resources.getIdentifier`) is invisible to the shrinker.
 */
object MerchantLogoDrawables {
    private val byKey: Map<String, Int> = mapOf(
        "ic_merchant_adobe" to R.drawable.ic_merchant_adobe,
        "ic_merchant_bembos" to R.drawable.ic_merchant_bembos,
        "ic_merchant_bitel" to R.drawable.ic_merchant_bitel,
        "ic_merchant_burger_king" to R.drawable.ic_merchant_burger_king,
        "ic_merchant_cabify" to R.drawable.ic_merchant_cabify,
        "ic_merchant_calidda" to R.drawable.ic_merchant_calidda,
        "ic_merchant_canva" to R.drawable.ic_merchant_canva,
        "ic_merchant_chatgpt" to R.drawable.ic_merchant_chatgpt,
        "ic_merchant_cinemark" to R.drawable.ic_merchant_cinemark,
        "ic_merchant_cineplanet" to R.drawable.ic_merchant_cineplanet,
        "ic_merchant_claro" to R.drawable.ic_merchant_claro,
        "ic_merchant_claude" to R.drawable.ic_merchant_claude,
        "ic_merchant_crunchyroll" to R.drawable.ic_merchant_crunchyroll,
        "ic_merchant_didi" to R.drawable.ic_merchant_didi,
        "ic_merchant_disney_plus" to R.drawable.ic_merchant_disney_plus,
        "ic_merchant_entel" to R.drawable.ic_merchant_entel,
        "ic_merchant_google_ai_pro" to R.drawable.ic_merchant_google_ai_pro,
        "ic_merchant_google_one" to R.drawable.ic_merchant_google_one,
        "ic_merchant_icloud_plus" to R.drawable.ic_merchant_icloud_plus,
        "ic_merchant_indrive" to R.drawable.ic_merchant_indrive,
        "ic_merchant_inkafarma" to R.drawable.ic_merchant_inkafarma,
        "ic_merchant_joinnus" to R.drawable.ic_merchant_joinnus,
        "ic_merchant_kfc" to R.drawable.ic_merchant_kfc,
        "ic_merchant_latam" to R.drawable.ic_merchant_latam,
        "ic_merchant_lima_expresa" to R.drawable.ic_merchant_lima_expresa,
        "ic_merchant_linea_1" to R.drawable.ic_merchant_linea_1,
        "ic_merchant_listo" to R.drawable.ic_merchant_listo,
        "ic_merchant_luz_del_sur" to R.drawable.ic_merchant_luz_del_sur,
        "ic_merchant_mass" to R.drawable.ic_merchant_mass,
        "ic_merchant_max" to R.drawable.ic_merchant_max,
        "ic_merchant_mcdonalds" to R.drawable.ic_merchant_mcdonalds,
        "ic_merchant_metro" to R.drawable.ic_merchant_metro,
        "ic_merchant_metropolitano" to R.drawable.ic_merchant_metropolitano,
        "ic_merchant_microsoft_365" to R.drawable.ic_merchant_microsoft_365,
        "ic_merchant_mifarma" to R.drawable.ic_merchant_mifarma,
        "ic_merchant_movistar" to R.drawable.ic_merchant_movistar,
        "ic_merchant_netflix" to R.drawable.ic_merchant_netflix,
        "ic_merchant_notion" to R.drawable.ic_merchant_notion,
        "ic_merchant_oxxo" to R.drawable.ic_merchant_oxxo,
        "ic_merchant_pedidosya" to R.drawable.ic_merchant_pedidosya,
        "ic_merchant_perplexity" to R.drawable.ic_merchant_perplexity,
        "ic_merchant_petroperu" to R.drawable.ic_merchant_petroperu,
        "ic_merchant_pizza_hut" to R.drawable.ic_merchant_pizza_hut,
        "ic_merchant_playstation" to R.drawable.ic_merchant_playstation,
        "ic_merchant_plaza_vea" to R.drawable.ic_merchant_plaza_vea,
        "ic_merchant_pluz_energia" to R.drawable.ic_merchant_pluz_energia,
        "ic_merchant_primax" to R.drawable.ic_merchant_primax,
        "ic_merchant_prime_video" to R.drawable.ic_merchant_prime_video,
        "ic_merchant_rappi" to R.drawable.ic_merchant_rappi,
        "ic_merchant_repsol" to R.drawable.ic_merchant_repsol,
        "ic_merchant_rutas_de_lima" to R.drawable.ic_merchant_rutas_de_lima,
        "ic_merchant_sedapal" to R.drawable.ic_merchant_sedapal,
        "ic_merchant_spotify" to R.drawable.ic_merchant_spotify,
        "ic_merchant_starbucks" to R.drawable.ic_merchant_starbucks,
        "ic_merchant_steam" to R.drawable.ic_merchant_steam,
        "ic_merchant_tambo" to R.drawable.ic_merchant_tambo,
        "ic_merchant_teleticket" to R.drawable.ic_merchant_teleticket,
        "ic_merchant_tottus" to R.drawable.ic_merchant_tottus,
        "ic_merchant_uber" to R.drawable.ic_merchant_uber,
        "ic_merchant_win" to R.drawable.ic_merchant_win,
        "ic_merchant_wong" to R.drawable.ic_merchant_wong,
        "ic_merchant_xbox" to R.drawable.ic_merchant_xbox,
        "ic_merchant_youtube_premium" to R.drawable.ic_merchant_youtube_premium,
    )

    /** Returns the bundled drawable for [logoKey], or 0 when none is bundled. */
    @DrawableRes
    fun resolve(logoKey: String?): Int = logoKey?.let(byKey::get) ?: 0
}
