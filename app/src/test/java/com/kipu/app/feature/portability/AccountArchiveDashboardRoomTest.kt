package com.kipu.app.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.sync.*
import com.kipu.app.feature.goals.data.sync.*
import com.kipu.app.feature.debts.data.sync.*
import com.kipu.app.feature.budgets.data.sync.*
import com.kipu.app.feature.budgets.data.remote.*
import com.kipu.app.feature.recurrences.data.sync.*
import com.kipu.app.feature.recurrences.data.remote.*
import com.kipu.app.feature.recurrences.data.repository.OfflineFirstRecurrenceRepository
import com.kipu.app.feature.categories.data.sync.*
import com.kipu.app.feature.categories.data.remote.*
import io.mockk.*
import java.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.presentation.dashboard.availableMoneyByCurrency
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AccountArchiveDashboardRoomTest {
    private val owner = "5d88af4d-6bf2-4b75-8a43-ac7db0182fca"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KipuDatabase
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
    private val api = mockk<FinancialInstrumentsApi>()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun pullArchiveAndReactivateChangesAvailableMoneyWithoutChangingLedger() = runTest {
        val hydrate = MovementHydration(db.movementDao(), api, db.accountDao(), db.cardDao(), db, session)
        fun account(id: String, type: String, amount: Long, seq: Long, revision: Long = 1, archived: Boolean = false) =
            SyncChangeItemDto(seq,"ACCOUNT",id,revision,"UPSERT",Json.parseToJsonElement(
                """{"id":"$id","alias":"QA","type":"$type","currency":"PEN","initial_balance_minor_units":$amount,"opened_at":"2026-11-01T00:00:00Z","is_archived":$archived}"""))
        suspend fun pull(vararg changes: SyncChangeItemDto) {
            coEvery { api.pullChanges(any(), any()) } answers {
                val cursor = firstArg<PullChangesRequestDto>().afterSequence
                FinancialApiResponse.Success(PullChangesResponseDto(changes.filter { it.sequence > cursor }, maxOf(cursor,changes.last().sequence)))
            }
            assertTrue(hydrate.pullChanges(owner))
        }
        suspend fun available(): Long {
            val rows = db.accountDao().observeAll(owner).first().map { row ->
                val amount = db.movementDao().getBalanceProjection(owner,row.id)!!.balanceMinor
                AccountWithBalance(Account(id=AccountId(row.id),userId=UserId(owner),alias=row.alias,
                    type=AccountType.valueOf(row.type),currency=Currency.PEN,initialBalance=Money(row.initialBalanceMinorUnits,Currency.PEN),
                    openedAt=Instant.ofEpochMilli(row.openedAt),isArchived=row.isArchived),Money(amount,Currency.PEN))
            }
            return availableMoneyByCurrency(rows).getValue(Currency.PEN)
        }
        pull(account("72000000-0000-4000-8000-000000000001","CASH",5000,1),account("72000000-0000-4000-8000-000000000002","SAVINGS",10000,2))
        assertEquals(15000L,available())
        pull(account("72000000-0000-4000-8000-000000000002","SAVINGS",10000,3,2,true))
        assertEquals(5000L,available())
        assertEquals(10000L,db.movementDao().getBalanceProjection(owner,"72000000-0000-4000-8000-000000000002")?.balanceMinor)
        assertEquals(10000L,db.movementDao().calculateLedgerSumForAccount(owner,"72000000-0000-4000-8000-000000000002"))
        pull(account("72000000-0000-4000-8000-000000000002","SAVINGS",10000,4,3,false))
        assertEquals(15000L,available())
        assertEquals(10000L,db.movementDao().calculateLedgerSumForAccount(owner,"72000000-0000-4000-8000-000000000002"))
    }
}
