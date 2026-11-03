package com.kipu.app.feature.portability

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kipu.app.core.database.KipuDatabase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class PortabilityStore @Inject constructor(private val database: KipuDatabase) {
    suspend fun json(owner: String): String = withContext(Dispatchers.IO) {
        database.withTransaction {
            PortableJson.encode(owner, PortableJson.columns.mapValues { (table, columns) ->
                database.openHelper.readableDatabase.query(SimpleSQLiteQuery(
                    "SELECT $columns FROM $table WHERE user_id = ?", arrayOf(owner)
                )).use { cursor -> buildList {
                    while (cursor.moveToNext()) add(buildJsonObject {
                        cursor.columnNames.forEachIndexed { index, name ->
                            put(name, when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JsonNull
                                Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(index))
                                else -> JsonPrimitive(cursor.getString(index))
                            })
                        }
                    })
                } }
            })
        }
    }
    suspend fun csv(owner: String): String = withContext(Dispatchers.IO) {
        MovementExport.csv(owner, database.movementDao().queryTransactions(SimpleSQLiteQuery("SELECT * FROM transactions WHERE user_id = ?", arrayOf(owner))))
    }
}
