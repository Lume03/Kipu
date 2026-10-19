package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test
    fun freshVersionTwentyThreeDatabaseCreatesRecurrenceTablesWithoutOccurrences() {
        helper.createDatabase("recurrence-fresh-v23", 23).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recurrences'"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recurrence_command_outbox'"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recurrence_sync_checkpoints'"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recurrence_occurrences'"))
        }
    }

    @Test
    fun versionTwentyTwoToTwentyThreePreservesFinancialAndQueuedDataAndAddsRecurrenceStorage() {
        val name = "recurrence-v22-to-v23"
        helper.createDatabase(name, 22).use { db ->
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
            db.execSQL(
                """INSERT INTO goal_command_outbox (
                    user_id,operation_id,goal_id,command_type,request_hash,contract_version,expected_revision,
                    payload,state,attempt_count,next_attempt_at,lease_until,last_error_code,result_payload,created_at,updated_at
                ) VALUES ('user-1','goal-op','goal-1','CREATE_GOAL','hash',1,NULL,'{}','PENDING',2,4000,NULL,NULL,NULL,3000,3000)""",
            )
        }

        helper.runMigrationsAndValidate(name, 23, true, MIGRATION_21_22, MIGRATION_22_23).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM accounts WHERE id='cash-1'"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE id='movement-1' AND amount_minor=1250"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM ledger_entries WHERE id='ledger-1' AND signed_amount_minor=-1250"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM goal_command_outbox WHERE operation_id='goal-op' AND attempt_count=2"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM recurrences"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM recurrence_command_outbox"))

            db.execSQL(
                """INSERT INTO recurrences (
                    id,user_id,template_transaction,amount_mode,frequency,interval_count,custom_interval_unit,
                    start_date,end_date,next_due_date,reminder_lead_days,status,is_active,revision,deleted_at,
                    sync_state,created_at,updated_at
                ) VALUES ('rec-1','user-1','{}','APPROXIMATE','MONTHLY',1,NULL,'2026-01-31',NULL,
                    '2026-02-28',2,'ACTIVE',1,1,NULL,'PENDING',5000,5000)""",
            )
            db.execSQL(
                """INSERT INTO recurrence_command_outbox (
                    user_id,operation_id,recurrence_id,command_type,request_hash,contract_version,expected_revision,
                    payload,state,attempt_count,next_attempt_at,lease_until,last_error_code,result_payload,created_at,updated_at
                ) VALUES ('user-1','rec-op','rec-1','CREATE_RECURRENCE','digest',1,NULL,'{}','PENDING',0,NULL,NULL,NULL,NULL,5000,5000)""",
            )
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM recurrences WHERE id='rec-1' AND is_active=1"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM recurrence_command_outbox WHERE operation_id='rec-op'"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recurrence_occurrences'"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
