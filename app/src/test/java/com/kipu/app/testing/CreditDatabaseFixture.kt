package com.kipu.app.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.OfflineFirstFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.data.local.*
import com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler
import com.kipu.app.feature.categories.data.local.CategoryEntity
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class CreditDatabaseFixture : AutoCloseable {
    val owner = "61000000-0000-4000-8000-000000000001"
    val cardId = CardId("61000000-0000-4000-8000-000000000010")
    val liabilityId = "61000000-0000-4000-8000-000000000020"
    val categoryId = "food"
    val context: Context = ApplicationProvider.getApplicationContext()
    val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
    val session = mockk<SessionCoordinator> {
        every { localAccess } returns MutableStateFlow(LocalAccess.Available(owner, RemoteSession.Absent))
        every { currentOwner } returns LocalOwner(owner)
    }
    val repository = OfflineFirstFinancialInstrumentsRepository(db, mockk(relaxed = true), db.accountDao(), db.cardDao(),
        db.financialMovementDao(), db.instrumentSyncDao(), session, mockk<InstrumentSyncScheduler>(relaxed = true))
    init { runBlocking {
        db.accountDao().insert(AccountEntity(liabilityId, owner, "liability-op", "Visa", "CREDIT_LIABILITY", "PEN",
            null, null, null, 0, 1, createdAt = 1, updatedAt = 1))
        db.cardDao().insert(CardEntity(cardId.value, owner, "card-op", liabilityId, "Visa", "CREDIT", "PEN", "VISA", "BCP", "1234",
            500000, 26, 19, createdAt = 1, updatedAt = 1, personalTeaBps = 4500))
        db.categoryDao().insertCategory(CategoryEntity(categoryId, null, null, "SYSTEM", createdAt = 1, updatedAt = 1, categoryType = "EXPENSE"))
    } }
    fun operation() = OperationId(UUID.randomUUID().toString())
    override fun close() = db.close()
}
