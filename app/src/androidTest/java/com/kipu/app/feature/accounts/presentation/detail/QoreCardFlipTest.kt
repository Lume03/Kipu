package com.kipu.app.feature.accounts.presentation.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Detail-screen card with the illustrated Qore plastic: front, flip to the back and back to the front. */
class QoreCardFlipTest {
    @get:Rule val compose = createComposeRule()

    private fun card(preset: String?) = CreditCard(
        id = CardId.generate(),
        userId = UserId.generate(),
        alias = "VISA",
        issuer = "BCP",
        network = CardNetwork.VISA,
        lastFourDigits = "4242",
        currency = Currency.PEN,
        creditLimitMinorUnits = 100_000L,
        billingDay = 15,
        dueDay = 5,
        stylePresetId = preset,
    )

    private fun shot(tag: String, name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "cardart").apply { mkdirs() }
        val bmp = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun qoreCardFlipsBetweenIllustratedFrontAndBack() {
        compose.setContent {
            KipuTheme { Column(Modifier.padding(16.dp).testTag("host")) { Kibo3DCreditCard(card = card("bcp-visa-qore-classic")) } }
        }
        compose.onNodeWithText("Toca la tarjeta para voltear").assertIsDisplayed()
        shot("host", "detail_front")
        compose.onNodeWithContentDescription("Tarjeta de crédito", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Toca la tarjeta para ver el frente").assertIsDisplayed()
        compose.onNodeWithText("CICLO DE FACTURACIÓN").assertIsDisplayed()
        shot("host", "detail_back")
        compose.onNodeWithContentDescription("Reverso de tarjeta de crédito", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Toca la tarjeta para voltear").assertIsDisplayed()
    }

    @Test
    fun cardsWithoutIllustratedArtKeepTheGenericStyle() {
        compose.setContent {
            KipuTheme { Column(Modifier.padding(16.dp).testTag("host")) { Kibo3DCreditCard(card = card("bbva-start")) } }
        }
        compose.onNodeWithText("Toca la tarjeta para voltear").assertIsDisplayed()
        compose.onNodeWithText("CORTE: Día 15   PAGO: Día 05").assertIsDisplayed()
    }
}
