package com.kipu.app.feature.accounts.presentation.instruments

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RateCatalogResponsiveLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun comparisonHeaderKeepsCompleteChipWithAndWithoutPersonalTeaAtLargeFontScales() {
        var fontScale by mutableFloatStateOf(1f)
        var personalTeaBps by mutableStateOf<Int?>(null)
        compose.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = 400 }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalConfiguration provides configuration,
            ) {
                KipuTheme {
                    RateCatalogScreen(
                        creditCard = fixtureCard(personalTeaBps),
                        products = listOf(fixtureProduct()),
                        catalogError = null,
                        catalogLoading = false,
                        cardLoading = false,
                        events = emptyFlow(),
                        onLoadCatalog = {},
                        onUpdatePersonalTea = { _, _ -> },
                        onNavigateBack = {},
                    )
                }
            }
        }

        listOf(1f, 1.3f, 2f).forEach { scale ->
            listOf(null, 6_000).forEach { teaBps ->
                compose.runOnIdle {
                    fontScale = scale
                    personalTeaBps = teaBps
                }
                compose.waitForIdle()
                val expectedChip = if (teaBps == null) "Sin TEA personal" else "-25% del máximo"

                val list = compose.onNodeWithTag("rate_catalog_list")
                list.performScrollToNode(hasText("Comparativa vs. Tarifario Oficial"))
                compose.onNodeWithText("Comparativa vs. Tarifario Oficial").assertIsDisplayed()
                list.performScrollToNode(hasText(expectedChip))
                compose.onNodeWithText(expectedChip).assertIsDisplayed()
                captureFixture("catalog-header-$scale-${teaBps ?: 0}")
                if (teaBps != null) {
                    list.performScrollToNode(hasText("Tasa máxima SBS"))
                    val maximumLabel = compose.onNodeWithText("Tasa máxima SBS").getUnclippedBoundsInRoot()
                    org.junit.Assert.assertTrue(maximumLabel.right - maximumLabel.left > maximumLabel.bottom - maximumLabel.top)
                    captureFixture("catalog-track-$scale-${teaBps ?: 0}")
                }
                list.performScrollToNode(hasText("Ejemplo SBS"))
                val exampleChip = compose.onNodeWithText("Ejemplo SBS").getUnclippedBoundsInRoot()
                org.junit.Assert.assertTrue(exampleChip.right - exampleChip.left > exampleChip.bottom - exampleChip.top)
                captureFixture("catalog-technical-$scale-${teaBps ?: 0}")
            }
        }
    }

    @Test
    fun annualMembershipFeeFollowsHideAmountsPreference() {
        var masked by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalBalanceMasked provides masked) {
                KipuTheme {
                    RateCatalogScreen(
                        creditCard = fixtureCard(personalTeaBps = null),
                        products = listOf(fixtureProduct()),
                        catalogError = null,
                        catalogLoading = false,
                        cardLoading = false,
                        events = emptyFlow(),
                        onLoadCatalog = {},
                        onUpdatePersonalTea = { _, _ -> },
                        onNavigateBack = {},
                    )
                }
            }
        }
        listOf(false, true).forEach { isMasked ->
            compose.runOnIdle { masked = isMasked }
            compose.waitForIdle()
            compose.onNodeWithTag("rate_catalog_list").performScrollToNode(hasText("Membresía anual"))
            compose.onNodeWithText("Membresía anual").assertIsDisplayed()
            captureFixture("membership-masked-$isMasked")

            if (isMasked) {
                compose.onAllNodesWithText("100.00", substring = true).assertCountEquals(0)
                compose.onAllNodesWithText("999.99", substring = true).assertCountEquals(0)
            } else {
                compose.onNodeWithText("100.00", substring = true).assertIsDisplayed()
            }
        }
    }

    @Test
    fun addFormTopBarDefinesOneTitleAndOneBackActionAtNormalAndCompactScales() {
        var fontScale by mutableFloatStateOf(1f)
        var backCalls = 0
        compose.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = 400 }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalConfiguration provides configuration,
            ) {
                KipuTheme {
                    AddFormTopBar(
                        title = "Agregar tarjeta de crédito",
                        compactTitle = "Tarjeta de crédito",
                        onBack = { backCalls++ },
                    )
                }
            }
        }

        listOf(1f, 1.3f, 2f).forEach { scale ->
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            val expectedTitle = if (scale >= 1.3f) "Tarjeta de crédito" else "Agregar tarjeta de crédito"
            compose.onAllNodesWithText(expectedTitle).assertCountEquals(1)
            val previousBackCalls = backCalls
            compose.onNodeWithContentDescription("Volver").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(previousBackCalls + 1, backCalls) }
        }
    }

    @Test
    fun initialBalanceLabelStacksWithoutSqueezingAt200Percent() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                KipuTheme {
                    Box(Modifier.width(320.dp)) {
                        AddFormLabel("Saldo inicial", "Opcional por defecto 0")
                    }
                }
            }
        }
        val label = compose.onNodeWithText("Saldo inicial").getUnclippedBoundsInRoot()
        val tag = compose.onNodeWithText("Opcional por defecto 0").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(tag.top >= label.bottom)
        captureFixture("initial-balance-label-200")
    }

    private fun captureFixture(name: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "qa95-$name.png")
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun fixtureCard(personalTeaBps: Int?) = CreditCard(
        id = CardId("a2000000-0000-4000-8000-000000000001"),
        userId = UserId("a2000000-0000-4000-8000-000000000002"),
        alias = "Amex QA ficticia",
        issuer = "BCP",
        network = CardNetwork.AMEX,
        lastFourDigits = "4242",
        currency = Currency.PEN,
        creditLimitMinorUnits = 100_000L,
        billingDay = 10,
        dueDay = 20,
        personalTeaBps = personalTeaBps,
        stylePresetId = "bcp-amex-latam-gold",
    )

    private fun fixtureProduct() = CreditProductReference(
        id = "fixture-bcp-amex-gold",
        institutionCode = "BCP",
        institutionName = "BCP",
        productName = "American Express Oro LATAM Pass",
        cardNetwork = "AMEX",
        penTeaMinBps = 5_000,
        penTeaMaxBps = 8_000,
        usdTeaMinBps = null,
        usdTeaMaxBps = null,
        publishedTeaSummary = "Referencia ficticia para pruebas",
        publishedTceaSummary = "Referencia ficticia para pruebas",
        membershipFeePenMinor = 10_000L,
        membershipFeeUsdMinor = null,
        membershipCondition = "Consumo mensual S/ 999.99",
        sourceUrl = null,
        verificationStatus = "FIXTURE",
        catalogAsOf = LocalDate.of(2026, 1, 1),
        effectiveTo = null,
    )
}
