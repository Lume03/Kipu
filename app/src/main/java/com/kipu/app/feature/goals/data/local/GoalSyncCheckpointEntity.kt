package com.kipu.app.feature.goals.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "goal_sync_checkpoints", primaryKeys = ["user_id"])
data class GoalSyncCheckpointEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
