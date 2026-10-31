package com.kipu.app.feature.movements.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.navigation.ReceiptShareIntent
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.receipts.data.AndroidReceiptImageRecognizer
import com.kipu.app.feature.receipts.data.RoomReceiptOcrConsent
import com.kipu.app.feature.receipts.data.local.RoomReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.*
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real ML Kit + parser + candidate Room + existing RegisterTransaction/ledger, without network. */
class YapeReceiptLedgerAcceptanceTest {
    private lateinit var db: KipuDatabase
    private lateinit var store: RoomReceiptCandidateStore
    private lateinit var confirm: ConfirmReceiptCandidate
    private val owner = UUID.randomUUID().toString()
    private val account = UUID.randomUUID().toString()
    private val category = UUID.randomUUID().toString()

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        store = RoomReceiptCandidateStore(db, db.receiptCandidateDao())
        val projection = BalanceProjectionStore(db.movementDao())
        val repo = OfflineFirstMovementRepository(
            MovementLocalDataSource(db, db.movementDao(), projection), projection,
            TransactionRequestHasher(), MovementRevisionRequestHasher(), mockk<MovementSyncScheduler>(relaxed = true),
            db.accountDao(), db.cardDao(), db.categoryDao(), db.merchantCatalogDao(),
        )
        confirm = ConfirmReceiptCandidate(store, RegisterTransaction(repo, RegisterTransactionValidator()))
        db.categoryDao().insertCategory(CategoryEntity(category, null, null, "SYSTEM", createdAt = 1, updatedAt = 1))
        db.accountDao().insert(AccountEntity(
            id = account, userId = owner, creationOperationId = UUID.randomUUID().toString(),
            alias = "Cuenta de prueba", type = "SAVINGS", currency = "PEN", presetId = null,
            color = null, icon = null, initialBalanceMinorUnits = 0, openedAt = 1, createdAt = 1, updatedAt = 1,
        ))
        db.accountDao().insert(AccountEntity(
            id = UUID.randomUUID().toString(), userId = owner, creationOperationId = UUID.randomUUID().toString(),
            alias = "Yape", type = "DIGITAL_WALLET", currency = "PEN", presetId = "YAPE",
            color = null, icon = null, initialBalanceMinorUnits = 0, openedAt = 1, createdAt = 1, updatedAt = 1,
            linkedAccountId = account,
        ))
    }
    @After fun teardown() { db.close() }

    @Test fun localOcrToCandidateToConfirmedExpenseChangesBalanceExactlyOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".yape-fixture"
        val shared = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://$authority/voucher.png"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val uri = requireNotNull(ReceiptShareIntent.imageUri(shared))
        val consent = RoomReceiptOcrConsent(db.permissionConsentDao())
        consent.grant(owner)
        val importer = ImportYapeReceipt(ReceiptScanAccess { true }, AndroidReceiptImageRecognizer(context), YapeReceiptParser(), store, consent)
        val imported = importer(owner, uri, userConsented = true)
        assertTrue("Share image must become an OCR candidate", imported is ReceiptImportResult.CandidateCreated)
        val candidate = (imported as ReceiptImportResult.CandidateCreated).candidate
        assertEquals(1250L, candidate.amountMinor)
        assertNotNull(candidate.occurredAt)
        assertNull(db.movementDao().getBalanceProjection(owner, account))
        val result = confirm(owner, candidate.id, command(candidate, MovementType.EXPENSE))
        assertTrue("Confirmation must use the existing financial registration", result is RegisterTransactionResult.Success)
        assertEquals(-1250L, db.movementDao().calculateLedgerSumForAccount(owner, account))
        assertEquals(-1250L, db.movementDao().getBalanceProjection(owner, account)?.balanceMinor)
        assertEquals(ReceiptCandidateStatus.CONFIRMED, store.get(owner, candidate.id)?.status)
        assertTrue(importer(owner, uri, userConsented = true) is ReceiptImportResult.Duplicate)
        confirm(owner, candidate.id, command(candidate, MovementType.EXPENSE))
        assertEquals(-1250L, db.movementDao().calculateLedgerSumForAccount(owner, account))
    }

    @Test fun distinctIncomeWithSameAmountWarnsAndOnlyChangesBalanceAfterExplicitOverride() = runBlocking {
        val parser = YapeReceiptParser()
        val fields = parser.parse("Te Yapearon!\nS/ 20\nPersona Prueba\n21 oct. 2026 12:40 a. m.\nNro. de operación\n01234567")
        val first = (store.import(owner, "income-one", fields, true) as ReceiptCandidateSaveResult.Saved).candidate
        assertTrue(confirm(owner, first.id, command(first, MovementType.INCOME)) is RegisterTransactionResult.Success)
        val second = (store.import(owner, "income-two", fields.copy(reference = "01234568"), true) as ReceiptCandidateSaveResult.Saved).candidate
        val proposed = command(second, MovementType.INCOME)
        assertTrue(confirm(owner, second.id, proposed) is RegisterTransactionResult.SimilarTransactionWarning)
        assertEquals(2000L, db.movementDao().calculateLedgerSumForAccount(owner, account))
        assertEquals(ReceiptCandidateStatus.PENDING, store.get(owner, second.id)?.status)
        assertTrue(confirm(owner, second.id, proposed.copy(ignoreSimilarityWarning = true)) is RegisterTransactionResult.Success)
        assertEquals(4000L, db.movementDao().calculateLedgerSumForAccount(owner, account))
        assertEquals(4000L, db.movementDao().getBalanceProjection(owner, account)?.balanceMinor)
    }

    private fun command(candidate: ReceiptCandidate, type: MovementType) = RegisterTransactionCommand(
        idempotencyKey = "caller-key", userId = owner, type = type, amountMinor = requireNotNull(candidate.amountMinor),
        currency = "PEN", sourceAccountId = account, categoryId = category,
        merchantProvisionalText = candidate.recipient, occurredAt = requireNotNull(candidate.occurredAt), paymentChannel = "YAPE",
    )

}
