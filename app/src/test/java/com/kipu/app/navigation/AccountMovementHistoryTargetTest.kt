package com.kipu.app.navigation

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.movements.domain.model.WalletPaymentChannel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountMovementHistoryTargetTest {
    @Test
    fun bankAccountHistoryUsesOnlyItsAccountId() {
        val account = account(AccountType.BANK, AccountPreset.BCP)

        val target = account.toMovementHistoryTarget()

        assertEquals(account.id.value, target?.accountId)
        assertNull(target?.paymentChannel)
    }

    @Test
    fun walletHistoryUsesOnlyItsPaymentChannel() {
        val yape = account(AccountType.DIGITAL_WALLET, AccountPreset.YAPE)
        val plin = account(AccountType.DIGITAL_WALLET, AccountPreset.PLIN)

        val yapeTarget = yape.toMovementHistoryTarget()
        val plinTarget = plin.toMovementHistoryTarget()

        assertNull(yapeTarget?.accountId)
        assertEquals(WalletPaymentChannel.YAPE, yapeTarget?.paymentChannel)
        assertNull(plinTarget?.accountId)
        assertEquals(WalletPaymentChannel.PLIN, plinTarget?.paymentChannel)
    }

    private fun account(type: AccountType, preset: AccountPreset) = Account(
        id = AccountId.generate(),
        userId = UserId.generate(),
        alias = "Cuenta de prueba",
        type = type,
        currency = Currency.PEN,
        preset = preset,
        initialBalance = Money(0L, Currency.PEN),
        openedAt = Instant.EPOCH,
    )
}
