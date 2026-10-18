package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test
    fun versionTwentyOneFinancialHistorySurvivesAndGoalTablesSupportVirtualAndLegacyRows() {
        val name = "goals-v21-to-v22"
        helper.createDatabase(name, 21).use { db ->
            db.execSQL(
                """INSERT INTO accounts (
                    id,user_id,creation_operation_id,alias,type,currency,initial_balance_minor_units,
                    opened_at,is_archived,remote_revision,created_at,updated_at
                ) VALUES ('cash-1','user-1','account-op','Ahorros','SAVINGS','PEN',100000,1000,0,1,1000,1000)""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,source_account_id,operation_kind,
                    occurred_at,status,sync_status,created_at,updated_at
                ) VALUES ('movement-1','user-1','EXPENSE',1250,'PEN','cash-1','STANDARD',2000,
                    'ACTIVE','SYNCED',2000,2000)""",
            )
            db.execSQL(
                """INSERT INTO ledger_entries (
                    id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at
                ) VALUES ('ledger-1','user-1','movement-1','cash-1','SOURCE',-1250,'PEN',2000)""",
            )
        }

        helper.runMigrationsAndValidate(name, 22, true, MIGRATION_21_22).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM accounts WHERE id='cash-1'"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE id='movement-1' AND amount_minor=1250"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM ledger_entries WHERE id='ledger-1' AND signed_amount_minor=-1250"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM goals"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM goal_events"))

            db.execSQL(
                """INSERT INTO goals (
                    id,user_id,name,target_minor,currency_code,linked_account_id,status,revision,sync_state,created_at,updated_at
                ) VALUES ('goal-1','user-1','Viaje',50000,'PEN',NULL,'ACTIVE',1,'PENDING',3000,3000)""",
            )
            db.execSQL(
                """INSERT INTO goal_events (
                    id,user_id,goal_id,event_type,delta_minor,currency_code,backing_account_id,
                    operation_id,occurred_at,created_at
                ) VALUES ('event-legacy','user-1','goal-1','ALLOCATION',5000,'PEN',NULL,
                    'legacy-operation',4000,4000)""",
            )
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM goals WHERE id='goal-1' AND linked_account_id IS NULL"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM goal_events WHERE id='event-legacy' AND backing_account_id IS NULL"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
