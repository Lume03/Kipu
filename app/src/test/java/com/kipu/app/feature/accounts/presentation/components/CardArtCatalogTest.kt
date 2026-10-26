package com.kipu.app.feature.accounts.presentation.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardArtCatalogTest {

    @Test
    fun `art is resolved by the persisted style preset id and only for products that have it`() {
        val art = CardArtCatalog.forPreset("bcp-visa-qore-classic")
        assertNotNull(art)
        assertEquals("BCP Visa Clásica Qore", art!!.productName)
        // Visa Clásica convencional y Visa Clásica Qore son productos distintos
        assertTrue(CardArtCatalog.forPreset("bcp-visa-classic")!!.front != art.front)
        assertEquals("BCP Visa Oro Qore", CardArtCatalog.forPreset("bcp-visa-qore-gold")!!.productName)
        assertNull(CardArtCatalog.forPreset("bbva-start"))
        assertNull(CardArtCatalog.forPreset(null))
        assertNull(CardArtCatalog.forPreset("generic-credit"))
    }

    @Test
    fun `every art preset id exists in the style preset catalog`() {
        CardArtCatalog.presetIds.forEach { id ->
            assertNotNull("Preset $id must exist in CardStylePresets", CardStylePresets.byId(id))
        }
    }

    @Test
    fun `plastic keeps ID-1 proportions and slots stay inside the card`() {
        CardArtCatalog.presetIds.forEach { id ->
            val art = CardArtCatalog.forPreset(id)!!
            assertEquals(1.586f, art.aspectRatio, 0.01f)
            listOf(art.frontDigits, art.backDigits).forEach { slot ->
                assertTrue(slot.endInset in 0f..0.4f)
                assertTrue(slot.top in 0f..0.95f)
                assertTrue(slot.fontFraction in 0.02f..0.06f)
            }
        }
    }

    @Test
    fun `catalog covers BCP, Interbank, Falabella, Ripley and BanBif`() {
        val ids = CardArtCatalog.presetIds
        assertEquals(51, ids.count { !it.startsWith("bbva-") && !it.startsWith("scotiabank-") })
        listOf(
            "bcp-visa-qore-classic", "bcp-visa-qore-infinite", "bcp-visa-latam-iridium", "bcp-amex-latam-black",
            "bcp-debit-classic", "bcp-visa-classic", "bcp-visa-light", "bcp-visa-io",
            "interbank-visa-classic", "interbank-visa-premia", "interbank-amex-the-platinum-card", "interbank-guaranteed",
            "falabella-cmr-visa-signature", "falabella-debit-classic", "ripley-mastercard-classic", "banbif-visa-infinite",
        ).forEach { assertTrue("$it missing", it in ids) }
    }

    @Test
    fun `source svgs are pure vector without embedded rasters or personal data`() {
        val base = listOf("../docs/design/cards", "docs/design/cards").map(::File).firstOrNull { it.isDirectory }
        assertNotNull("SVG source folder not found from ${File(".").absolutePath}", base)
        val svgs = base!!.walkTopDown().filter { it.isFile && it.extension == "svg" }.toList()
        assertEquals("Expected a front and a back SVG per product", CardArtCatalog.presetIds.size * 2, svgs.size)
        svgs.forEach { file ->
            val svg = file.readText()
            val name = file.name
            assertTrue("$name needs a viewBox", "viewBox=\"0 0 1213 766\"" in svg)
            assertFalse("$name must not embed images", "<image" in svg)
            assertFalse("$name must not embed base64", "base64" in svg)
            assertFalse("$name must not depend on fonts", "<text" in svg)
            assertTrue("$name should use gradients", "Gradient" in svg)
            assertFalse("$name must not carry a card number", Regex("""\d{4}[ -]\d{4}[ -]\d{4}[ -]\d{4}""").containsMatchIn(svg))
        }
    }

    @Test
    fun `BBVA and Scotia include only visually verified products`() {
        val expected = bankSources.map { it.first }.toSet()
        assertEquals(expected, CardArtCatalog.presetIds.filter { it.startsWith("bbva-") || it.startsWith("scotiabank-") }.toSet())
        assertNull("Básica has no verified product art", CardArtCatalog.forPreset("bbva-basica"))
        assertNull("Start must not inherit Bfree art", CardArtCatalog.forPreset("bbva-start"))
        bankSources.forEach { (id, _) ->
            val art = CardArtCatalog.forPreset(id)!!
            assertTrue("$id needs different faces", art.front != art.back)
            assertTrue("$id is an illustration", art.isIllustration)
        }
    }

    @Test
    fun `bank resources stay vector only and use supported path commands`() {
        val base = listOf("../app/src/main/res/drawable-nodpi", "src/main/res/drawable-nodpi").map(::File).first { it.isDirectory }
        val svgBase = listOf("../docs/design/cards", "docs/design/cards").map(::File).first { it.isDirectory }
        bankSources.forEach { (id, stem) ->
            listOf("front", "back").forEach { side ->
                assertTrue("$id $side SVG missing", File(svgBase, "$id/$stem${side.replaceFirstChar(Char::uppercase)}.svg").isFile)
                val xml = File(base, "${id.replace('-', '_')}_$side.xml").readText()
                assertFalse("$id must not use Compose-incompatible clipping", "<clip-path" in xml)
                assertFalse("$id must not embed bitmaps", "<bitmap" in xml || "base64" in xml)
                assertTrue("$id has too many draw nodes", Regex("<path ").findAll(xml).count() < 100)
                assertTrue("$id wrong viewport", "android:viewportWidth=\"1213\"" in xml && "android:viewportHeight=\"766\"" in xml)
                Regex("android:pathData=\"([^\"]+)\"").findAll(xml).forEach { match ->
                    assertTrue("$id exceeds the AAPT UTF-8 string limit", match.groupValues[1].toByteArray(Charsets.UTF_8).size < 30_000)
                    val commands = match.groupValues[1].filter(Char::isLetter).toSet()
                    assertTrue("$id uses unsupported commands: $commands", setOf('M', 'L', 'C', 'Z').containsAll(commands))
                }
            }
        }
    }

    private val bankSources = listOf(
        "bbva-debit-classic" to "BbvaDebitClassic", "bbva-visa-bfree" to "BbvaVisaBfree",
        "bbva-mastercard-bfree" to "BbvaMastercardBfree", "bbva-visa-cero" to "BbvaVisaCero",
        "bbva-visa-platinum" to "BbvaVisaPlatinum", "bbva-mastercard-platinum" to "BbvaMastercardPlatinum",
        "bbva-visa-signature" to "BbvaVisaSignature", "bbva-visa-infinite" to "BbvaVisaInfinite",
        "bbva-mastercard-black" to "BbvaMastercardBlack", "scotiabank-debit-classic" to "ScotiabankDebitClassic",
        "scotiabank-singular-signature" to "ScotiabankSingularSignature", "scotiabank-singular-infinite" to "ScotiabankSingularInfinite",
        "scotiabank-singular-black" to "ScotiabankSingularBlack",
    )
}
