package com.kipu.app.feature.statements.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface StatementImportMatchingDao {
    @Query("""SELECT EXISTS(SELECT 1 FROM transactions WHERE user_id=:ownerId AND card_id=:cardId
        AND amount_minor=:amountMinor AND currency_code=:currency AND status='ACTIVE'
        AND occurred_at>=:fromMillis AND occurred_at<:untilMillis)
        OR EXISTS(SELECT 1 FROM financial_movements WHERE user_id=:ownerId AND card_id=:cardId
        AND ABS(amount_minor_units)=:amountMinor AND currency=:currency AND status='POSTED'
        AND effective_at>=:fromMicros AND effective_at<:untilMicros)""")
    suspend fun hasDuplicate(ownerId: String, cardId: String, amountMinor: Long, currency: String,
        fromMillis: Long, untilMillis: Long, fromMicros: Long, untilMicros: Long): Boolean
}
