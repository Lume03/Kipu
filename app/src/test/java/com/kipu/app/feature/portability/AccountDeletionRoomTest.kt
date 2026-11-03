package com.kipu.app.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AccountDeletionRoomTest {
    @Test fun cancellationIsReadOnlyAndCleanupRemovesBothOwnerColumnsWithOtherUserIntact() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val sql = db.openHelper.writableDatabase
            for (owner in listOf("owner", "other")) {
                sql.execSQL("INSERT INTO goals(id,user_id,name,target_minor,currency_code,status,revision,sync_state,created_at,updated_at) VALUES(?,?,'Meta',1000,'PEN','ACTIVE',1,'PENDING',1,1)", arrayOf("goal-$owner",owner))
                sql.execSQL("INSERT INTO capture_evidence(owner_id,source,event_id,root,reference,separated) VALUES(?,'YAPE',?,?,'ref',0)", arrayOf(owner,"event-$owner","root-$owner"))
                sql.execSQL("INSERT INTO goal_command_outbox(user_id,operation_id,goal_id,command_type,request_hash,contract_version,expected_revision,payload,state,attempt_count,next_attempt_at,created_at,updated_at) VALUES(?,?,?,'CREATE','hash',1,1,'{}','PENDING',0,0,1,1)", arrayOf(owner,"op-$owner","goal-$owner"))
            }
            fun snapshot() = listOf("goals" to "user_id", "capture_evidence" to "owner_id", "goal_command_outbox" to "user_id").associate { (t,c) ->
                t to sql.query("SELECT $c FROM $t ORDER BY $c").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            }
            sql.execSQL("INSERT INTO transactions(id,user_id,type,amount_minor,currency_code,occurred_at,status,revision,sync_status,created_at,updated_at,acknowledged_revision) VALUES('tx','owner','EXPENSE',100,'PEN',1,'ACTIVE',1,'SYNCED',1,1,1)")
            sql.execSQL("INSERT INTO transaction_revisions(user_id,revision_id,transaction_id,command_id,command_type,base_revision,local_revision,new_payload,provenance,created_at) VALUES('owner','rev','tx','cmd','CREATE',0,1,'{}','LOCAL',1)")
            for (entry in listOf("entry", "reversal")) sql.execSQL("INSERT INTO ledger_entries(id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at) VALUES(?,'owner','tx','cash','SOURCE',100,'PEN',1)", arrayOf(entry))
            sql.execSQL("INSERT INTO movement_ledger_effects(user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id) VALUES('owner','cmd',0,'tx','rev','entry')")
            sql.execSQL("INSERT INTO movement_ledger_effects(user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id,reverses_command_id,reverses_effect_ordinal) VALUES('owner','reverse',0,'tx','rev','reversal','cmd',0)")
            val proposal = com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity("owner","proposal","tx","cmd",0,"rev","{}","rev",null,"UNRESOLVED",1,1)
            db.movementDao().insertHydratedConflictProposal(proposal)
            db.movementDao().insertHydratedConflictProposal(proposal.copy(proposalId="retry"))
            assertEquals(1,db.movementDao().getConflictProposals("owner","tx").size)
            val before = snapshot()
            val store = object : DeletionRequestStore {
                override suspend fun read(owner: String): DeletionRequest? = null
                override suspend fun write(request: DeletionRequest) { error("Cancel must not persist a request") }
            }
            val gateway = object : AccountDeletionGateway {
                override suspend fun reauthenticate(owner: String, password: String) {}
                override suspend fun delete(request: DeletionRequest): DeletionRemote = error("Cancel must not call Edge")
                override fun clearReauthentication() {}
            }
            val deletion = AccountDeletion(store,gateway,object : AccountDeletionCleanup {
                override suspend fun clean(owner: String) { DeleteLocalAccountRows(db)(owner) }
            }, { "owner" }, { PendingLocalChanges(db)(it).total })
            deletion.load(); deletion.open(); deletion.cancel()
            assertEquals(before,snapshot())
            DeleteLocalAccountRows(db)("owner")
            assertTrue(snapshot().values.all { it == listOf("other") })
            // Retry local cleanup is also idempotent.
            DeleteLocalAccountRows(db)("owner")
            assertTrue(snapshot().values.all { it == listOf("other") })
        } finally { db.close() }
    }
}
