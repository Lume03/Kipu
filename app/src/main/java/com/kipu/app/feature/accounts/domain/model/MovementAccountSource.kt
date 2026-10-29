package com.kipu.app.feature.accounts.domain.model

/** Same ledger routing for manual entry and capture: an affiliated wallet spends its parent's funds. */
data class MovementAccountSource(val accountId: String, val paymentChannel: String?) {
    companion object {
        fun of(selectedId: String, linkedAccountId: String?, preset: String?): MovementAccountSource =
            MovementAccountSource(linkedAccountId ?: selectedId, preset.takeIf { linkedAccountId != null })
    }
}
