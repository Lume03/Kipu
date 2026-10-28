package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Shared identity for liquid accounts in the dashboard, detail and editor. */
@Composable
internal fun accountSurfaceColor(account: Account): Color {
    val colors = rememberCalmEmeraldColors()
    val preset = AccountPreset.normalizedFor(account.type, account.preset)
    val tint = when {
        account.colorToken == "custom_kipu_teal" -> Color(0xFF0F766E)
        account.type == AccountType.CASH -> Color(0xFF208358)
        else -> KipuBrand.from(preset?.name)?.brandColor ?: Color(0xFF0F766E)
    }
    return tint.copy(alpha = if (colors.isDark) 0.24f else 0.11f)
        .compositeOver(MaterialTheme.colorScheme.surface)
}

@Composable
internal fun AccountIdentityIcon(account: Account, size: Dp = 52.dp) {
    val preset = AccountPreset.normalizedFor(account.type, account.preset)
    val brand = KipuBrand.from(preset?.name)
    if (brand != null && account.type != AccountType.CASH) {
        BrandLogo(brand, account.alias, null, size = size, contentPadding = 0.dp)
    } else {
        val colors = rememberCalmEmeraldColors()
        Box(
            Modifier.size(size).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (account.type) {
                    AccountType.CASH -> Icons.Rounded.Payments
                    AccountType.DIGITAL_WALLET -> Icons.Rounded.Wallet
                    else -> Icons.Rounded.AccountBalance
                },
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
