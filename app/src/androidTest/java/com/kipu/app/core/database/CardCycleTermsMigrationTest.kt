package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardCycleTermsMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test fun migration30To31PreservesPreferredDaysAndDefaultsToQoreOperationBasis() {
        val name = "card-cycle-30-31"
        helper.createDatabase(name, 30).use { seedCard(it) }
        // Validates the complete schema against exported 31.json, including defaults.
        helper.runMigrationsAndValidate(name, 31, true, MIGRATION_30_31).use { db ->
            db.query("SELECT billing_day,due_day,closing_day_inclusive,cycle_date_basis FROM cards").use {
                assertTrue(it.moveToFirst())
                assertEquals(26, it.getInt(0)); assertEquals(19, it.getInt(1))
                assertEquals(0, it.getInt(2)); assertEquals("OPERATION", it.getString(3))
            }
        }
    }
    @Test fun migration31To32BackfillsTermsClosingDatesAndPreservesDueDates() {
        val name = "card-terms-31-32"
        helper.createDatabase(name, 31).use { db ->
            seedCard(db)
            seedPurchase(db, "purchase", "2026-06-26T12:00:00Z", 3)
            for (n in 1..3) seedInstallment(db, "i$n", "purchase", n)
        }
        // Validates the complete schema against exported 32.json, including allocation FK cascade.
        helper.runMigrationsAndValidate(name, 32, true, MIGRATION_31_32).use { db ->
            db.query("SELECT installment_number,purchase_type,financing,installments_count,tea_bps,rate_source,rounding_policy,billing_closing_date,due_date FROM credit_installments ORDER BY installment_number").use {
                for (n in 1..3) {
                    assertTrue(it.moveToNext()); assertEquals(n, it.getInt(0))
                    assertEquals("INSTALLMENTS", it.getString(1)); assertEquals("INTEREST_FREE", it.getString(2))
                    assertEquals(3, it.getInt(3)); assertTrue(it.isNull(4)); assertTrue(it.isNull(5))
                    assertEquals("LAST_INSTALLMENT_RESIDUAL", it.getString(6))
                    assertEquals(LocalDate.of(2026, 7, 26).plusMonths(n - 1L).toEpochDay(), it.getLong(7))
                    assertEquals(20000L, it.getLong(8)) // Existing contractual due dates are not recalculated.
                }
            }
        }
    }
    @Test fun invalidInstallmentNumbersDoNotAbortAndUnknownCardCycleStaysNull() {
        val name = "card-terms-invalid-31-32"
        helper.createDatabase(name, 31).use { db ->
            seedCard(db)
            seedPurchase(db, "purchase", "2026-06-25T12:00:00Z", 1)
            seedInstallment(db, "zero", "purchase", 0)
            seedInstallment(db, "negative", "purchase", -4)
            seedInstallment(db, "valid", "purchase", 1)
            seedPurchase(db, "no-card", "2026-06-25T12:00:00Z", 1, card = null)
            seedInstallment(db, "unknown-cycle", "no-card", 1)
        }
        helper.runMigrationsAndValidate(name, 32, true, MIGRATION_31_32).use { db ->
            db.query("SELECT id,billing_closing_date FROM credit_installments").use { rows ->
                var count = 0
                while (rows.moveToNext()) {
                    count++
                    if (rows.getString(0) == "unknown-cycle") assertTrue(rows.isNull(1))
                    else assertEquals(LocalDate.of(2026, 6, 26).toEpochDay(), rows.getLong(1))
                }
                assertEquals(4, count)
            }
        }
    }
    @Test fun combinedUpgradeAndRemoteIdentityAdoptionKeepPaymentAllocations() {
        val name = "card-cycle-30-32"
        helper.createDatabase(name, 30).use { db ->
            seedCard(db); seedPurchase(db, "purchase", "2026-06-25T12:00:00Z", 1)
            seedInstallment(db, "local-id", "purchase", 1)
            db.execSQL("INSERT INTO credit_payment_allocations (id,user_id,payment_transaction_id,installment_id,allocated_minor,created_at) VALUES ('a','owner','purchase','local-id',100,1)")
        }
        helper.runMigrationsAndValidate(name, 32, true, MIGRATION_30_31, MIGRATION_31_32).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("UPDATE credit_installments SET id='remote-id' WHERE id='local-id'")
            db.query("SELECT installment_id FROM credit_payment_allocations").use {
                assertTrue(it.moveToFirst()); assertEquals("remote-id", it.getString(0))
            }
            db.query("SELECT purchase_type,installments_count FROM credit_installments").use {
                assertTrue(it.moveToFirst()); assertEquals("DIRECT", it.getString(0)); assertEquals(1, it.getInt(1))
            }
        }
    }
    private fun seedCard(db: SupportSQLiteDatabase) {
        db.execSQL("""INSERT INTO cards (id,user_id,creation_operation_id,type,currency,network,issuer,last_four_digits,
            credit_limit_minor_units,billing_day,due_day,is_archived,remote_revision,created_at,updated_at)
            VALUES ('card','owner','op','CREDIT','PEN','VISA','BCP','1234',500000,26,19,0,1,1,1)""")
    }
    private fun seedPurchase(db: SupportSQLiteDatabase, id: String, occurred: String, count: Int, card: String? = "card") {
        db.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,
            created_at,updated_at,card_id,operation_kind,installment_count) VALUES (?,'owner','EXPENSE',120000,'PEN',?,'ACTIVE','PENDING',1,1,?,'CARD_PURCHASE',?)""",
            arrayOf<Any?>(id, Instant.parse(occurred).toEpochMilli(), card, count))
    }
    private fun seedInstallment(db: SupportSQLiteDatabase, id: String, transaction: String, number: Int) {
        db.execSQL("""INSERT INTO credit_installments (id,user_id,transaction_id,installment_number,due_date,principal_minor,
            interest_minor,status,revision,created_at,updated_at) VALUES (?,'owner',?,?,20000,40000,0,'PENDING',1,1,1)""",
            arrayOf<Any>(id, transaction, number))
    }
}
