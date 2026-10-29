package com.kipu.app.feature.capture.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureInstrumentResolution
import com.kipu.app.feature.capture.domain.CaptureInstrumentResolver
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.feature.capture.domain.ParsedCapture
import javax.inject.Inject

class RoomCaptureInstrumentResolver @Inject constructor(
    private val accounts: AccountDao,
    private val cards: CardDao,
) : CaptureInstrumentResolver {
    override suspend fun resolve(ownerId: String, provider: CaptureProvider, parsed: ParsedCapture): CaptureInstrumentResolution {
        val activeAccounts = accounts.getActiveComputable(ownerId)
        if (parsed.cardLast4 != null) {
            val matches = cards.getActive(ownerId).filter { card ->
                card.lastFourDigits == parsed.cardLast4 &&
                    (card.type == "CREDIT" || activeAccounts.any { it.id == card.accountId })
            }
            if (matches.size > 1) return CaptureInstrumentResolution(ambiguous = true)
            val card = matches.singleOrNull() ?: return CaptureInstrumentResolution(kind = parsed.instrumentKind)
            if (parsed.currency != null && card.currency != parsed.currency) return CaptureInstrumentResolution(kind = parsed.instrumentKind)
            val kind = if (card.type == "CREDIT") CaptureInstrumentKind.CREDIT_CARD else CaptureInstrumentKind.DEBIT_ACCOUNT
            // Conflicting card-type evidence must be reviewed, never silently reinterpreted.
            if (parsed.instrumentKind != null && parsed.instrumentKind != kind) {
                return CaptureInstrumentResolution(kind = parsed.instrumentKind, ambiguous = true)
            }
            return CaptureInstrumentResolution(kind, card.accountId, card.id)
        }
        if (provider == CaptureProvider.YAPE || provider == CaptureProvider.PLIN) {
            // accounts.preset_id stores the AccountPreset name (e.g. "YAPE"), not its color token.
            val preset = if (provider == CaptureProvider.YAPE) AccountPreset.YAPE.name else AccountPreset.PLIN.name
            val wallets = activeAccounts.filter {
                it.type == "DIGITAL_WALLET" && it.presetId == preset
            }
            val wallet = wallets.singleOrNull()
                ?.takeIf { parsed.currency == null || it.currency == parsed.currency }
            // A linked wallet draws on its actual bank account; standalone Yape uses its own balance.
            val accountId = wallet?.let { it.linkedAccountId ?: it.id }
                ?.takeIf { id -> activeAccounts.any { it.id == id } }
            return CaptureInstrumentResolution(CaptureInstrumentKind.WALLET, accountId, ambiguous = wallets.size > 1)
        }
        // Accounts do not store account-number suffixes. Never match an alias or invent a suffix.
        return CaptureInstrumentResolution(kind = parsed.instrumentKind)
    }
}
