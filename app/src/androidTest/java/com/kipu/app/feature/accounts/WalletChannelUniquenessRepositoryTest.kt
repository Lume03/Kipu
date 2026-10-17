package com.kipu.app.feature.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.OfflineFirstFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletChannelUniquenessRepositoryTest {
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstFinancialInstrumentsRepository

    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OfflineFirstFinancialInstrumentsRepository(
            database = database,
            financialApi = mockk<FinancialInstrumentsApi>(relaxed = true),
            accountDao = database.accountDao(),
            cardDao = database.cardDao(),
            movementDao = database.financialMovementDao(),
            syncDao = database.instrumentSyncDao(),
            sessionCoordinator = TestSessionCoordinator(userId),
            syncScheduler = mockk<InstrumentSyncScheduler>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onlyOneActiveWalletPerChannel_canArchiveAndReplaceButCannotReactivateDuplicate() = runTest {
        val yape = newWallet(AccountPreset.YAPE, "Yape principal")
        assertTrue(repository.createLiquidAccount(yape, OperationId.generate()).isSuccess)

        val duplicateYape = repository.createLiquidAccount(
            newWallet(AccountPreset.YAPE, "Yape duplicado"),
            OperationId.generate(),
        )
        assertTrue(duplicateYape.exceptionOrNull()?.message.orEmpty(), duplicateYape.isFailure)
        assertEquals(1, database.accountDao().observeAll(userId).first().count { it.presetId == "YAPE" })

        val plin = newWallet(AccountPreset.PLIN, "Plin principal")
        assertTrue(repository.createLiquidAccount(plin, OperationId.generate()).isSuccess)

        assertTrue(repository.archiveInstrument(
            instrumentId = yape.id.value,
            isCard = false,
            operationId = OperationId.generate(),
        ).isSuccess)
        val replacementYape = newWallet(AccountPreset.YAPE, "Yape renovado")
        assertTrue(repository.createLiquidAccount(replacementYape, OperationId.generate()).isSuccess)

        val reactivateDuplicate = repository.reactivateInstrument(
            yape.id.value,
            isCard = false,
            operationId = OperationId.generate(),
        )
        assertTrue(reactivateDuplicate.exceptionOrNull()?.message.orEmpty(), reactivateDuplicate.isFailure)
        assertTrue(database.accountDao().getById(userId, yape.id.value)?.isArchived == true)
        assertEquals(
            1,
            database.accountDao().observeAll(userId).first().count { it.presetId == "YAPE" && !it.isArchived },
        )

        val changePlinToYape = repository.updateAccountAppearance(
            accountId = plin.id,
            alias = plin.alias,
            preset = AccountPreset.YAPE,
            colorToken = plin.colorToken,
            iconToken = plin.iconToken,
            operationId = OperationId.generate(),
        )
        assertTrue(changePlinToYape.exceptionOrNull()?.message.orEmpty(), changePlinToYape.isFailure)
        assertEquals("PLIN", database.accountDao().getById(userId, plin.id.value)?.presetId)
    }

    private fun newWallet(preset: AccountPreset, alias: String) = Account(
        id = AccountId.generate(),
        userId = UserId(userId),
        alias = alias,
        type = AccountType.DIGITAL_WALLET,
        currency = Currency.PEN,
        preset = preset,
        initialBalance = Money(0L, Currency.PEN),
        openedAt = Instant.now(),
    )

    private class TestSessionCoordinator(userId: String) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow<LocalAccess>(LocalAccess.Available(userId, RemoteSession.Absent))
        override val currentOwner = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }
}
