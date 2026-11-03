package com.kipu.app.feature.portability
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class PendingLocalChangesRoomTest {
    @Test fun countsEveryQueueIncludingFailuresAndFiltersOwner() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).allowMainThreadQueries().build()
        val owner = "owner"
        try {
        db.openHelper.writableDatabase.execSQL("INSERT INTO goals(id,user_id,name,target_minor,currency_code,status,revision,sync_state,created_at,updated_at) VALUES('goal_id','owner','Viaje',1000,'PEN','ACTIVE',1,'PENDING',1,1)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(operation_id,user_id,aggregate_type,contract_version,selection_revision,selection,selected_at,status,attempt_count,next_attempt_at,lease_until,last_error_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("operation_id",owner,"aggregate_type",1L,1L,"selection",1L,"PENDING",1L,1L,1L,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO profile_preference_outbox(operation_id,user_id,expected_revision,payload,status,attempt_count,next_attempt_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("operation_id",owner,1L,"payload","PENDING",1L,1L,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO instrument_sync_outbox(operation_id,user_id,command_type,aggregate_type,aggregate_id,predecessor_operation_id,expected_revision,contract_version,payload_json,payload_hash,state,attempt_count,next_attempt_at,lease_expires_at,error_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("operation_id",owner,"command_type","aggregate_type","aggregate_id",null,1L,1L,"payload_json","payload_hash","PENDING",1L,1L,1L,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO category_sync_outbox(operation_id,user_id,command_type,aggregate_type,aggregate_id,expected_revision,payload_json,payload_hash,state,attempt_count,next_attempt_at,error_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("operation_id",owner,"command_type","aggregate_type","aggregate_id",1L,"payload_json","payload_hash","PENDING",1L,1L,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO debt_command_outbox(user_id,operation_id,debt_id,command_type,request_hash,payload,state,attempt_count,next_attempt_at,lease_until,last_error_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(owner,"operation_id","debt_id","command_type","request_hash","payload","PENDING",1L,1L,1L,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO goal_command_outbox(user_id,operation_id,goal_id,command_type,request_hash,contract_version,expected_revision,payload,state,attempt_count,next_attempt_at,lease_until,last_error_code,result_payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(owner,"operation_id","goal_id","command_type","request_hash",1L,1L,"payload","PENDING",1L,1L,1L,null,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO movement_outbox(id,user_id,idempotency_key,aggregate_id,payload,contract_version,command_type,expected_revision,depends_on_command_id,revision_id,state,attempt_count,next_attempt_at,lease_until,last_error_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("id",owner,"idempotency_key","aggregate_id","payload",1L,"command_type",1L,null,null,"PENDING",1L,1L,1L,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO notification_sync_outbox(operation_id,user_id,notification_id,is_read,deleted_at,attempt_count,created_at) VALUES(?,?,?,?,?,?,?)", arrayOf<Any?>("operation_id",owner,"notification_id",1L,1L,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO recurrence_command_outbox(user_id,operation_id,recurrence_id,command_type,request_hash,contract_version,expected_revision,payload,state,attempt_count,next_attempt_at,lease_until,last_error_code,result_payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(owner,"operation_id","recurrence_id","command_type","request_hash",1L,1L,"payload","PENDING",1L,1L,1L,null,null,1L,1L))
        db.openHelper.writableDatabase.execSQL("INSERT INTO budget_sync_outbox(sequence,operation_id,user_id,command_type,aggregate_id,expected_revision,payload_json,state,attempt_count,error_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(1L,"operation_id",owner,"command_type","aggregate_id",1L,"payload_json","PENDING",1L,null,1L,1L))
            val pending = PendingLocalChanges(db)
            val result = pending(owner)
            assertEquals(PendingLocalChanges.queues.size, result.total)
            assertTrue(result.byModule.values.all { it == 1 })
            assertEquals(0, pending("other").total)
            db.openHelper.writableDatabase.execSQL("UPDATE movement_outbox SET state='CONFLICT'")
            assertEquals(result.total, pending(owner).total)
            db.openHelper.writableDatabase.execSQL("UPDATE movement_outbox SET state='SYNCED'")
            assertEquals(result.total - 1, pending(owner).total)
            val export = PortabilityStore(db).json(owner)
            assertFalse(export.contains("outbox"))
        } finally { db.close() }
    }
}
