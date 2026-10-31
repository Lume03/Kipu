package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.model.PurchaseRateSource
import com.kipu.app.feature.accounts.domain.model.PurchaseTerms
import com.kipu.app.feature.accounts.domain.model.PurchaseType
import com.kipu.app.feature.accounts.domain.usecase.SimulateInstallments
import com.kipu.app.feature.accounts.presentation.components.CreditPurchaseDraft
import com.kipu.app.feature.accounts.presentation.components.CreditPurchaseDraftSaver
import com.kipu.app.feature.accounts.presentation.components.CreditPurchaseTermsSection
import com.kipu.app.feature.accounts.presentation.components.validateCreditPurchaseDraft
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.PastelCategoryPickerBottomSheet
import com.kipu.app.feature.movements.presentation.components.CategorySelectionRow
import com.kipu.app.feature.movements.presentation.components.MerchantSelectionRow
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Production wiring; the content remains renderable without Hilt for previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentSimulatorRoute(
    candidate: PurchaseCandidate,
    card: CreditCard,
    availableCredit: Money?,
    onConfirmPurchase: (PurchaseCandidate, PurchaseTerms, String?) -> Unit,
    onRejectPurchase: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InstallmentSimulatorViewModel = hiltViewModel(key = "simulator-${candidate.id}"),
    merchantPickerViewModel: MerchantPickerViewModel = hiltViewModel(key = "simulator-merchant-${candidate.id}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker by merchantPickerViewModel.uiState.collectAsStateWithLifecycle()
    var showMerchantPicker by rememberSaveable(candidate.id) { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable(candidate.id) { mutableStateOf(false) }
    LaunchedEffect(candidate.id) { viewModel.initialize(candidate) }
    val purchaseDraft = state.draft ?: return
    val selectedCandidate = candidate.copy(
        merchant = state.merchantName.ifBlank { candidate.merchant },
        categoryId = state.category?.id,
    )
    InstallmentSimulatorScreen(
        candidate = selectedCandidate, card = card, availableCredit = availableCredit,
        onConfirmPurchase = { terms ->
            // Read the same immutable values used to render this frame; no clock or rate lookup on save.
            onConfirmPurchase(selectedCandidate, terms, state.merchantId)
        },
        onRejectPurchase = onRejectPurchase, modifier = modifier,
        merchantName = state.merchantName, selectedCategory = state.category,
        purchaseDraft = purchaseDraft, onUpdateDraft = viewModel::updateDraft,
        categoryError = state.categoryError, products = state.products, referenceLoading = state.referenceLoading,
        canConfirmClassification = !state.categoriesLoading && !state.resolvingMerchant && !state.creatingCategory,
        onOpenMerchantPicker = {
            merchantPickerViewModel.initialize(
                initialMerchant = picker.catalogEntries.firstOrNull { it.id.value == state.merchantId },
                initialProvisionalText = state.merchantName.takeIf { state.merchantId == null },
            )
            showMerchantPicker = true
        },
        onClearMerchant = { merchantPickerViewModel.clearSelection(); viewModel.clearMerchant() },
        onOpenCategoryPicker = { showCategoryPicker = true },
    )
    if (showMerchantPicker) MerchantPickerBottomSheet(
        state = picker, onQueryChange = merchantPickerViewModel::onQueryChanged,
        onSelectMerchant = { merchant -> merchantPickerViewModel.selectMerchant(merchant); viewModel.selectMerchant(merchant) },
        onSetProvisionalText = { name -> merchantPickerViewModel.setProvisionalText(name); viewModel.setProvisionalMerchant(name) },
        onClearSelection = { merchantPickerViewModel.clearSelection(); viewModel.clearMerchant() },
        onDismiss = { showMerchantPicker = false },
    )
    if (showCategoryPicker) PastelCategoryPickerBottomSheet(
        categories = state.categories, selectedCategoryId = state.categoryId,
        onSelect = { category -> viewModel.selectCategory(category); showCategoryPicker = false },
        onCreateCategory = viewModel::createCategoryOrSubcategory,
        onDismiss = { showCategoryPicker = false },
        isCreating = state.creatingCategory, errorMessage = state.categoryCreationError,
        directSelectionMode = true, title = "Elegir categoría",
        subtitle = "Selecciona una categoría o subcategoría para clasificar tu gasto",
    )
}

@Composable
fun InstallmentSimulatorScreen(
    candidate: PurchaseCandidate,
    card: CreditCard,
    teaBps: Int? = null,
    availableCredit: Money? = null,
    onConfirmPurchase: (PurchaseTerms) -> Unit,
    onRejectPurchase: () -> Unit,
    modifier: Modifier = Modifier,
    purchaseDraft: CreditPurchaseDraft? = null,
    onUpdateDraft: ((CreditPurchaseDraft) -> Unit)? = null,
    merchantName: String = candidate.merchant,
    selectedCategory: CategoryOption? = null,
    categoryError: String? = null,
    products: List<CreditProductReference> = emptyList(),
    referenceLoading: Boolean = false,
    canConfirmClassification: Boolean = true,
    onOpenMerchantPicker: () -> Unit = {},
    onClearMerchant: () -> Unit = {},
    onOpenCategoryPicker: () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    var localDraft by rememberSaveable(candidate.id, stateSaver = CreditPurchaseDraftSaver) {
        mutableStateOf(CreditPurchaseDraft(
            purchaseType = if (candidate.suggestedInstallments == 1) PurchaseType.DIRECT else PurchaseType.INSTALLMENTS,
            installmentsText = candidate.suggestedInstallments.coerceAtLeast(2).toString(),
        ).forCard(card, products))
    }
    val draft = purchaseDraft ?: localDraft
    fun updateDraft(value: CreditPurchaseDraft) {
        if (onUpdateDraft != null) onUpdateDraft(value) else localDraft = value
    }
    LaunchedEffect(card, products, teaBps) {
        updateDraft(draft.forCard(card, products).let { current ->
            if (current.rateSource == null && teaBps != null && teaBps > 0) current.copy(
                teaPercentText = BigDecimal.valueOf(teaBps.toLong(), 2).toPlainString(), rateSource = PurchaseRateSource.REFERENCE,
            ) else current
        })
    }
    val validation = remember(draft) { validateCreditPurchaseDraft(draft) }
    val preview = remember(candidate, card, validation.terms) {
        validation.terms?.let { terms -> runCatching { SimulateInstallments()(candidate, card, terms = terms) } }
    }
    val simulation = preview?.getOrNull()
    val date = remember(candidate.occurredAt) {
        candidate.occurredAt.atZone(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }
    val overLimitMinor = availableCredit?.minorUnits?.coerceAtLeast(0L)?.let { available ->
        (candidate.amount.minorUnits - available).takeIf { it > 0L }
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.credit_simulator_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${card.issuer} •••• ${card.lastFourDigits}", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            Text(stringResource(R.string.credit_simulator_principal), style = MaterialTheme.typography.labelMedium)
            MoneyText(candidate.amount, style = MaterialTheme.typography.headlineMedium, color = colors.primaryDeep)
            Text(stringResource(R.string.credit_simulator_date, date), style = MaterialTheme.typography.bodySmall)
            MerchantSelectionRow(merchantName, onOpenMerchantPicker, onClearMerchant)
            CategorySelectionRow(selectedCategory?.name, selectedCategory?.icon, required = true,
                error = categoryError, onClick = onOpenCategoryPicker)
            if (merchantName.isBlank()) Text(stringResource(R.string.credit_simulator_merchant_required), color = MaterialTheme.colorScheme.error)
            if (selectedCategory == null) Text(stringResource(R.string.credit_simulator_category_required), color = colors.secondaryMuted)
            CreditPurchaseTermsSection(
                draft = draft, validation = validation, simulation = simulation, onDraftChanged = ::updateDraft,
                referenceLoading = referenceLoading,
                previewError = if (preview?.isFailure == true) stringResource(R.string.credit_simulator_calculation_error) else null,
            )
            if (overLimitMinor != null) Surface(color = colors.warningBg, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.credit_simulator_over_limit), color = colors.warningText, fontWeight = FontWeight.SemiBold)
                    MoneyText(Money(overLimitMinor, card.currency), color = colors.warningText)
                    Text(stringResource(R.string.credit_simulator_available_notice), style = MaterialTheme.typography.bodySmall, color = colors.warningText)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRejectPurchase, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.credit_simulator_reject))
                }
                Button(onClick = { simulation?.let { onConfirmPurchase(it.terms) } },
                    enabled = simulation != null && merchantName.isNotBlank() && selectedCategory != null && canConfirmClassification,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("credit_simulator_confirm")) {
                    Text(stringResource(R.string.credit_simulator_confirm))
                }
            }
        }
    }
}
