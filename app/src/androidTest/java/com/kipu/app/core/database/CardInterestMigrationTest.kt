package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.accounts.data.local.CREDIT_INSTALLMENT_BALANCES_SQL
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardInterestMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test fun migrationReopensInterestDebtAndReplacesCapturedTriggerSql() {
        val name = "card-interest-40-41"
        helper.createDatabase(name, 40).use { db ->
            db.execSQL("""INSERT INTO cards (id,user_id,creation_operation_id,type,currency,network,issuer,last_four_digits,
                credit_limit_minor_units,billing_day,due_day,is_archived,remote_revision,created_at,updated_at)
                VALUES ('card','owner','op','CREDIT','PEN','VISA','BCP','1234',500000,26,19,0,1,1,1)""")
            for (id in listOf("purchase", "payment", "free", "draft")) {
                db.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,
                    created_at,updated_at,card_id,operation_kind,installment_count)
                    VALUES (?,'owner',?,10000,'PEN',1,?,'SYNCED',1,1,'card',?,1)""",
                    arrayOf(id, if (id == "payment") "TRANSFER" else "EXPENSE",
                        if (id == "draft") "DRAFT" else "ACTIVE",
                        if (id == "payment") "CARD_PAYMENT" else "CARD_PURCHASE"))
            }
            for (id in listOf("purchase", "free", "draft")) {
                db.execSQL("""INSERT INTO credit_installments (id,user_id,transaction_id,installment_number,due_date,
                    principal_minor,interest_minor,status,revision,created_at,updated_at,financing)
                    VALUES (?,'owner',?,1,20000,10000,2000,'PAID',1,1,1,?)""",
                    arrayOf(id, id, if (id == "free") "INTEREST_FREE" else "WITH_INTEREST"))
            }
            db.execSQL("INSERT INTO credit_payment_allocations (id,user_id,payment_transaction_id,installment_id,allocated_minor,created_at) VALUES ('a','owner','payment','purchase',10000,1)")
            // Simulate SQL persisted by a pre-41 database, regardless of today's callback.
            val legacy = com.kipu.app.feature.accounts.data.local.REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL
                .replace(" + CASE WHEN i.financing = 'WITH_INTEREST' THEN i.interest_minor ELSE 0 END", "")
                .replace(":userId", "NEW.user_id")
            db.execSQL("CREATE TRIGGER credit_installment_status_credit_payment_allocations_update AFTER UPDATE ON credit_payment_allocations BEGIN $legacy; END")
        }
        helper.runMigrationsAndValidate(name, 41, true, MIGRATION_40_41).use { db ->
            assertStatus(db, "purchase", "PARTIAL")
            assertBalance(db, "purchase", 2000)
            assertBalance(db, "free", 10000)
            db.query(CREDIT_INSTALLMENT_BALANCES_SQL + "SELECT COUNT(*) FROM balances WHERE id='draft'").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            db.execSQL("UPDATE credit_payment_allocations SET allocated_minor=11000 WHERE id='a'")
            assertStatus(db, "purchase", "PARTIAL")
            assertBalance(db, "purchase", 1000)
            db.execSQL("UPDATE credit_payment_allocations SET allocated_minor=12000 WHERE id='a'")
            assertStatus(db, "purchase", "PAID")
            assertBalance(db, "purchase", 0)
            db.execSQL("UPDATE transactions SET status='VOIDED' WHERE id='payment'")
            assertStatus(db, "purchase", "PENDING")
            assertBalance(db, "purchase", 12000)
        }
    }

    private fun assertStatus(db: SupportSQLiteDatabase, id: String, expected: String) {
        db.query("SELECT status FROM credit_installments WHERE id=?", arrayOf(id)).use {
            assertTrue(it.moveToFirst()); assertEquals(expected, it.getString(0))
        }
    }
    private fun assertBalance(db: SupportSQLiteDatabase, id: String, expected: Long) {
        db.query(CREDIT_INSTALLMENT_BALANCES_SQL + "SELECT outstanding_minor FROM balances WHERE id=?", arrayOf(id)).use {
            assertTrue(it.moveToFirst()); assertEquals(expected, it.getLong(0))
        }
    }
}
