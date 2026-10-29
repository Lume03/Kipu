package com.kipu.app.feature.capture.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.feature.capture.domain.CaptureWalletChannelResolver
import com.kipu.app.feature.capture.domain.CaptureWalletPayment
import javax.inject.Inject

/**
 * Mirrors the affiliated-wallet check of the movement repository: a channel is sent only when the
 * account really has an active wallet for it, so a standalone wallet registers as a plain movement.
 */
class RoomCaptureWalletChannelResolver @Inject constructor(
    private val accounts: AccountDao,
) : CaptureWalletChannelResolver {
    override suspend fun resolve(ownerId: String, selectedId: String, currency: String, provider: CaptureProvider): CaptureWalletPayment? {
        val selected = accounts.getById(ownerId, selectedId)?.takeIf {
            it.userId == ownerId && !it.isArchived && it.currency == currency
        } ?: return null
        if (selected.type == "DIGITAL_WALLET") {
            val parentId = selected.linkedAccountId ?: return CaptureWalletPayment(selected.id, null)
            val parent = accounts.getById(ownerId, parentId)?.takeIf {
                it.userId == ownerId && !it.isArchived && it.currency == currency && it.type in setOf("SAVINGS", "BANK")
            } ?: return null
            val channel = selected.presetId?.takeIf { it in setOf("YAPE", "PLIN") } ?: return null
            val source = com.kipu.app.feature.accounts.domain.model.MovementAccountSource.of(selected.id, parent.id, channel)
            return CaptureWalletPayment(source.accountId, source.paymentChannel)
        }
        // Extracted candidates already carry the parent account. Revalidate its actual affiliation.
        val channel = channelFor(ownerId, selected.id, provider) ?: return null
        val wallet = accounts.getActiveAffiliatedWallet(ownerId, selected.id, channel)?.takeIf {
            it.userId == ownerId && !it.isArchived && it.currency == currency && it.linkedAccountId == selected.id
        } ?: return null
        return CaptureWalletPayment(selected.id, wallet.presetId)
    }
    override suspend fun channelFor(ownerId: String, accountId: String, provider: CaptureProvider): String? {
        val channel = when (provider) {
            CaptureProvider.YAPE -> "YAPE"
            CaptureProvider.PLIN -> "PLIN"
            else -> return null
        }
        return channel.takeIf { accounts.getActiveAffiliatedWallet(ownerId, accountId, channel) != null }
    }
}
