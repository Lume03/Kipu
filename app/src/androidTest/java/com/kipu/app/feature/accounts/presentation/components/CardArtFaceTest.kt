package com.kipu.app.feature.accounts.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders the illustrated BCP Visa Clásica Qore plastic on the device and exports PNGs for visual review. */
class CardArtFaceTest {
    @get:Rule val compose = createComposeRule()

    private val art = CardArtCatalog.BcpVisaClassicQore

    @Test
    fun verifiedBbvaAndScotiaFacesRenderAt200320And420DpWithLegibleDigits() {
        data class Case(val art: CardArt, val back: Boolean, val width: Int)
        val cases = verifiedBankArtIds.flatMap { id ->
            val a = requireNotNull(CardArtCatalog.forPreset(id))
            listOf(200, 320, 420).flatMap { width -> listOf(false, true).map { back -> Case(a, back, width) } }
        }
        val current = mutableStateOf(cases.first())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A local density allows a real 420dp layout to fit this phone without screen clipping.
        // It does not change any device setting, user font setting or saved application state.
        val density = context.resources.displayMetrics.widthPixels / 448f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
                KipuTheme {
                    val c = current.value
                    Column(Modifier.width(c.width.dp)) {
                        CardArtFace(c.art, c.back, "4242", Modifier.testTag("bank-face"))
                    }
                }
            }
        }
        val dir = File(context.getExternalFilesDir(null), "cardart").apply { mkdirs() }
        cases.forEach { c ->
            compose.runOnIdle { current.value = c }
            val face = compose.onNodeWithTag("bank-face").assertIsDisplayed()
            val size = face.fetchSemanticsNode().size
            assertTrue("${c.art.presetId} width ${c.width}", abs(size.width - (c.width * density).roundToInt()) <= 1)
            assertTrue("${c.art.presetId} aspect ratio", abs(size.height - size.width / c.art.aspectRatio) <= 1.5f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("•••• 4242").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            val image = face.captureToImage().asAndroidBitmap()
            val side = if (c.back) "back" else "front"
            File(dir, "${c.art.presetId}_${c.width}_$side.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val layout = layouts.single()
            val diagnostic = "${c.art.presetId} ${c.width} $side digits: size=${layout.size}, " +
                "left=${layout.getLineLeft(0)}, right=${layout.getLineRight(0)}"
            // Auto-sized Text can report didOverflowWidth for subpixel rounding of its IntSize.
            // Check actual line bounds with one physical pixel of tolerance, and real height overflow.
            assertTrue(diagnostic, layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
            assertFalse(diagnostic, layout.didOverflowHeight)
            if (c.back) assertBackDigitsStayInTheSignaturePanel(image, c.art)
        }
    }

    private fun assertBackDigitsStayInTheSignaturePanel(bitmap: Bitmap, art: CardArt) {
        val ink = art.backDigitsColor.toArgb()
        val ys = mutableListOf<Int>()
        // This area contains only dynamic digits and the panel/background, never the magnetic stripe or logos.
        for (y in (bitmap.height * 0.28f).toInt() until (bitmap.height * 0.51f).toInt()) {
            for (x in (bitmap.width * 0.26f).toInt() until (bitmap.width * 0.69f).toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (listOf(16, 8, 0).all { shift -> abs(((pixel shr shift) and 255) - ((ink shr shift) and 255)) <= 5 }) ys += y
            }
        }
        assertTrue("${art.presetId}: missing visible reverse digits", ys.size > 12)
        val top = bitmap.height * 224f / 766f
        val bottom = bitmap.height * 368f / 766f
        assertTrue("${art.presetId}: digits outside signature panel: ${ys.min()}..${ys.max()} vs $top..$bottom", ys.min() >= top - 1 && ys.max() <= bottom + 1)
    }

    @Test
    fun frontAndBackRenderWithDynamicDigitsAndKeepProportions() {
        compose.setContent {
            KipuTheme {
                Column(Modifier.width(360.dp).padding(8.dp)) {
                    CardArtFace(art = art, back = false, lastFourDigits = "4242", modifier = Modifier.testTag("front"))
                    CardArtFace(
                        art = art,
                        back = true,
                        lastFourDigits = "4242",
                        modifier = Modifier.testTag("back"),
                        backInfo = { Text("CICLO DE FACTURACIÓN · corte 15 · pago 5") },
                    )
                }
            }
        }
        compose.onNodeWithTag("front").assertIsDisplayed().assertWidthIsEqualTo(344.dp)
            .assertHeightIsEqualTo((344f / art.aspectRatio).dp)
        compose.onNodeWithTag("back").assertIsDisplayed()
        compose.onNodeWithText("CICLO DE FACTURACIÓN · corte 15 · pago 5").assertIsDisplayed()

        val outDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "cardart").apply { mkdirs() }
        listOf("front", "back").forEach { tag ->
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            File(outDir, "bcp_visa_classic_qore_$tag.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun narrowAndWideWidthsStayProportionalWithoutClippingTheDigits() {
        compose.setContent {
            KipuTheme {
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.width(200.dp).testTag("small")) { CardArtFace(art, back = false, lastFourDigits = "0001") }
                    Column(Modifier.width(420.dp).testTag("large")) { CardArtFace(art, back = true, lastFourDigits = "0001") }
                }
            }
        }
        compose.onNodeWithTag("small").assertIsDisplayed()
        compose.onNodeWithTag("large").assertIsDisplayed()
    }

    @Test
    fun everyCatalogProductRendersFrontAndBackOnDevice() {
        val arts = CardArtCatalog.presetIds.map { CardArtCatalog.forPreset(it)!! }
        val current = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            KipuTheme {
                androidx.compose.foundation.layout.Column(Modifier.width(300.dp)) {
                    val a = arts[current.intValue]
                    CardArtFace(a, back = false, lastFourDigits = "4242", modifier = Modifier.testTag("front"))
                    CardArtFace(a, back = true, lastFourDigits = "4242", modifier = Modifier.testTag("back"))
                }
            }
        }
        val outDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "cardart").apply { mkdirs() }
        File(outDir, "order.txt").writeText(arts.joinToString(separator = ",") { it.presetId })
        arts.indices.forEach { i ->
            current.intValue = i
            compose.waitForIdle()
            listOf("front", "back").forEach { side ->
                compose.onNodeWithTag(side).assertExists()
                val bmp = compose.onNodeWithTag(side).captureToImage().asAndroidBitmap()
                File(outDir, "qore_${i}_$side.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
