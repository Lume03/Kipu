package com.kipu.app.feature.plans.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle
import com.kipu.app.ui.theme.KipuSelectionRing
import com.kipu.app.ui.theme.KipuSurfaceContainerLowest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PlanPurchaseScreen(
    state: PlanPurchaseUiState,
    onOfferSelected: (String) -> Unit,
    onPurchase: () -> Unit,
    onRetryVerification: () -> Unit,
    onReloadCatalog: () -> Unit,
    onManageSubscriptions: () -> Unit,
    onNavigateBack: () -> Unit,
    reduceMotion: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.developmentMode) DevModeChip()
            Text(stringResource(if (state.developmentMode) R.string.plan_dev_subtitle else R.string.purchase_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)

            when {
                state.isLoading -> LoadingOffers(reduceMotion)
                state.offers.isEmpty() -> UnavailableOffers(onReloadCatalog)
                else -> Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.offers.forEach { offer ->
                        PurchaseOfferCard(
                            offer = offer,
                            selected = offer.product.storeProductId == state.selectedProductId,
                            current = state.developmentMode && offer.product.storeProductId == state.currentProductId,
                            developmentMode = state.developmentMode,
                            reduceMotion = reduceMotion,
                            onClick = { onOfferSelected(offer.product.storeProductId) },
                        )
                    }
                }
            }

            if (!state.isLoading && state.offers.isNotEmpty()) {
                PurchaseStatus(state, reduceMotion, onRetryVerification)
                Button(
                    onClick = onPurchase,
                    enabled = state.selectedOffer != null &&
                        state.status !in setOf(PlanPurchaseStatus.LAUNCHING, PlanPurchaseStatus.VERIFYING) &&
                        (state.developmentMode || state.status != PlanPurchaseStatus.RETRYABLE) &&
                        !(state.developmentMode && state.selectedProductId == state.currentProductId),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("purchase-cta"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.status == PlanPurchaseStatus.LAUNCHING || state.status == PlanPurchaseStatus.VERIFYING) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when (state.status) {
                            PlanPurchaseStatus.LAUNCHING -> stringResource(R.string.purchase_loading)
                            PlanPurchaseStatus.VERIFYING -> stringResource(R.string.purchase_verifying)
                            else -> stringResource(if (state.developmentMode) R.string.plan_dev_activate else R.string.purchase_buy)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!state.developmentMode) TextButton(onClick = onManageSubscriptions, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("purchase-manage-link")) {
                    Text(stringResource(R.string.purchase_manage_subscriptions), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun PurchaseOfferCard(
    offer: LocalizedBillingOffer,
    selected: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    current: Boolean = false,
    developmentMode: Boolean = false,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) KipuSelectionRing else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 480f),
        label = "purchase-card-border",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 4.dp else 1.dp,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.78f, stiffness = 480f),
        label = "purchase-card-elevation",
    )
    val scale = purchaseCardScale(selected, reduceMotion)
    Card(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .testTag("purchase-option-${offer.product.option.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KipuSurfaceContainerLowest),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(planTitle(offer), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (developmentMode) devPlanDetail(offer.product.option) else planTerms(offer), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    current -> PurchaseBadge(stringResource(R.string.plan_dev_current), emphasized = true)
                    offer.product.option == CommercialOption.ANNUAL -> PurchaseBadge(stringResource(R.string.purchase_badge_popular))
                    offer.product.option == CommercialOption.LIFETIME && !developmentMode -> PurchaseBadge(stringResource(R.string.purchase_lifetime_badge))
                }
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.purchase_option_selected_content_description),
                        tint = KipuSelectionRing,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                if (developmentMode) devPlanValidity(offer.product.option) else offer.formattedPrice,
                style = MaterialTheme.typography.headlineSmall.merge(androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("purchase-price-${offer.product.option.name.lowercase()}"),
            )
            if (offer.product.kind == BillingProductKind.SUBSCRIPTION && offer.trialPeriodDays != null) {
                val firstChargeDate = LocalDate.now().plusDays(offer.trialPeriodDays.toLong())
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("es", "PE")))
                Text(stringResource(R.string.purchase_trial_disclosure), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.purchase_trial_first_charge, firstChargeDate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DevModeChip() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
        Text(
            stringResource(R.string.plan_dev_mode_chip),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("purchase-dev-mode"),
        )
    }
}

@Composable
private fun devPlanValidity(option: CommercialOption): String = stringResource(
    when (option) {
        CommercialOption.FREE -> R.string.plan_dev_validity_free
        CommercialOption.MONTHLY -> R.string.plan_dev_validity_monthly
        CommercialOption.ANNUAL -> R.string.plan_dev_validity_annual
        CommercialOption.LIFETIME -> R.string.plan_dev_validity_lifetime
    },
)

@Composable
private fun devPlanDetail(option: CommercialOption): String = stringResource(
    when (option) {
        CommercialOption.FREE -> R.string.plan_dev_detail_free
        CommercialOption.MONTHLY -> R.string.plan_dev_detail_monthly
        CommercialOption.ANNUAL -> R.string.plan_dev_detail_annual
        CommercialOption.LIFETIME -> R.string.plan_dev_detail_lifetime
    },
)

@Composable
private fun planTitle(offer: LocalizedBillingOffer): String = when (offer.product.option) {
    CommercialOption.ANNUAL -> stringResource(R.string.purchase_plan_annual)
    CommercialOption.MONTHLY -> stringResource(R.string.purchase_plan_monthly)
    CommercialOption.LIFETIME -> stringResource(R.string.purchase_plan_lifetime)
    CommercialOption.FREE -> offer.product.displayName
}

@Composable
private fun planTerms(offer: LocalizedBillingOffer): String = when (offer.product.option) {
    CommercialOption.ANNUAL -> stringResource(R.string.purchase_annual_terms)
    CommercialOption.MONTHLY -> stringResource(R.string.purchase_monthly_terms)
    CommercialOption.LIFETIME -> stringResource(R.string.purchase_lifetime_terms)
    CommercialOption.FREE -> offer.product.displayName
}

@Composable
private fun PurchaseBadge(text: String, emphasized: Boolean = false) {
    Surface(color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
        Text(
            text,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LoadingOffers(reduceMotion: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("purchase-loading"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KipuSurfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.width(144.dp).height(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {}
                    PurchasePriceShimmer(reduceMotion = reduceMotion)
                    Surface(Modifier.fillMaxWidth().height(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun UnavailableOffers(onReloadCatalog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("purchase-unavailable"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.purchase_unavailable_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.purchase_unavailable_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onReloadCatalog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.purchase_retry_catalog))
            }
        }
    }
}

@Composable
private fun PurchaseStatus(state: PlanPurchaseUiState, reduceMotion: Boolean, onRetryVerification: () -> Unit) {
    if (state.status == PlanPurchaseStatus.READY && !state.effectivePremium) return
    if (state.developmentMode && state.status == PlanPurchaseStatus.VERIFIED && state.currentProductId == null) return
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state.status) {
        PlanPurchaseStatus.VERIFIED -> colors.primaryContainer to colors.onPrimaryContainer
        PlanPurchaseStatus.ERROR -> colors.errorContainer to colors.onErrorContainer
        PlanPurchaseStatus.RETRYABLE, PlanPurchaseStatus.PENDING, PlanPurchaseStatus.UNAVAILABLE ->
            colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceContainerHigh to colors.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("purchase-status-${state.status.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.status) {
                PlanPurchaseStatus.VERIFIED -> if (state.developmentMode) {
                    DevAppliedStatus(state)
                } else if (state.effectivePremium) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimatedVisibility(
                            visible = true,
                            enter = if (reduceMotion) fadeIn(tween(0)) else scaleIn(spring(dampingRatio = 0.72f, stiffness = 500f)) + fadeIn(tween(160)),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.purchase_success_content_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.purchase_success_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.purchase_success_body))
                    if (state.acknowledgementPending) Text(stringResource(R.string.purchase_ack_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.purchase_no_access_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.purchase_no_access_body))
                }
                PlanPurchaseStatus.PENDING -> {
                    Text(stringResource(R.string.purchase_pending_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.purchase_pending_body))
                }
                PlanPurchaseStatus.RETRYABLE -> if (state.developmentMode) {
                    StatusHeading(Icons.Filled.Info, stringResource(R.string.plan_dev_failed_title), content)
                    Text(stringResource(R.string.plan_dev_failed_body), style = MaterialTheme.typography.bodyMedium)
                } else {
                    StatusHeading(Icons.Filled.Info, stringResource(R.string.purchase_retryable_title), content)
                    Text(stringResource(R.string.purchase_retryable_body), style = MaterialTheme.typography.bodyMedium)
                    StatusAction(stringResource(R.string.purchase_retry), content, onRetryVerification)
                }
                PlanPurchaseStatus.ERROR -> {
                    StatusHeading(Icons.Filled.Info, stringResource(if (state.developmentMode) R.string.plan_dev_failed_title else R.string.purchase_error_title), content)
                    Text(stringResource(if (state.developmentMode) R.string.plan_dev_failed_body else R.string.purchase_error_body), style = MaterialTheme.typography.bodyMedium)
                    if (!state.developmentMode) StatusAction(stringResource(R.string.purchase_retry), content, onRetryVerification)
                }
                PlanPurchaseStatus.UNAVAILABLE -> {
                    Text(stringResource(R.string.purchase_unavailable_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.purchase_unavailable_body))
                }
                PlanPurchaseStatus.VERIFYING, PlanPurchaseStatus.LAUNCHING -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.purchase_verifying))
                    }
                }
                else -> Unit
            }
            state.lifecycle?.let { lifecycle -> LifecycleDisclosure(lifecycle, state.expiresAtEpochMillis) }
        }
    }
}

@Composable
private fun StatusHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusAction(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

/** The server computes the same validity; this mirrors it for display only. */
@Composable
private fun DevAppliedStatus(state: PlanPurchaseUiState) {
    val option = state.offers.firstOrNull { it.product.storeProductId == state.currentProductId }?.product?.option
    val activatedAt = state.activatedAtEpochMillis ?: System.currentTimeMillis()
    val body = when (option) {
        CommercialOption.FREE, null -> stringResource(R.string.plan_dev_applied_free)
        CommercialOption.LIFETIME -> stringResource(R.string.plan_dev_applied_lifetime)
        CommercialOption.MONTHLY -> stringResource(R.string.plan_dev_applied_until, localizedDate(activatedAt + 30L * DAY_MILLIS))
        CommercialOption.ANNUAL -> stringResource(R.string.plan_dev_applied_until, localizedDate(activatedAt + 365L * DAY_MILLIS))
    }
    val name = state.offers.firstOrNull { it.product.option == option }?.let { planTitle(it) }.orEmpty()
    StatusHeading(Icons.Filled.CheckCircle, stringResource(R.string.plan_dev_applied_title, name), MaterialTheme.colorScheme.onPrimaryContainer)
    Text(body, style = MaterialTheme.typography.bodyMedium)
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1_000

@Composable
private fun LifecycleDisclosure(lifecycle: PurchaseLifecycle, expiresAtEpochMillis: Long?) {
    val date = expiresAtEpochMillis?.let(::localizedDate)
    val text = when (lifecycle) {
        PurchaseLifecycle.ACTIVE -> stringResource(if (expiresAtEpochMillis == null) R.string.purchase_status_lifetime else R.string.purchase_status_active)
        PurchaseLifecycle.IN_GRACE_PERIOD -> stringResource(R.string.purchase_status_grace)
        PurchaseLifecycle.ACCOUNT_HOLD -> stringResource(R.string.purchase_status_hold)
        PurchaseLifecycle.CANCELED_ACTIVE -> stringResource(R.string.purchase_status_canceled_active, date.orEmpty())
        PurchaseLifecycle.EXPIRED -> stringResource(R.string.purchase_status_expired, date.orEmpty())
        PurchaseLifecycle.REVOKED -> stringResource(R.string.purchase_status_revoked)
        PurchaseLifecycle.PAUSED -> stringResource(R.string.purchase_status_paused)
        PurchaseLifecycle.PENDING -> stringResource(R.string.purchase_status_pending)
    }
    Text(text, modifier = Modifier.semantics { contentDescription = text }, style = MaterialTheme.typography.bodyMedium)
}

private fun localizedDate(epochMillis: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale("es", "PE"))
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())
