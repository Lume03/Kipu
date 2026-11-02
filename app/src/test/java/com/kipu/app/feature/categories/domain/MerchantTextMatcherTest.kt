package com.kipu.app.feature.categories.domain

import com.kipu.app.feature.categories.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MerchantTextMatcherTest {
    private val catalog = listOf("Tambo", "Rappi", "Steam", "Betano", "Conamu", "Royal", "Yape", "Nacion", "Pago", "Cuenta").map {
        MerchantCatalogEntry(MerchantId(it.lowercase()), it, it.lowercase())
    }
    @Test fun `bank descriptions produce only unambiguous merchant suggestions`() {
        val cases = mapOf("DLC*RAPPI PERU" to "rappi", "YQ-TAMBO BARRERA-R" to "tambo",
            "YQ-T7185 ROYAL PLA" to "royal", "YP APUESTAS-BETANO" to "betano",
            "STEAMGAMES.COM 425" to "steam", "YQ-BCP*CONAMU" to "conamu",
            "PAGO000402141223" to null, "Yape Doris Luq" to null, "PLIN-OSCAR ALEXIS" to null,
            "TRANSF.BCO.NACION" to null, "DEPOSITO AG 980456" to null, "MANT. CUENTA AGO26" to null,
            "IZI*TAMBO 1234" to "tambo", "RAPPITAMBO" to null, "Tambo Rappi" to null,
            "PAYU*RAPPI*123" to "rappi", "Sin comercio" to null)
        cases.forEach { (raw, expected) -> assertEquals(raw, expected, MerchantTextMatcher.suggest(raw, catalog)?.id?.value) }
        assertNull(MerchantTextMatcher.suggest("Tambo", catalog.map { it.copy(isActive = false) }))
    }
}
