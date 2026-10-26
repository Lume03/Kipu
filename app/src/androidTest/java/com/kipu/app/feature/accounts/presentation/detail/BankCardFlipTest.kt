package com.kipu.app.feature.accounts.presentation.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.presentation.components.CardArtCatalog
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.CardStyleType
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.feature.accounts.presentation.components.verifiedBankArtIds
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Real detail composable and preview, with ephemeral fixtures only. No saved user cards. */
class BankCardFlipTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(presetId: String): CreditCard {
        val preset = requireNotNull(CardStylePresets.byId(presetId))
        return CreditCard(
            id = CardId.generate(), userId = UserId.generate(), alias = preset.productName,
            issuer = preset.institutionCode,
            network = CardNetwork.entries.firstOrNull { it.name == preset.network } ?: CardNetwork.OTHER,
            lastFourDigits = "4242", currency = Currency.PEN, creditLimitMinorUnits = 100_000,
            billingDay = 15, dueDay = 5, stylePresetId = presetId,
        )
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "cardart").apply { mkdirs() }
        val bmp = compose.onNodeWithTag("bank-host").captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun allVerifiedCreditProductsFlipAndReturnToTheirOwnFront() {
        val cards = verifiedBankArtIds.filter { CardStylePresets.byId(it)!!.cardType == CardStyleType.CREDIT }.map(::fixture)
        val current = mutableStateOf(cards.first())
        compose.setContent {
            KipuTheme {
                Column(Modifier.padding(16.dp).testTag("bank-host")) {
                    key(current.value.stylePresetId) { Kibo3DCreditCard(current.value) }
                }
            }
        }
        cards.forEach { card ->
            compose.runOnIdle { current.value = card }
            compose.onNodeWithText("Toca la tarjeta para voltear").assertIsDisplayed()
            shot("${card.stylePresetId}_detail_front")
            compose.onNodeWithContentDescription("Tarjeta de crédito", substring = true).performClick()
            compose.onNodeWithText("Toca la tarjeta para ver el frente").assertIsDisplayed()
            compose.onNodeWithText("CICLO DE FACTURACIÓN").assertIsDisplayed()
            shot("${card.stylePresetId}_detail_back")
            compose.onNodeWithContentDescription("Reverso de tarjeta de crédito", substring = true).performClick()
            compose.onNodeWithText("Toca la tarjeta para voltear").assertIsDisplayed()
            compose.onNodeWithText("•••• 4242").assertIsDisplayed()
        }
    }

    @Test
    fun unverifiedBasicsAndStartRetainGenericFrontAndReverse() {
        val current = mutableStateOf(fixture("bbva-basica"))
        compose.setContent {
            KipuTheme {
                Column(Modifier.padding(16.dp).testTag("bank-host")) {
                    key(current.value.stylePresetId) { Kibo3DCreditCard(current.value) }
                }
            }
        }
        listOf("bbva-basica", "bbva-start").forEach { id ->
            assertNull(CardArtCatalog.forPreset(id))
            compose.runOnIdle { current.value = fixture(id) }
            compose.onNodeWithText("CORTE: Día 15   PAGO: Día 05").assertIsDisplayed()
            shot("${id}_generic_front")
            compose.onNodeWithContentDescription("Tarjeta de crédito", substring = true).performClick()
            compose.onNodeWithText("Toca la tarjeta para ver el frente").assertIsDisplayed()
            shot("${id}_generic_back")
        }
    }

    @Test
    fun previewsResolveThePersistedArtForDebitAndCreditFixtures() {
        val current = mutableStateOf(verifiedBankArtIds.first())
        compose.setContent {
            KipuTheme {
                Column(Modifier.padding(16.dp).testTag("bank-host")) {
                    val preset = requireNotNull(CardStylePresets.byId(current.value))
                    InstrumentCardPreview(
                        title = preset.productName, instrumentType = if (preset.cardType == CardStyleType.DEBIT) "Débito" else "Crédito",
                        subtitle = preset.institutionCode, lastFourDigits = "4242", network = preset.network, stylePreset = preset,
                    )
                }
            }
        }
        verifiedBankArtIds.forEach { id ->
            compose.runOnIdle { current.value = id }
            compose.onNodeWithText("•••• 4242").assertIsDisplayed()
            shot("${id}_preview")
        }
    }
}
