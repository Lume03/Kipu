package com.kipu.app.core.storage.data

import android.net.Uri

object MerchantLogoUrlResolver {
    private const val MERCHANT_LOGO_BUCKET = "merchant-logos"

    fun resolve(supabaseUrl: String, logoKey: String?): String? {
        val baseUrl = supabaseUrl.trimEnd('/')
        val key = logoKey?.takeIf(String::isNotBlank) ?: return null
        if (baseUrl.isBlank()) return null

        return baseUrl +
            "/storage/v1/object/public/$MERCHANT_LOGO_BUCKET/" +
            Uri.encode(key, "/")
    }
}
