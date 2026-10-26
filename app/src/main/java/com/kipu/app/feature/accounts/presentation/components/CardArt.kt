package com.kipu.app.feature.accounts.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.kipu.app.R

/**
 * Illustrated (vector) plastic for a specific card product.
 *
 * The artwork is a Vector Drawable generated from the SVG sources in
 * `docs/design/cards/<product>/` (see `scripts/cards`). It carries no personal data: the masked last
 * four digits and the billing-cycle text are drawn by Compose on top, so the base drawable never changes.
 */
data class CardArt(
    val presetId: String,
    @DrawableRes val front: Int,
    @DrawableRes val back: Int,
    /** width / height of the plastic (ID-1 is 1.586). */
    val aspectRatio: Float,
    /** Corner radius as a fraction of the card width, to clip the vector like the physical card. */
    val cornerFraction: Float,
    /** Where the dynamic masked digits go on each side, as fractions of width/height. */
    val frontDigits: DigitsSlot,
    val backDigits: DigitsSlot,
    val frontDigitsColor: Color = Color.White,
    val backDigitsColor: Color = Color(0xFF1C2430),
    /** Top of the (optional) informative panel drawn over the reverse, as a fraction of height. */
    val backInfoTop: Float = 0.47f,
    val productName: String,
    val isIllustration: Boolean = true,
)

/** Text slot, right-aligned by default (left-aligned when [fromStart]). Insets are fractions of width/height. */
data class DigitsSlot(val endInset: Float, val top: Float, val fontFraction: Float, val fromStart: Boolean = false)

object CardArtCatalog {
    private const val VIEW_W = 1213f
    private const val VIEW_H = 766f

    private fun qore(
        presetId: String,
        productName: String,
        @DrawableRes front: Int,
        @DrawableRes back: Int,
        frontDigitsColor: Color = Color.White,
    ) = CardArt(
        presetId = presetId,
        front = front,
        back = back,
        aspectRatio = VIEW_W / VIEW_H,
        cornerFraction = 46f / VIEW_W,
        frontDigits = DigitsSlot(endInset = (VIEW_W - 1160f) / VIEW_W, top = 494f / VIEW_H, fontFraction = 0.034f),
        backDigits = DigitsSlot(endInset = (VIEW_W - 806f) / VIEW_W, top = 256f / VIEW_H, fontFraction = 0.034f),
        frontDigitsColor = frontDigitsColor,
        productName = productName,
    )

    /** BCP Visa Qore family: the front is measured from the public artwork; every reverse is a design approximation. */
    val BcpVisaClassicQore = qore("bcp-visa-qore-classic", "BCP Visa Clásica Qore", R.drawable.bcp_visa_classic_qore_front, R.drawable.bcp_visa_classic_qore_back)
    val BcpVisaGoldQore = qore("bcp-visa-qore-gold", "BCP Visa Oro Qore", R.drawable.bcp_visa_gold_qore_front, R.drawable.bcp_visa_gold_qore_back)
    val BcpVisaPlatinumQore = qore("bcp-visa-qore-platinum", "BCP Visa Platinum Qore", R.drawable.bcp_visa_platinum_qore_front, R.drawable.bcp_visa_platinum_qore_back)
    val BcpVisaSignatureQore = qore(
        "bcp-visa-qore-signature", "BCP Visa Signature Qore",
        R.drawable.bcp_visa_signature_qore_front, R.drawable.bcp_visa_signature_qore_back,
        frontDigitsColor = Color(0xFFD2D2D2),
    )
    val BcpVisaInfiniteQore = qore("bcp-visa-qore-infinite", "BCP Visa Infinite Qore", R.drawable.bcp_visa_infinite_qore_front, R.drawable.bcp_visa_infinite_qore_back)

    /** LATAM Pass family: digits go bottom-left, as on the printed card. */
    private fun latam(
        presetId: String,
        productName: String,
        @DrawableRes front: Int,
        @DrawableRes back: Int,
        digitsColor: Color = Color.White,
    ) = CardArt(
        presetId = presetId,
        front = front,
        back = back,
        aspectRatio = VIEW_W / VIEW_H,
        cornerFraction = 46f / VIEW_W,
        frontDigits = DigitsSlot(endInset = 60f / VIEW_W, top = 596f / VIEW_H, fontFraction = 0.034f, fromStart = true),
        backDigits = DigitsSlot(endInset = (VIEW_W - 806f) / VIEW_W, top = 256f / VIEW_H, fontFraction = 0.034f),
        frontDigitsColor = digitsColor,
        productName = productName,
    )

    val BcpVisaLatamClassic = latam("bcp-visa-latam-classic", "BCP Visa Clásica LATAM Pass", R.drawable.bcp_visa_latam_classic_front, R.drawable.bcp_visa_latam_classic_back)
    val BcpVisaLatamGold = latam("bcp-visa-latam-gold", "BCP Visa Oro LATAM Pass", R.drawable.bcp_visa_latam_gold_front, R.drawable.bcp_visa_latam_gold_back, Color(0xFF3B2A00))
    val BcpVisaLatamPlatinum = latam("bcp-visa-latam-platinum", "BCP Visa Platinum LATAM Pass", R.drawable.bcp_visa_latam_platinum_front, R.drawable.bcp_visa_latam_platinum_back, Color(0xFF20242B))
    val BcpVisaLatamSignature = latam("bcp-visa-latam-signature", "BCP Visa Signature LATAM Pass", R.drawable.bcp_visa_latam_signature_front, R.drawable.bcp_visa_latam_signature_back, Color(0xFFD9D6D1))
    val BcpVisaLatamIridium = latam("bcp-visa-latam-iridium", "BCP Visa Infinite Iridium LATAM Pass", R.drawable.bcp_visa_latam_iridium_front, R.drawable.bcp_visa_latam_iridium_back)
    val BcpVisaLatamSapphire = latam("bcp-visa-latam-sapphire", "BCP Visa Infinite Sapphire LATAM Pass", R.drawable.bcp_visa_latam_sapphire_front, R.drawable.bcp_visa_latam_sapphire_back)
    val BcpAmexLatamClassic = latam("bcp-amex-latam-classic", "BCP American Express Clásica LATAM Pass", R.drawable.bcp_amex_latam_classic_front, R.drawable.bcp_amex_latam_classic_back)
    val BcpAmexLatamGold = latam("bcp-amex-latam-gold", "BCP American Express Oro LATAM Pass", R.drawable.bcp_amex_latam_gold_front, R.drawable.bcp_amex_latam_gold_back, Color(0xFF3B2A00))
    val BcpAmexLatamPlatinum = latam("bcp-amex-latam-platinum", "BCP American Express Platinum LATAM Pass", R.drawable.bcp_amex_latam_platinum_front, R.drawable.bcp_amex_latam_platinum_back, Color(0xFF20242B))
    val BcpAmexLatamBlack = latam("bcp-amex-latam-black", "BCP American Express Black LATAM Pass", R.drawable.bcp_amex_latam_black_front, R.drawable.bcp_amex_latam_black_back)

    /** BCP products without a family of their own (debit, conventional Visa Clásica, Light, iO). */
    private fun bcpOther(
        presetId: String,
        productName: String,
        @DrawableRes front: Int,
        @DrawableRes back: Int,
        digitsColor: Color = Color.White,
        digitsTop: Float = 596f,
    ) = latam(presetId, productName, front, back, digitsColor).copy(
        frontDigits = DigitsSlot(endInset = 60f / VIEW_W, top = digitsTop / VIEW_H, fontFraction = 0.034f, fromStart = true),
    )

    val BcpDebit = bcpOther("bcp-debit-classic", "BCP Débito", R.drawable.bcp_debit_front, R.drawable.bcp_debit_back)
    val BcpVisaClassic = bcpOther("bcp-visa-classic", "BCP Visa Clásica", R.drawable.bcp_visa_classic_front, R.drawable.bcp_visa_classic_back)
    val BcpVisaLight = bcpOther("bcp-visa-light", "BCP Visa Light", R.drawable.bcp_visa_light_front, R.drawable.bcp_visa_light_back, Color(0xFF0B3A8F))
    val BcpVisaIo = bcpOther("bcp-visa-io", "BCP Visa iO", R.drawable.bcp_visa_io_front, R.drawable.bcp_visa_io_back, digitsTop = 70f)

    /** Interbank: same bottom-left digits slot as the other horizontal cards. */
    val InterbankVisaClassic = bcpOther("interbank-visa-classic", "Interbank Visa Clásica", R.drawable.interbank_visa_classic_front, R.drawable.interbank_visa_classic_back)
    val InterbankVisaGold = bcpOther("interbank-visa-gold", "Interbank Visa Oro", R.drawable.interbank_visa_gold_front, R.drawable.interbank_visa_gold_back, Color(0xFF2A1F00))
    val InterbankVisaPlatinum = bcpOther("interbank-visa-platinum", "Interbank Visa Platinum", R.drawable.interbank_visa_platinum_front, R.drawable.interbank_visa_platinum_back, Color(0xFF101418))
    val InterbankVisaSignature = bcpOther("interbank-visa-signature", "Interbank Visa Signature", R.drawable.interbank_visa_signature_front, R.drawable.interbank_visa_signature_back)
    val InterbankVisaInfinite = bcpOther("interbank-visa-infinite", "Interbank Visa Infinite", R.drawable.interbank_visa_infinite_front, R.drawable.interbank_visa_infinite_back, Color(0xFF101418))
    val InterbankVisaAccess = bcpOther("interbank-visa-access", "Interbank Visa Access", R.drawable.interbank_visa_access_front, R.drawable.interbank_visa_access_back, Color(0xFF06381B))
    val InterbankVisaPremia = bcpOther("interbank-visa-premia", "Interbank Visa Premia", R.drawable.interbank_visa_premia_front, R.drawable.interbank_visa_premia_back, Color(0xFF12306E))
    val InterbankMastercardClassic = bcpOther("interbank-mastercard-classic", "Interbank Mastercard Clásica", R.drawable.interbank_mastercard_classic_front, R.drawable.interbank_mastercard_classic_back, Color(0xFF06381B))
    val InterbankMastercardGold = bcpOther("interbank-mastercard-gold", "Interbank Mastercard Oro", R.drawable.interbank_mastercard_gold_front, R.drawable.interbank_mastercard_gold_back, Color(0xFF2A1F00))
    val InterbankMastercardPlatinum = bcpOther("interbank-mastercard-platinum", "Interbank Mastercard Platinum", R.drawable.interbank_mastercard_platinum_front, R.drawable.interbank_mastercard_platinum_back)
    val InterbankGuaranteed = bcpOther("interbank-guaranteed", "Interbank Tarjeta Garantía Líquida", R.drawable.interbank_guaranteed_front, R.drawable.interbank_guaranteed_back)
    val InterbankDebit = bcpOther("interbank-debit-classic", "Interbank Débito", R.drawable.interbank_debit_front, R.drawable.interbank_debit_back, Color(0xFF06381B))
    val InterbankAmexGreen = bcpOther("interbank-amex-green", "Interbank American Express Green", R.drawable.interbank_amex_green_front, R.drawable.interbank_amex_green_back, Color(0xFF12301F))
    val InterbankAmexGold = bcpOther("interbank-amex-gold", "Interbank American Express Gold", R.drawable.interbank_amex_gold_front, R.drawable.interbank_amex_gold_back, Color(0xFF2A1F00))
    val InterbankAmexPlatinum = bcpOther("interbank-amex-platinum", "Interbank American Express Platinum", R.drawable.interbank_amex_platinum_front, R.drawable.interbank_amex_platinum_back, Color(0xFF101418))
    val InterbankAmexBlack = bcpOther("interbank-amex-black", "Interbank American Express Black", R.drawable.interbank_amex_black_front, R.drawable.interbank_amex_black_back)
    val InterbankAmexThePlatinumCard = bcpOther("interbank-amex-the-platinum-card", "Interbank The Platinum Card", R.drawable.interbank_amex_the_platinum_card_front, R.drawable.interbank_amex_the_platinum_card_back, Color(0xFF191C1E))

    /** Verified BBVA / Scotia fronts recomposed horizontally; every reverse is illustrative. */
    private fun bankPlastic(
        presetId: String,
        productName: String,
        @DrawableRes front: Int,
        @DrawableRes back: Int,
        digitsColor: Color = Color.White,
    ) = CardArt(
        presetId = presetId,
        front = front,
        back = back,
        aspectRatio = VIEW_W / VIEW_H,
        cornerFraction = 46f / VIEW_W,
        frontDigits = DigitsSlot(endInset = 66f / VIEW_W, top = 596f / VIEW_H, fontFraction = 0.034f, fromStart = true),
        backDigits = DigitsSlot(endInset = (VIEW_W - 806f) / VIEW_W, top = 228f / VIEW_H, fontFraction = 0.034f),
        frontDigitsColor = digitsColor,
        backInfoTop = 0.51f,
        productName = productName,
    )

    val BbvaDebitClassic = bankPlastic("bbva-debit-classic", "Débito BBVA", R.drawable.bbva_debit_classic_front, R.drawable.bbva_debit_classic_back, Color(0xFF082228))
    val BbvaVisaBfree = bankPlastic("bbva-visa-bfree", "BBVA Bfree Visa", R.drawable.bbva_visa_bfree_front, R.drawable.bbva_visa_bfree_back, Color(0xFF104984))
    val BbvaMastercardBfree = bankPlastic("bbva-mastercard-bfree", "BBVA Bfree Mastercard", R.drawable.bbva_mastercard_bfree_front, R.drawable.bbva_mastercard_bfree_back, Color(0xFF104984))
    val BbvaVisaCero = bankPlastic("bbva-visa-cero", "BBVA Cero Visa", R.drawable.bbva_visa_cero_front, R.drawable.bbva_visa_cero_back, Color(0xFF123D65))
    val BbvaVisaPlatinum = bankPlastic("bbva-visa-platinum", "BBVA Platinum Visa", R.drawable.bbva_visa_platinum_front, R.drawable.bbva_visa_platinum_back)
    val BbvaMastercardPlatinum = bankPlastic("bbva-mastercard-platinum", "BBVA Platinum Mastercard", R.drawable.bbva_mastercard_platinum_front, R.drawable.bbva_mastercard_platinum_back)
    val BbvaVisaSignature = bankPlastic("bbva-visa-signature", "BBVA Visa Signature", R.drawable.bbva_visa_signature_front, R.drawable.bbva_visa_signature_back)
    val BbvaVisaInfinite = bankPlastic("bbva-visa-infinite", "BBVA Visa Infinite", R.drawable.bbva_visa_infinite_front, R.drawable.bbva_visa_infinite_back)
    val BbvaMastercardBlack = bankPlastic("bbva-mastercard-black", "BBVA Mastercard Black", R.drawable.bbva_mastercard_black_front, R.drawable.bbva_mastercard_black_back)

    val ScotiabankDebitClassic = bankPlastic("scotiabank-debit-classic", "Débito Scotiabank", R.drawable.scotiabank_debit_classic_front, R.drawable.scotiabank_debit_classic_back)
    val ScotiabankSingularSignature = bankPlastic("scotiabank-singular-signature", "Scotiabank Visa Singular Signature", R.drawable.scotiabank_singular_signature_front, R.drawable.scotiabank_singular_signature_back, Color(0xFFD4D2D0))
    val ScotiabankSingularInfinite = bankPlastic("scotiabank-singular-infinite", "Scotiabank Visa Singular Infinite", R.drawable.scotiabank_singular_infinite_front, R.drawable.scotiabank_singular_infinite_back, Color(0xFF252321))
    val ScotiabankSingularBlack = bankPlastic("scotiabank-singular-black", "Scotiabank Mastercard Singular Black", R.drawable.scotiabank_singular_black_front, R.drawable.scotiabank_singular_black_back, Color(0xFFD4D2D0))

    /** Banco Falabella, Banco Ripley and BanBif. */
    val FalabellaCmrVisaClassic = bcpOther("falabella-cmr-visa-classic", "Banco Falabella CMR Visa", R.drawable.falabella_cmr_visa_classic_front, R.drawable.falabella_cmr_visa_classic_back, Color(0xFF06260A))
    val FalabellaCmrVisaBasic = bcpOther("falabella-cmr-visa-basic", "Banco Falabella CMR Visa Básica", R.drawable.falabella_cmr_visa_basic_front, R.drawable.falabella_cmr_visa_basic_back, Color(0xFF06260A))
    val FalabellaCmrVisaPlatinum = bcpOther("falabella-cmr-visa-platinum", "Banco Falabella CMR Visa Platinum", R.drawable.falabella_cmr_visa_platinum_front, R.drawable.falabella_cmr_visa_platinum_back, Color(0xFF0F1A1A))
    val FalabellaCmrVisaSignature = bcpOther("falabella-cmr-visa-signature", "Banco Falabella CMR Visa Signature", R.drawable.falabella_cmr_visa_signature_front, R.drawable.falabella_cmr_visa_signature_back)
    val FalabellaCmrGuaranteed = bcpOther("falabella-cmr-guaranteed", "Banco Falabella CMR con Garantía", R.drawable.falabella_cmr_guaranteed_front, R.drawable.falabella_cmr_guaranteed_back)
    val FalabellaDebitClassic = bcpOther("falabella-debit-classic", "Banco Falabella Débito", R.drawable.falabella_debit_classic_front, R.drawable.falabella_debit_classic_back)
    val RipleyMastercardClassic = bcpOther("ripley-mastercard-classic", "Banco Ripley Mastercard", R.drawable.ripley_mastercard_classic_front, R.drawable.ripley_mastercard_classic_back)
    val RipleyDebitMastercard = bcpOther("ripley-debit-mastercard", "Banco Ripley Débito", R.drawable.ripley_debit_mastercard_front, R.drawable.ripley_debit_mastercard_back)
    val BanbifDebitClassic = bcpOther("banbif-debit-classic", "BanBif Débito", R.drawable.banbif_debit_classic_front, R.drawable.banbif_debit_classic_back)
    val BanbifVisaCero = bcpOther("banbif-visa-cero", "BanBif Cero Membresía Visa", R.drawable.banbif_visa_cero_front, R.drawable.banbif_visa_cero_back, Color(0xFF00386B))
    val BanbifVisaClassic = bcpOther("banbif-visa-classic", "BanBif Visa Clásica", R.drawable.banbif_visa_classic_front, R.drawable.banbif_visa_classic_back)
    val BanbifVisaGold = bcpOther("banbif-visa-gold", "BanBif Visa Oro", R.drawable.banbif_visa_gold_front, R.drawable.banbif_visa_gold_back, Color(0xFF001E3D))
    val BanbifVisaPlatinum = bcpOther("banbif-visa-platinum", "BanBif Visa Platinum", R.drawable.banbif_visa_platinum_front, R.drawable.banbif_visa_platinum_back)
    val BanbifVisaSignature = bcpOther("banbif-visa-signature", "BanBif Visa Signature", R.drawable.banbif_visa_signature_front, R.drawable.banbif_visa_signature_back)
    val BanbifVisaInfinite = bcpOther("banbif-visa-infinite", "BanBif Visa Infinite", R.drawable.banbif_visa_infinite_front, R.drawable.banbif_visa_infinite_back)

    private val all: List<CardArt> = listOf(
        BbvaDebitClassic, BbvaVisaBfree, BbvaMastercardBfree, BbvaVisaCero,
        BbvaVisaPlatinum, BbvaMastercardPlatinum, BbvaVisaSignature, BbvaVisaInfinite, BbvaMastercardBlack,
        ScotiabankDebitClassic, ScotiabankSingularSignature, ScotiabankSingularInfinite, ScotiabankSingularBlack,
        BcpVisaClassicQore, BcpVisaGoldQore, BcpVisaPlatinumQore, BcpVisaSignatureQore, BcpVisaInfiniteQore,
        BcpVisaLatamClassic, BcpVisaLatamGold, BcpVisaLatamPlatinum, BcpVisaLatamSignature, BcpVisaLatamIridium, BcpVisaLatamSapphire,
        BcpAmexLatamClassic, BcpAmexLatamGold, BcpAmexLatamPlatinum, BcpAmexLatamBlack,
        BcpDebit, BcpVisaClassic, BcpVisaLight, BcpVisaIo,
        InterbankVisaClassic,
        InterbankVisaGold,
        InterbankVisaPlatinum,
        InterbankVisaSignature,
        InterbankVisaInfinite,
        InterbankVisaAccess,
        InterbankVisaPremia,
        InterbankMastercardClassic,
        InterbankMastercardGold,
        InterbankMastercardPlatinum,
        InterbankGuaranteed,
        InterbankDebit,
        InterbankAmexGreen,
        InterbankAmexGold,
        InterbankAmexPlatinum,
        InterbankAmexBlack,
        InterbankAmexThePlatinumCard,
        FalabellaCmrVisaClassic,
        FalabellaCmrVisaBasic,
        FalabellaCmrVisaPlatinum,
        FalabellaCmrVisaSignature,
        FalabellaCmrGuaranteed,
        FalabellaDebitClassic,
        RipleyMastercardClassic,
        RipleyDebitMastercard,
        BanbifDebitClassic,
        BanbifVisaCero,
        BanbifVisaClassic,
        BanbifVisaGold,
        BanbifVisaPlatinum,
        BanbifVisaSignature,
        BanbifVisaInfinite,
    )

    /** Resolved by the persisted `style_preset_id`; other products keep the generic native style. */
    fun forPreset(presetId: String?): CardArt? = presetId?.let { id -> all.firstOrNull { it.presetId == id } }

    val presetIds: Set<String> get() = all.mapTo(linkedSetOf()) { it.presetId }
}

/** The front or back plastic with its dynamic overlays. Sized by width; height follows [CardArt.aspectRatio]. */
@Composable
fun CardArtFace(
    art: CardArt,
    back: Boolean,
    lastFourDigits: String?,
    modifier: Modifier = Modifier,
    backInfo: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(art.aspectRatio),
    ) {
        val width = maxWidth
        val height = maxHeight
        val shape = RoundedCornerShape(width * art.cornerFraction)
        Box(Modifier.matchParentSize().clip(shape)) {
            Image(
                painter = painterResource(if (back) art.back else art.front),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
            val slot = if (back) art.backDigits else art.frontDigits
            val digitsColor = if (back) art.backDigitsColor else art.frontDigitsColor
            if (!lastFourDigits.isNullOrBlank()) {
                Text(
                    text = "•••• $lastFourDigits",
                    color = digitsColor,
                    fontSize = (width.value * slot.fontFraction).coerceAtLeast(11f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .align(if (slot.fromStart) Alignment.TopStart else Alignment.TopEnd)
                        .padding(
                            start = if (slot.fromStart) width * slot.endInset else width * 0f,
                            end = if (slot.fromStart) width * 0f else width * slot.endInset,
                            top = height * slot.top,
                        ),
                )
            }
            if (back && backInfo != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = width * 0.06f, end = width * 0.06f, top = height * art.backInfoTop),
                ) { backInfo() }
            }
        }
    }
}
