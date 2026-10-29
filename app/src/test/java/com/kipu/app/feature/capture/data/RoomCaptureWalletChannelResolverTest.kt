package com.kipu.app.feature.capture.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.feature.capture.domain.CaptureWalletPayment
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomCaptureWalletChannelResolverTest {
    private val dao = mockk<AccountDao>()
    private val resolver = RoomCaptureWalletChannelResolver(dao)
    private fun account(id: String, type: String = "BANK", parent: String? = null) = AccountEntity(
        id, "owner", "op-$id", id, type, "PEN", if (type == "DIGITAL_WALLET") "YAPE" else "BCP",
        null, null, 0, 0, createdAt = 0, updatedAt = 0, linkedAccountId = parent,
    )

    @Test fun `explicit affiliated wallet spends parent with its channel`() = runTest {
        coEvery { dao.getById("owner", "wallet") } returns account("wallet", "DIGITAL_WALLET", "bank")
        coEvery { dao.getById("owner", "bank") } returns account("bank")
        assertEquals(CaptureWalletPayment("bank", "YAPE"), resolver.resolve("owner", "wallet", "PEN", CaptureProvider.BCP))
    }

    @Test fun `standalone wallet retains its own account and has no affiliated channel`() = runTest {
        coEvery { dao.getById("owner", "wallet") } returns account("wallet", "DIGITAL_WALLET")
        assertEquals(CaptureWalletPayment("wallet", null), resolver.resolve("owner", "wallet", "PEN", CaptureProvider.YAPE))
    }

    @Test fun `changed link owner currency or archived account cannot authorize wallet movement`() = runTest {
        coEvery { dao.getById("owner", "wallet") } returns account("wallet", "DIGITAL_WALLET", "bank")
        for (parent in listOf(null, account("bank").copy(userId = "other"), account("bank").copy(currency = "USD"), account("bank").copy(isArchived = true))) {
            coEvery { dao.getById("owner", "bank") } returns parent
            assertNull(resolver.resolve("owner", "wallet", "PEN", CaptureProvider.YAPE))
        }
        coEvery { dao.getById("owner", "wallet") } returns account("wallet", "DIGITAL_WALLET", "bank").copy(isArchived = true)
        assertNull(resolver.resolve("owner", "wallet", "PEN", CaptureProvider.YAPE))
    }
}
