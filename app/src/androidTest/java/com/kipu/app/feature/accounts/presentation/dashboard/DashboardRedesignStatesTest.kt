package com.kipu.app.feature.accounts.presentation.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.DashboardUiState
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import com.kipu.app.feature.home.domain.CurrencyNetWorth
import com.kipu.app.feature.home.presentation.NetWorthUiState
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Rediseño de Dinero (H1–H8) con datos deterministas; guarda capturas para la QA visual. */
class DashboardRedesignStatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val user = UserId("f3000000-0000-4000-8000-000000000001")
    private val opened = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun h1WalletsAppearUnderTheirAccountAndDoNotAddToBankMoney() {
        show(populatedViewModel(), netWorth = netWorthContent())

        compose.onNodeWithText("S/ 1,197.70").assertIsDisplayed()
        compose.onNodeWithText("S/ 1,097.70").assertIsDisplayed()
        compose.scrolled("3 activas · 2 billeteras", false).assertIsDisplayed()
        compose.scrolled("Usa el saldo de Cuenta BCP", false).assertIsDisplayed()
        compose.onNodeWithText("Usa el saldo de BBVA").assertExists()
        compose.onNodeWithText("Plin y Yape no suman saldo aparte", substring = true).assertExists()
        compose.scrolled("Plan Básico · 4 de 4 cuentas y tarjetas", false).assertExists()
        compose.onNodeWithText("Mejorar a PRO").assertExists()
        capture("H1-dinero")
    }

    @Test
    fun h2MaskHidesEveryAmount() {
        show(populatedViewModel(masked = true), netWorth = netWorthContent())

        assertEquals(0, compose.onAllNodesWithText("1,197.70", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Mostrar importes").assertExists()
        capture("H2-ocultos")
    }

    @Test
    fun h3NetWorthExpandsIntoItsComponents() {
        show(populatedViewModel(), netWorth = netWorthContent())

        compose.onNodeWithText("Ver detalle").performClick()
        compose.onNodeWithText("Activos reales").assertIsDisplayed()
        compose.onNodeWithText("Por cobrar").assertIsDisplayed()
        compose.onNodeWithText("Pasivos pendientes").assertIsDisplayed()
        compose.onNodeWithText("1,052.30", substring = true).assertExists()
        capture("H3-patrimonio")
    }

    @Test
    fun h4ArchivedInstrumentsExpand() {
        show(populatedViewModel(), netWorth = netWorthContent())

        compose.scrolled("Cuentas y tarjetas archivadas", false).performClick()
        compose.scrolled("Cuenta BCP (anterior)", false).assertIsDisplayed()
        compose.scrolled("Conservan su historial", true).assertIsDisplayed()
        capture("H4-archivadas")
    }

    @Test
    fun h6UnavailableNetWorthIsNeverZero() {
        show(populatedViewModel(), netWorth = NetWorthUiState.Error("No se pudo calcular tu patrimonio."))

        compose.onNodeWithText("No disponible").assertIsDisplayed()
        compose.onNodeWithText("no significa S/ 0.00", substring = true).assertIsDisplayed()
        capture("H6-no-disponible")
    }

    @Test
    fun h7LoadingShowsSkeletonWithoutFigures() {
        val viewModel = populatedViewModel()
        every { viewModel.dashboardUiState } returns MutableStateFlow(DashboardUiState(isLoading = true))
        show(viewModel)

        compose.onNodeWithTag(DASHBOARD_LOADING_TAG).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("S/", substring = true).fetchSemanticsNodes().size)
        capture("H7-cargando")
    }

    @Test
    fun h8ErrorOffersRetryAndNoFigures() {
        val viewModel = populatedViewModel()
        every { viewModel.dashboardUiState } returns MutableStateFlow(
            DashboardUiState(isLoading = false, errorMessage = "boom"),
        )
        var netWorthRetried = false
        show(viewModel, onRetryNetWorth = { netWorthRetried = true })

        compose.onNodeWithText("No pudimos cargar tu dinero").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("S/ 0.00", substring = true).fetchSemanticsNodes().size)
        capture("H8-error")
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertTrue(netWorthRetried) }
        verify { viewModel.retryDashboard() }
    }

    @Test
    fun h1DarkThemeAndLargeTextKeepActionsReachable() {
        show(populatedViewModel(), netWorth = netWorthContent(), dark = true, fontScale = 1.3f)

        compose.onNodeWithText("Mi dinero").assertIsDisplayed()
        compose.scrolled("+ Cuenta", false).assertIsDisplayed()
        compose.scrolled("Usa el saldo de Cuenta BCP", false).assertIsDisplayed()
        capture("H1-dark-1_3x")
        compose.scrolled("+ Tarjeta", false).assertIsDisplayed()
        capture("H1-dark-1_3x-tarjetas")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.scrolled(text: String, substring: Boolean) =
        onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText(text, substring = substring))
            .let { onNodeWithText(text, substring = substring) }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescription(text: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(text))

    private fun show(
        viewModel: AccountsViewModel,
        netWorth: NetWorthUiState? = null,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onRetryNetWorth: () -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                KipuTheme(darkTheme = dark) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToNewAccount = {},
                        onNavigateToNewCard = {},
                        netWorthState = netWorth,
                        onRetryNetWorth = onRetryNetWorth,
                        onNavigateToCalendar = {},
                        modifier = Modifier.fillMaxSize().testTag(ROOT),
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "b1-dashboard").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onNodeWithTag(ROOT).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun netWorthContent() = NetWorthUiState.Content(
        listOf(
            CurrencyNetWorth(
                currency = Currency.PEN,
                realAssets = Money(119_770, Currency.PEN),
                receivables = Money(15_000, Currency.PEN),
                liabilities = Money(240_000, Currency.PEN),
            ),
        ),
    )

    private fun populatedViewModel(masked: Boolean = false): AccountsViewModel {
        val cash = account("1", "Efectivo demo", AccountType.CASH, AccountPreset.CASH)
        val bbva = account("2", "BBVA", AccountType.SAVINGS, AccountPreset.BBVA)
        val plin = account("3", "Plin", AccountType.DIGITAL_WALLET, AccountPreset.PLIN, linkedTo = "2")
        val bcp = account("4", "Cuenta BCP", AccountType.SAVINGS, AccountPreset.BCP)
        val yape = account("5", "Yape", AccountType.DIGITAL_WALLET, AccountPreset.YAPE, linkedTo = "4")
        val archived = account("6", "Cuenta BCP (anterior)", AccountType.SAVINGS, AccountPreset.BCP).copy(isArchived = true)
        val visa = CreditCard(
            id = CardId("f3000000-0000-4000-8000-000000000101"),
            userId = user,
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "4242",
            currency = Currency.PEN,
            creditLimitMinorUnits = 100_000,
            billingDay = 20,
            dueDay = 5,
            stylePresetId = "bcp-visa-classic",
        )
        val data = FinancialDashboardData(
            totalPen = Money(119_770, Currency.PEN),
            totalUsd = Money(0, Currency.USD),
            liquidAccounts = listOf(
                AccountWithBalance(cash, Money(10_000, Currency.PEN)),
                AccountWithBalance(bbva, Money(10_000, Currency.PEN)),
                AccountWithBalance(plin, Money(10_000, Currency.PEN)),
                AccountWithBalance(bcp, Money(99_770, Currency.PEN)),
                AccountWithBalance(yape, Money(99_770, Currency.PEN)),
            ),
            creditCards = listOf(CreditCardWithSummary(visa, Money(0, Currency.PEN), Money(100_000, Currency.PEN), 0.0)),
            activeComputableCount = 4,
        )
        val viewModel = mockk<AccountsViewModel>(relaxed = true)
        every { viewModel.dashboardUiState } returns MutableStateFlow(DashboardUiState(isLoading = false, dashboardData = data))
        every { viewModel.instrumentsUiState } returns MutableStateFlow(
            InstrumentsUiState(
                isLoading = false,
                activeAccounts = listOf(cash, bbva, plin, bcp, yape),
                archivedAccounts = listOf(archived),
                activeCards = listOf(visa),
                activeComputableCount = 4,
                maxFreeQuota = 4,
            ),
        )
        every { viewModel.creditNotifications } returns MutableStateFlow<List<CreditUtilizationNotification>>(emptyList())
        every { viewModel.isMasked } returns MutableStateFlow(masked)
        return viewModel
    }

    private fun account(
        suffix: String,
        alias: String,
        type: AccountType,
        preset: AccountPreset,
        linkedTo: String? = null,
    ) = Account(
        id = AccountId(id(suffix)),
        userId = user,
        alias = alias,
        type = type,
        currency = Currency.PEN,
        preset = preset,
        initialBalance = Money(0L, Currency.PEN),
        openedAt = opened,
        linkedAccountId = linkedTo?.let { AccountId(id(it)) },
    )

    private fun id(suffix: String) = "f3000000-0000-4000-8000-" + suffix.padStart(12, '0')

    private companion object {
        const val ROOT = "dashboard_redesign_root"
    }
}
