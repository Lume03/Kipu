package com.kipu.app.feature.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.auth.presentation.AuthCtaButton
import com.kipu.app.feature.auth.presentation.AuthFloatingField
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.util.Locale

private val BankPresets = listOf(AccountPreset.BCP, AccountPreset.BBVA, AccountPreset.INTERBANK, AccountPreset.SCOTIABANK, AccountPreset.BANCO_NACION)
private val WalletPresets = listOf(AccountPreset.YAPE, AccountPreset.PLIN)

@Composable
internal fun SetupGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 50.dp).background(colors.surfaceCard, shape)
            .border(1.5.dp, colors.borderSubtle, shape).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = colors.primaryText); Spacer(Modifier.width(8.dp)) }
        Text(text, color = colors.primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ 1 · cuenta (tipo)
@Composable
internal fun SetupAccountTypeContent(onKind: (SetupAccountKind) -> Unit, onBack: (() -> Unit)?) {
    val colors = rememberCalmEmeraldColors()
    SetupFrame(
        current = SetupStepId.ACCOUNT, allTied = false, pill = "Paso 1 de 3",
        title = "¿Dónde está tu dinero?", subtitle = "Elige el tipo de tu primera cuenta.",
        onBack = onBack, footer = {},
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KindRow("Cuenta bancaria", "Ahorros o cuenta sueldo, en soles o dólares",
                { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(KipuBrand.BCP, KipuBrand.BBVA, KipuBrand.INTERBANK).forEach { BrandLogo(it, it.displayName, null, size = 30.dp, contentPadding = 0.dp) } } },
                "setup-kind-bank") { onKind(SetupAccountKind.BANK) }
            KindRow("Billetera digital", "Yape o Plin, con saldo propio",
                { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(KipuBrand.YAPE, KipuBrand.PLIN).forEach { BrandLogo(it, it.displayName, null, size = 30.dp, contentPadding = 3.dp) } } },
                "setup-kind-wallet") { onKind(SetupAccountKind.WALLET) }
            KindRow("Efectivo", "Dinero en bolsillo o en casa",
                { SetupIconTile(Icons.Rounded.Payments, Color(0xFF208358), 36.dp) },
                "setup-kind-cash") { onKind(SetupAccountKind.CASH) }
            SetupCard(Modifier.fillMaxWidth(), dashed = true) {
                Text(
                    "Por ahora, una cuenta. Después agregas las que quieras desde Dinero, incluidas tus tarjetas de crédito.",
                    color = colors.secondaryMuted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun KindRow(title: String, subtitle: String, logos: @Composable () -> Unit, tag: String, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    SetupCard(Modifier.fillMaxWidth().testTag(tag), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                logos()
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = colors.secondaryMuted)
        }
    }
}

// ------------------------------------------------------------------ 1 · cuenta (formulario)
@Composable
internal fun SetupAccountFormContent(
    account: SetupAccountDraft,
    showErrors: Boolean,
    onPreset: (AccountPreset) -> Unit,
    onSubtype: (SetupBankSubtype) -> Unit,
    onAlias: (String) -> Unit,
    onBalance: (String) -> Unit,
    onCurrency: (Currency) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val title = when (account.kind) {
        SetupAccountKind.BANK -> "Cuenta bancaria"
        SetupAccountKind.WALLET -> "Billetera digital"
        else -> "Efectivo"
    }
    SetupFrame(
        current = SetupStepId.ACCOUNT, allTied = false, pill = "Paso 1 de 3",
        title = title, subtitle = "Solo lo necesario para empezar.", onBack = onBack,
        footer = {
            AuthCtaButton("Guardar y continuar", "Guardando…", loading = false, enabled = account.isComplete, onClick = onContinue)
        },
    ) {
        // Label and control travel together (6 dp); groups are 10 dp apart so the bank form fits one screen.
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (account.kind != SetupAccountKind.CASH) FieldGroup(if (account.kind == SetupAccountKind.WALLET) "Billetera" else "Banco") {
                val presets = if (account.kind == SetupAccountKind.WALLET) WalletPresets else BankPresets
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset -> PresetTile(preset, preset == account.preset) { onPreset(preset) } }
                }
            }
            if (account.kind == SetupAccountKind.BANK) FieldGroup("Tipo de cuenta") {
                Segmented(SetupBankSubtype.entries.map { it.label }, SetupBankSubtype.entries.indexOf(account.bankSubtype)) { onSubtype(SetupBankSubtype.entries[it]) }
            }
            AuthFloatingField(
                value = account.alias, onValueChange = onAlias, label = "Nombre de la cuenta", leadingIcon = Icons.Rounded.Badge,
                error = if (showErrors && account.alias.isBlank()) "Ponle un nombre para reconocerla" else account.aliasError,
                keyboardType = KeyboardType.Text,
            )
            AuthFloatingField(
                value = account.balanceText, onValueChange = onBalance, label = "Saldo inicial", leadingIcon = Icons.Outlined.AttachMoney,
                error = if (showErrors) account.balanceError else null, keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done,
            )
            FieldGroup("Moneda") {
                Segmented(listOf("Soles (S/)", "Dólares (US$)"), if (account.currency == Currency.PEN) 0 else 1) {
                    onCurrency(if (it == 0) Currency.PEN else Currency.USD)
                }
            }
            Text("Podrás corregir el saldo después desde la cuenta.", Modifier.padding(start = 4.dp), color = colors.secondaryMuted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FieldGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = rememberCalmEmeraldColors().primaryText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp))
        content()
    }
}

@Composable
private fun PresetTile(preset: AccountPreset, selected: Boolean, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(80.dp).background(colors.surfaceCard, shape)
            .border(if (selected) BorderStroke(2.dp, colors.primaryDeep) else BorderStroke(1.dp, colors.borderSubtle), shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 9.dp).testTag("setup-preset-${preset.name}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BrandLogo(KipuBrand.from(preset.name), preset.defaultName, preset.defaultName, size = 40.dp, contentPadding = if (preset == AccountPreset.PLIN) 5.dp else 0.dp)
        Text(
            preset.defaultName, color = if (selected) colors.primaryDeep else colors.primaryText, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth().background(colors.pillTrack, RoundedCornerShape(16.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) colors.surfaceCard else Color.Transparent, RoundedCornerShape(13.dp))
                    .selectable(on, role = Role.RadioButton) { onSelected(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) colors.primaryDeep else colors.primaryText, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ 2 · categorías
@Composable
internal fun SetupCategoriesContent(
    catalog: List<InitialCategoryCatalog.Entry>,
    hidden: Set<String>,
    minimumNotice: Boolean,
    onToggle: (String) -> Unit,
    onAll: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val active = catalog.count { it.id !in hidden }
    // System roots are GENERAL (usable for both); they read as spending, so only explicit income goes below.
    val expenses = catalog.filter { it.type != CategoryType.INCOME }
    val incomes = catalog.filter { it.type == CategoryType.INCOME }
    SetupFrame(
        current = SetupStepId.CATEGORIES, allTied = false, pill = "Paso 2 de 3",
        title = "Tus categorías", subtitle = "Deja activas las que usarás. Las demás se ocultan y puedes reactivarlas cuando quieras.",
        onBack = onBack,
        footer = {
            if (minimumNotice) Text("Necesitas al menos una categoría activa", Modifier.fillMaxWidth(), color = colors.expenseText,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            AuthCtaButton("Continuar", "Continuar", loading = false, enabled = active >= 1, onClick = onContinue)
        },
    ) {
        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$active de ${catalog.size} activas", Modifier.weight(1f), color = colors.primaryText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                if (hidden.isEmpty()) "Desmarcar todas" else "Activar todas", color = colors.primaryDeep,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.heightIn(min = 44.dp).clickable { onAll(hidden.isNotEmpty()) }.padding(vertical = 12.dp).testTag("setup-categories-all"),
            )
        }
        CategorySection("Gastos", expenses, catalog, hidden, onToggle)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Ingresos", incomes.takeIf { it.isNotEmpty() }?.let { list -> "${list.count { it.id !in hidden }} de ${list.size}" })
            if (incomes.isEmpty()) {
                SetupCard(Modifier.fillMaxWidth().testTag("setup-categories-income-empty"), dashed = true) {
                    Text(
                        "Kipu aún no trae categorías de ingreso. Crea las tuyas cuando las necesites desde Categorías.",
                        color = colors.secondaryMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (incomes.isNotEmpty()) CategoryGrid(incomes, catalog, hidden, onToggle)
    }
}

@Composable
private fun CategorySection(
    title: String,
    entries: List<InitialCategoryCatalog.Entry>,
    catalog: List<InitialCategoryCatalog.Entry>,
    hidden: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (entries.isEmpty()) return
    Column(Modifier.padding(horizontal = 20.dp)) {
        SectionLabel(title, "${entries.count { it.id !in hidden }} de ${entries.size}")
    }
    CategoryGrid(entries, catalog, hidden, onToggle)
}

@Composable
private fun SectionLabel(title: String, count: String?) {
    val colors = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(Locale.ROOT), Modifier.weight(1f).semantics { heading() }, color = colors.secondaryMuted,
            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        if (count != null) Text(count, color = colors.secondaryMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryGrid(
    entries: List<InitialCategoryCatalog.Entry>,
    catalog: List<InitialCategoryCatalog.Entry>,
    hidden: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { entry ->
                    CategoryCard(
                        entry, categoryTone(catalog.indexOf(entry)), entry.id !in hidden,
                        Modifier.weight(1f).fillMaxHeight().testTag("setup-category-${entry.name}"),
                    ) { onToggle(entry.id) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCard(entry: InitialCategoryCatalog.Entry, tone: PastelTone, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    SetupCard(
        modifier.heightIn(min = 60.dp), selected = active, dashed = !active,
        onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            // The check rides on the tile's corner so long names keep the whole width of the card.
            Box(Modifier.size(42.dp)) {
                SetupPastelTile(resolveCategoryIcon(entry.iconKey), tone, 36.dp, muted = !active)
                Box(Modifier.align(Alignment.BottomEnd).background(if (active) colors.surfaceCard else colors.background, RoundedCornerShape(8.dp)).padding(2.dp)) { SetupCheck(active, 18.dp) }
            }
            Text(entry.name, Modifier.weight(1f), color = if (active) colors.primaryText else colors.secondaryMuted, fontSize = 13.sp, lineHeight = 16.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------ 3 · plan
@Composable
internal fun SetupPlanContent(selected: CommercialOption, onSelect: (CommercialOption) -> Unit, onContinue: () -> Unit, onBack: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    SetupFrame(
        current = SetupStepId.PLAN, allTied = false, pill = "Paso 3 de 3",
        title = "Elige tu plan", subtitle = "Empieza gratis o desbloquea todo Kipu.", onBack = onBack,
        footer = {
            Text(stringResource(R.string.plan_preference_notice), Modifier.fillMaxWidth(), color = colors.secondaryMuted,
                style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            AuthCtaButton("Continuar", "Continuar", loading = false, enabled = true, onClick = onContinue)
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PREMIUM", Modifier.padding(start = 4.dp), color = colors.primaryDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            PlanRow("Anual", stringResource(R.string.plan_display_annual_price), "al año", stringResource(R.string.plan_display_savings), selected == CommercialOption.ANNUAL) { onSelect(CommercialOption.ANNUAL) }
            PlanRow("Mensual", stringResource(R.string.plan_display_monthly_price), "al mes", null, selected == CommercialOption.MONTHLY) { onSelect(CommercialOption.MONTHLY) }
            PlanRow("Lifetime", stringResource(R.string.plan_display_lifetime_price), "pago único", null, selected == CommercialOption.LIFETIME) { onSelect(CommercialOption.LIFETIME) }
            Spacer(Modifier.height(6.dp))
            Text("O CONTINÚA GRATIS", Modifier.padding(start = 4.dp), color = colors.secondaryMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            PlanRow("Free", null, null, null, selected == CommercialOption.FREE, "Gratis para siempre") { onSelect(CommercialOption.FREE) }
        }
    }
}

@Composable
private fun PlanRow(name: String, price: String?, per: String?, badge: String?, selected: Boolean, note: String? = null, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(colors.surfaceCard, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primaryDeep else colors.borderSubtle, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("setup-plan-$name"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(22.dp).border(2.dp, if (selected) colors.primaryDeep else colors.secondaryMuted, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(colors.primaryDeep, androidx.compose.foundation.shape.CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (badge != null) Text(badge, Modifier.background(colors.primaryDeep, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 9.dp, vertical = 2.dp),
                    color = colors.onPrimaryDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (note != null) Text(note, color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (price != null) Column(horizontalAlignment = Alignment.End) {
            Text(price, color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            if (per != null) Text(per, color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------------ resumen
@Composable
internal fun SetupSummaryContent(
    draft: SetupDraft,
    catalog: List<InitialCategoryCatalog.Entry>,
    save: SetupSaveState,
    onEditAccount: () -> Unit,
    onEditCategories: () -> Unit,
    onEditPlan: () -> Unit,
    onConfirm: () -> Unit,
    onChange: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val locked = save.loading
    SetupFrame(
        current = null, allTied = true, pill = "Último paso",
        title = "Revisa y confirma", subtitle = "Cambia lo que quieras antes de empezar.",
        onBack = if (locked) null else onBack,
        footer = {
            AuthCtaButton(if (save.error != null) "Reintentar" else "Todo correcto", "Guardando tu configuración…", loading = locked, enabled = true, onClick = onConfirm)
            if (!locked) SetupGhostButton("Cambiar algo", onChange, Modifier.testTag("setup-change"), Icons.Rounded.Edit)
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (save.error != null) SetupNotice(save.error, warning = save.offline)
            SummaryCard("CUENTA", locked, onEditAccount) { AccountSummary(draft.account) }
            SummaryCard("CATEGORÍAS", locked, onEditCategories) {
                val active = catalog.size - draft.hiddenCategoryIds.size
                Column {
                    Text("$active de ${catalog.size} activas", color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (draft.hiddenCategoryIds.isNotEmpty()) Text("${draft.hiddenCategoryIds.size} ocultas", color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    // A short preview of the active ones; the rest are counted so the row never overflows.
                    val shown = catalog.filter { it.id !in draft.hiddenCategoryIds }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        shown.take(SUMMARY_TILES).forEach { SetupPastelTile(resolveCategoryIcon(it.iconKey), categoryTone(catalog.indexOf(it)), 30.dp) }
                        if (shown.size > SUMMARY_TILES) Text("+${shown.size - SUMMARY_TILES}", color = colors.secondaryMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            SummaryCard("PLAN", locked, onEditPlan) { PlanSummary(draft.plan) }
        }
    }
}

@Composable
private fun SummaryCard(label: String, locked: Boolean, onEdit: () -> Unit, content: @Composable () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    SetupCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = colors.secondaryMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Row(
                    Modifier.heightIn(min = 44.dp).then(if (locked) Modifier else Modifier.clickable(role = Role.Button, onClick = onEdit)).padding(horizontal = 8.dp)
                        .testTag("setup-edit-$label"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = if (locked) colors.secondaryMuted else colors.primaryDeep)
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", color = if (locked) colors.secondaryMuted else colors.primaryDeep, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
            content()
        }
    }
}

@Composable
private fun AccountSummary(account: SetupAccountDraft) {
    val colors = rememberCalmEmeraldColors()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val brand = KipuBrand.from(account.preset?.name).takeIf { account.kind != SetupAccountKind.CASH }
        if (brand != null) BrandLogo(brand, account.alias, null, size = 44.dp, contentPadding = if (brand == KipuBrand.PLIN) 6.dp else 0.dp)
        else SetupIconTile(if (account.kind == SetupAccountKind.CASH) Icons.Rounded.Payments else Icons.Rounded.AccountBalance, Color(0xFF208358))
        Column(Modifier.weight(1f)) {
            Text(account.alias.ifBlank { "Sin nombre" }, color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                when (account.kind) {
                    SetupAccountKind.BANK -> "${account.bankSubtype.label} · ${if (account.currency == Currency.PEN) "Soles" else "Dólares"}"
                    SetupAccountKind.WALLET -> "Billetera · ${if (account.currency == Currency.PEN) "Soles" else "Dólares"}"
                    else -> "Efectivo · ${if (account.currency == Currency.PEN) "Soles" else "Dólares"}"
                },
                color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(account.balanceMinorUnits ?: 0L, account.currency), color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text("saldo inicial", color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanSummary(plan: CommercialOption) {
    val colors = rememberCalmEmeraldColors()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupIconTile(Icons.Rounded.Star, Color(0xFF8A4B00))
        Column(Modifier.weight(1f)) {
            Text(
                when (plan) { CommercialOption.FREE -> "Free"; CommercialOption.ANNUAL -> "Premium anual"; CommercialOption.MONTHLY -> "Premium mensual"; CommercialOption.LIFETIME -> "Premium lifetime" },
                color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            Text(if (plan == CommercialOption.FREE) "Gratis para siempre · puedes cambiar luego" else "Podrás cambiarlo cuando quieras",
                color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (plan != CommercialOption.FREE) {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(plan.amountMinor, Currency.PEN), color = colors.primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(when (plan) { CommercialOption.ANNUAL -> "al año"; CommercialOption.MONTHLY -> "al mes"; else -> "pago único" }, color = colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val SUMMARY_TILES = 7

internal fun formatMoney(minorUnits: Long, currency: Currency): String =
    (if (currency == Currency.PEN) "S/ " else "US$ ") + String.format(Locale.US, "%,.2f", minorUnits / 100.0)
