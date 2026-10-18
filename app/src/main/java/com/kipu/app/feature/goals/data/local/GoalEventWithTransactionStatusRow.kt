package com.kipu.app.feature.goals.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class GoalEventWithTransactionStatusRow(
    @Embedded val event: GoalEventEntity,
    @ColumnInfo(name = "linked_transaction_status") val linkedTransactionStatus: String?,
)
