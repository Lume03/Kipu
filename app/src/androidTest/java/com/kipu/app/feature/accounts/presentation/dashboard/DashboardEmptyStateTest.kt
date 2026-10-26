package com.kipu.app.feature.accounts.presentation.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.kipu.app.feature.accounts.presentation.FinancialTerminology
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.feature.accounts.presentation.instruments.CalmEmeraldIntroView
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardEmptyStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstAccountActionNavigatesToAccountCreation() {
        var navigatedToAccountCreation = false
        setEmptyDashboard(onNavigateToNewAccount = { navigatedToAccountCreation = true })

        compose.onNodeWithText("Agregar mi primera cuenta").performScrollTo().assertIsDisplayed().performClick()

        compose.runOnIdle { assertTrue(navigatedToAccountCreation) }
    }

    @Test
    fun creditCardCreationRemainsAvailableFromTheEmptyState() {
        var navigatedToCardCreation = false
        setEmptyDashboard(onNavigateToNewCard = { navigatedToCardCreation = true })

        compose.onNodeWithTag("dashboard_empty_add_card")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle { assertTrue(navigatedToCardCreation) }
    }

    @Test
    fun emptyDashboardShowsZeroOwnMoneyAndOneFirstStep() {
        setEmptyDashboard()

        // H5: with no instruments the own money is a real zero, not a missing figure.
        compose.onNodeWithTag("dashboard_empty_balance").assertIsDisplayed()
        compose.onNodeWithText(FinancialTerminology.REAL_MONEY_AVAILABLE).assertIsDisplayed()
        compose.onNodeWithText("Plan Básico · 0 de 4 cuentas y tarjetas").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("0 activas")[0].assertExists()
        compose.onNodeWithText("Aún no tienes cuentas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Agregar mi primera cuenta").assertIsDisplayed()
        compose.onNodeWithText("Gestionar").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sin tarjetas de crédito", substring = true))
        compose.onNodeWithText("Sin tarjetas de crédito", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cuentas y tarjetas archivadas").assertDoesNotExist()
        compose.onNodeWithText("Tus datos permanecen privados y protegidos", substring = true).assertDoesNotExist()
    }

    @Test
    fun firstAccountActionLeadsToInstrumentIntroWithQuotaAndPlansRoute() {
        val showInstrumentIntro = mutableStateOf(false)
        var navigatedToPlans = false
        compose.setContent {
            KipuTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showInstrumentIntro.value) {
                        CalmEmeraldIntroView(
                            activeCount = 0,
                            maxQuota = 4,
                            onNavigateToPlans = { navigatedToPlans = true },
                            onSelectSavingsDebit = {},
                            onSelectCreditCard = {},
                            onSelectWallet = {},
                            onSelectCash = {},
                        )
                    } else {
                        EmptyDashboardContent(
                            onNavigateToNewAccount = { showInstrumentIntro.value = true },
                            onNavigateToNewCard = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("dashboard_empty_add_account").performClick()
        compose.onNodeWithText("Cuentas y tarjetas activas: 0 de 4 incluidos en el plan base")
            .assertIsDisplayed()
        compose.onNodeWithText("Ver planes →").performClick()

        compose.runOnIdle { assertTrue(navigatedToPlans) }
    }

    @Test
    fun bothEmptyStateActionsRemainAvailableWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KipuTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        EmptyDashboardContent(
                            onNavigateToNewAccount = {},
                            onNavigateToNewCard = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("dashboard_empty_add_account").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("dashboard_empty_add_card"))
        compose.onNodeWithTag("dashboard_empty_add_card").assertIsDisplayed()
    }

    @Test
    fun capturesEmptyDashboardLightThemeAfter() = captureAfter(dark = false, name = "dashboard-empty-after-light")

    @Test
    fun capturesEmptyDashboardDarkThemeAfter() = captureAfter(dark = true, name = "dashboard-empty-after-dark")

    private fun setEmptyDashboard(
        onNavigateToNewAccount: () -> Unit = {},
        onNavigateToNewCard: () -> Unit = {},
        dark: Boolean = false,
    ) {
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EmptyDashboardContent(
                        onNavigateToNewAccount = onNavigateToNewAccount,
                        onNavigateToNewCard = onNavigateToNewCard,
                    )
                }
            }
        }
    }

    private fun captureAfter(dark: Boolean, name: String) {
        setEmptyDashboard(dark = dark)
        compose.waitForIdle()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "dashboard-empty-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
