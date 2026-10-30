package com.kipu.app.feature.goals.data.local

import androidx.room.ColumnInfo

data class GoalBackingAccountRow(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "confirmed_balance_minor") val confirmedBalanceMinor: Long,
    @ColumnInfo(name = "reserved_minor") val reservedMinor: Long,
    @ColumnInfo(name = "account_type") val accountType: String = "BANK",
    @ColumnInfo(name = "preset_id") val presetId: String? = null,
) {
    val availableMinor: Long get() = Math.subtractExact(confirmedBalanceMinor, reservedMinor)
    val shortfallMinor: Long
        get() = if (reservedMinor > confirmedBalanceMinor) Math.subtractExact(reservedMinor, confirmedBalanceMinor) else 0L
}
