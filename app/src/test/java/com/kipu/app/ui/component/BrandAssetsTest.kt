package com.kipu.app.ui.component

import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetsTest {
    @Test
    fun everyBankAndWalletPresetResolvesToABrand() {
        AccountPreset.entries
            .filter { it != AccountPreset.CASH && it != AccountPreset.GENERIC }
            .forEach { preset -> assertNotNull(preset.name, KipuBrand.from(preset.name)) }
    }

    @Test
    fun everyCardCatalogInstitutionResolvesToABrand() {
        CardStylePresets.all
            .map { it.institutionCode }
            .filter { it != "KIPU" }
            .distinct()
            .forEach { code -> assertNotNull(code, KipuBrand.from(code)) }
    }

    @Test
    fun brandLookupIgnoresAccentsCaseAndUnknowns() {
        assertEquals(KipuBrand.BANCO_NACION, KipuBrand.from("Banco de la Nación"))
        assertEquals(KipuBrand.FALABELLA, KipuBrand.from("BANCO_FALABELLA"))
        assertEquals(KipuBrand.YAPE, KipuBrand.from("yape"))
        assertNull(KipuBrand.from("Caja Arequipa"))
        assertNull(KipuBrand.from(""))
        assertNull(KipuBrand.from(null))
    }

    @Test
    fun fallbackInitialsUseTheFirstTwoWords() {
        assertEquals("CA", fallbackInitials("Caja Arequipa"))
        assertEquals("MI", fallbackInitials("mibanco"))
        assertEquals("?", fallbackInitials("  "))
    }

    @Test
    fun cardArtworkOnlyCoversExistingBcpPresets() {
        assertEquals(17, CardArtwork.presetIds.size)
        CardArtwork.presetIds.forEach { id ->
            val preset = CardStylePresets.byId(id)
            assertNotNull(id, preset)
            assertEquals("BCP", preset!!.institutionCode)
        }
        assertNull(CardArtwork.forPreset("bcp-visa-io"))
        assertNull(CardArtwork.forPreset(null))
    }

    @Test
    fun usageLevelsFollowThe50_80_100Scale() {
        assertEquals(UsageLevel.NORMAL, UsageLevel.forPercent(49.9))
        assertEquals(UsageLevel.ATTENTION, UsageLevel.forPercent(50.0))
        assertEquals(UsageLevel.NEAR_LIMIT, UsageLevel.forPercent(80.0))
        assertEquals(UsageLevel.EXCEEDED, UsageLevel.forPercent(100.0))
        assertTrue(UsageLevel.entries.map { it.label }.distinct().size == UsageLevel.entries.size)
    }
}
