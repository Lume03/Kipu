package com.kipu.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.feature.accounts.presentation.components.CardArtCatalog
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.Normalizer
import java.util.Locale

/**
 * Banks, retail issuers and wallets with a bundled logo. Marks belong to their owners; see
 * `docs/referencias/Marcas/README.md` before publishing. Every brand keeps an initials fallback.
 */
enum class KipuBrand(
    val displayName: String,
    val initials: String,
    val brandColor: Color,
    @DrawableRes val logoRes: Int?,
    private val aliases: Set<String>,
) {
    BCP("BCP", "BCP", Color(0xFF002A8F), R.drawable.ic_brand_bcp, setOf("bcp", "banco de credito", "banco de credito del peru")),
    BBVA("BBVA", "BBVA", Color(0xFF004481), R.drawable.ic_brand_bbva, setOf("bbva", "bbva peru")),
    INTERBANK("Interbank", "IB", Color(0xFF00843D), R.drawable.ic_brand_interbank, setOf("interbank")),
    SCOTIABANK("Scotiabank", "SB", Color(0xFFEC111A), R.drawable.ic_brand_scotiabank, setOf("scotiabank")),
    BANCO_NACION("Banco de la Nación", "BN", Color(0xFFC8102E), R.drawable.ic_brand_banco_nacion, setOf("banco nacion", "banco de la nacion", "bn")),
    BANBIF("BanBif", "BB", Color(0xFF0B3D91), R.drawable.ic_brand_banbif, setOf("banbif")),
    FALABELLA("Banco Falabella", "BF", Color(0xFF007A33), R.drawable.ic_brand_falabella, setOf("banco falabella", "falabella")),
    CENCOSUD("Cencosud", "CS", Color(0xFF0060A9), R.drawable.ic_brand_cencosud, setOf("cencosud", "cencosud scotia", "metro")),
    RIPLEY("Banco Ripley", "BR", Color(0xFF5B2D86), R.drawable.ic_brand_ripley, setOf("ripley", "banco ripley")),
    YAPE("Yape", "Y", Color(0xFF742284), R.drawable.ic_brand_yape, setOf("yape")),
    PLIN("Plin", "P", Color(0xFF00A19B), R.drawable.ic_brand_plin, setOf("plin"));

    companion object {
        /** Resolves presets ("BANCO_NACION"), catalog codes ("BANCO_FALABELLA") and display names. */
        fun from(value: String?): KipuBrand? {
            val key = value?.let(::normalizeBrandKey)?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { brand ->
                normalizeBrandKey(brand.name) == key || key in brand.aliases
            }
        }
    }
}

internal fun normalizeBrandKey(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase(Locale.ROOT)
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()

/**
 * Logo tile for a bank, issuer or wallet. Without a bundled image it shows the initials on the
 * brand color (or on teal for unknown institutions), so a missing asset never leaves a hole.
 */
@Composable
fun BrandLogo(
    brand: KipuBrand?,
    fallbackName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(size * 0.28f)
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val logo = brand?.logoRes
    if (logo != null) {
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(Color.White)
                .border(BorderStroke(1.dp, colors.borderSubtle), shape)
                .then(semantics),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f),
            )
        }
    } else {
        val initials = brand?.initials ?: fallbackInitials(fallbackName)
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(brand?.brandColor ?: colors.primaryDeep)
                .then(semantics),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * if (initials.length > 2) 0.28f else 0.36f).sp,
                maxLines = 1,
            )
        }
    }
}

internal fun fallbackInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase(Locale.ROOT)
        else -> (words[0].take(1) + words[1].take(1)).uppercase(Locale.ROOT)
    }
}

/**
 * Card thumbnail: the vector card art of the catalog ([CardArtCatalog], front side, no digits) when the
 * preset has one, otherwise a gradient tile in the preset colors with the tier label, so every card in
 * a list keeps the same shape.
 */
@Composable
fun CardArtThumbnail(
    presetId: String?,
    tierLabel: String,
    startColor: Color,
    endColor: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
) {
    val shape = RoundedCornerShape(width * 0.1f)
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val art = remember(presetId) { CardArtCatalog.forPreset(presetId) }
    val base = modifier.width(width).aspectRatio(art?.aspectRatio ?: 1.586f).clip(shape).then(semantics)
    if (art != null) {
        Image(
            painter = painterResource(art.front),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    } else {
        Box(
            base.background(Brush.linearGradient(listOf(startColor, endColor))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                tierLabel,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (width.value * 0.13f).coerceAtLeast(8f).sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = width * 0.08f, vertical = width * 0.06f),
            )
        }
    }
}
