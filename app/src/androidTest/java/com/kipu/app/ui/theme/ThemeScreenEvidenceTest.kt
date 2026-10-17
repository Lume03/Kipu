package com.kipu.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.presentation.detail.AccountDetailContent
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen
import com.kipu.app.feature.accounts.presentation.instruments.InstallmentSimulatorScreen
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.categories.presentation.categories.CategoryTab
import com.kipu.app.feature.categories.presentation.categories.CategoryTypeTabs
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.feature.debts.presentation.*
import com.kipu.app.feature.movements.presentation.*
import com.kipu.app.feature.settings.presentation.PermissionSafetyBanner
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** All models are synthetic; no repositories, preferences, purchases or ledger writes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class ThemeScreenEvidenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureCorrectedScreensInBothThemes() {
        val screen = mutableStateOf("accounts")
        val dark = mutableStateOf(false)
        val account = Account(AccountId("e3900000-0000-4000-8000-000000000001"), UserId("e3900000-0000-4000-8000-000000000002"), alias = "Ahorro de ejemplo",
            type = AccountType.SAVINGS, currency = Currency.PEN, preset = AccountPreset.GENERIC,
            initialBalance = Money(25000, Currency.PEN), openedAt = Instant.parse("2026-10-01T00:00:00Z"))
        val debt = DebtSummary("theme-debt", "e3900000-0000-4000-8000-000000000002", DebtObligationType.PAYABLE,
            "Préstamo de ejemplo", 30000, 20000, "PEN", LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 11, 1), 3, "Datos ficticios", DebtLifecycleStatus.ACTIVE, "SYNCED", 1)
        val card = CreditCard(CardId("e3900000-0000-4000-8000-000000000003"), UserId("e3900000-0000-4000-8000-000000000002"), issuer = "BCP",
            network = CardNetwork.VISA, lastFourDigits = "1234", currency = Currency.PEN,
            creditLimitMinorUnits = 500_000, billingDay = 15, dueDay = 5, personalTeaBps = 4200)
        val movement = TransactionItem(Transaction(id = "theme-movement", userId = account.userId.value,
            type = MovementType.EXPENSE, amountMinor = 1500, currency = "PEN", sourceAccountId = account.id.value,
            categoryId = "theme-category",
            occurredAt = 1_758_000_000_000, status = TransactionStatus.ACTIVE), sourceAccountAlias = account.alias)
        compose.setContent {
            KipuTheme(darkTheme = dark.value) {
                Surface(Modifier.fillMaxSize().testTag("theme_evidence_root"), color = rememberKipuColors().background) {
                    when (screen.value) {
                        "accounts" -> AccountDetailContent(account, null, Money(25000, Currency.PEN), null,
                            false, {}, { _, _ -> }, {}, {}, {})
                        "account-selector" -> AccountSelectorBottomSheet({}, listOf(account), selectedAccountId = account.id.value)
                        "debts" -> DebtListScreen(listOf(debt), null, {}, {}, {}, {})
                        "debt-detail" -> DebtDetailScreen(debt, true, {}, {}, {})
                        "schedule" -> DebtScheduleScreen(DebtScheduleUiState(debt = debt, isLoading = false),
                            {}, {}, {}, {}, {}, { _, _ -> }, {})
                        "settlement" -> DebtSettlementSheet(debt, DebtSettlementUiState(principalAmount = "100", isLoading = false),
                            {}, {}, {}, {}, {}, {})
                        "rates" -> RateCatalogScreen(card, emptyList(), null, false, false, emptyFlow(), {}, { _, _ -> }, {})
                        "categories-permissions" -> Column {
                            CategoryTypeTabs(CategoryTab.EXPENSE, 4, 2, {})
                            PermissionSafetyBanner()
                        }
                        "color-picker" -> CustomPastelColorDialog("#FFFFFF", "Categoría de ejemplo", {}, {})
                        "icon-picker" -> IconCatalogDialog("restaurant", {}, {})
                        "movement-detail" -> MovementDetailSheet(movement, emptyList(), false, false, {}, {}, {})
                        "free-filters" -> MovementFiltersSheet(MovementHistoryUiState(ownerId = account.userId.value,
                            accessStatus = MovementHistoryAccessDecision.PremiumRequired(MovementHistoryQuery())), {}, { it.validate() })
                        "installments" -> InstallmentSimulatorScreen(PurchaseCandidate("theme-purchase", card.id,
                            Money(10000, Currency.PEN), "Comercio de ejemplo", Instant.parse("2026-09-16T12:00:00Z"),
                            suggestedInstallments = 3), card, onConfirmPurchase = {}, onRejectPurchase = {})
                        "editor" -> MovementEditorContent(MovementEditorUiState(isLoading = false, amountText = "15.00"),
                            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val directory = File(evidenceRoot, "issue-39").apply {
            check(mkdirs() || isDirectory) { "Could not create issue-39 evidence directory: $absolutePath" }
        }
        android.util.Log.i("ThemeScreenEvidence", "package=${context.packageName} evidenceDir=${directory.absolutePath}")
        for (name in listOf("accounts", "account-selector", "debts", "debt-detail", "schedule", "settlement", "rates", "categories-permissions", "editor", "color-picker", "icon-picker", "movement-detail", "installments", "free-filters")) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { screen.value = name; dark.value = night }
                compose.waitForIdle()
                val target = when (name) {
                    "account-selector" -> compose.onNodeWithTag("account_selector_sheet")
                    "movement-detail" -> compose.onNodeWithTag("movement_detail_sheet")
                    "free-filters" -> compose.onNodeWithTag("panel_advanced_filters")
                    "settlement" -> compose.onNode(isDialog() and hasAnyDescendant(hasTestTag("settlement-save")))
                    "color-picker" -> compose.onNode(isDialog() and hasAnyDescendant(hasTestTag("color_modal_hex_input")))
                    "icon-picker" -> compose.onNode(isDialog() and hasAnyDescendant(hasTestTag("icon_catalog_search_input")))
                    else -> compose.onNodeWithTag("theme_evidence_root")
                }
                compose.waitUntil(timeoutMillis = 10_000) {
                    runCatching { target.assertIsDisplayed() }.isSuccess
                }
                val captured = target.captureToImage()
                val pixels = captured.toPixelMap()
                val sampledColors = (1..12).flatMap { x ->
                    (1..20).map { y -> pixels[x * (pixels.width - 1) / 13, y * (pixels.height - 1) / 21].toArgb() }
                }.toSet()
                assertTrue("$name dark=$night must capture content, not just the backdrop", sampledColors.size > 3)
                val image = captured.asAndroidBitmap()
                val output = File(directory, "after-$name-${if (night) "dark" else "light"}.png")
                output.outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertTrue("${output.name} must be written", output.isFile && output.length() > 0)
                if (name == "free-filters") {
                    compose.onNodeWithText("Cuenta").performScrollTo().performClick()
                    compose.waitForIdle()
                    val upsell = compose.onNodeWithTag("dialog_movement_filter_pro_upsell").captureToImage().asAndroidBitmap()
                    val upsellOutput = File(directory, "after-upsell-${if (night) "dark" else "light"}.png")
                    upsellOutput.outputStream().use {
                        upsell.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    assertTrue("${upsellOutput.name} must be written", upsellOutput.isFile && upsellOutput.length() > 0)
                    compose.onNodeWithContentDescription("Cerrar").performClick()
                }
            }
        }
    }
}
