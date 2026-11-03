package com.kipu.app.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class SupplementaryHydrationRoomTest {
    @Test fun repeatedHistoryRulesAndOpeningFactsDoNotDuplicateOrDoublePost() = runTest {
        val owner = "5d88af4d-6bf2-4b75-8a43-ac7db0182fca"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        val bodies = mapOf(
            "transaction_revisions" to """[{"id":"r","user_id":"$owner","transaction_id":"t","command_id":null,"command_type":"REGISTER_TRANSACTION","base_revision":0,"local_revision":1,"previous_payload":null,"new_payload":{"amount_minor":100},"change_reason":null,"provenance":"REMOTE","created_at":"2026-11-01T00:00:00Z"}]""",
            "movement_official_revisions" to """[{"user_id":"$owner","transaction_id":"t","official_revision":1,"revision_id":"r","official_revision_id":"r","assigned_at":"2026-11-01T00:00:00Z"}]""",
            "financial_movements" to """[{"id":"opening","operation_id":"op","operation_sequence":0,"user_id":"$owner","kind":"OPENING","amount_minor_units":1000,"currency":"PEN","account_id":"cash","opening_account_id":"cash","effective_at":"2026-11-01T00:00:00Z","status":"POSTED","created_at":"2026-11-01T00:00:00Z"}]""",
            "merchant_alias_rules" to """[{"id":"alias","user_id":"$owner","normalized_pattern":"pan","merchant_id":"merchant","revision":1,"updated_at":"2026-11-01T00:00:00Z","deleted_at":null}]""",
        )
        val client = HttpClient(MockEngine { request ->
            assertEquals("eq.$owner", request.url.parameters["user_id"])
            respond(bodies[request.url.encodedPath.substringAfterLast('/')].orEmpty().ifEmpty { "[]" }, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }; defaultRequest { url("https://kipu.test/") } }
        try {
            db.accountDao().insert(AccountEntity("cash", owner, "create", "Caja", "CASH", "PEN", null, null, null, 1000, 1, createdAt = 1, updatedAt = 1))
            db.movementDao().insertTransactions(listOf(TransactionEntity("t", owner, "INCOME", 100, "PEN", occurredAt = 1)))
            db.movementDao().insertPulledLedgerEntries(listOf(com.kipu.app.feature.movements.data.local.LedgerEntryEntity("remote:opening:cash", owner, "remote:opening:cash", "cash", "DESTINATION", 1000, "PEN", 1)))
            val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
            val auth = mockk<AuthenticatedSessionProvider> { coEvery { currentSession() } returns AuthenticatedSession(UUID.fromString(owner), "token") }
            val hydration = SupplementaryHydration(client, auth, session, db)
            repeat(2) { assertTrue(hydration.movementHistory(owner)); assertTrue(hydration.financialFacts(owner)); assertTrue(hydration.merchantRules(owner)) }
            fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
            assertEquals(1, count("transaction_revisions")); assertEquals(1, count("movement_official_revisions"))
            assertEquals(1, count("financial_movements")); assertEquals(1, count("merchant_alias_rules"))
            assertEquals(1, count("ledger_entries")); assertEquals(1000L, db.movementDao().calculateLedgerSumForAccount(owner, "cash"))
        } finally { client.close(); db.close() }
    }
}
