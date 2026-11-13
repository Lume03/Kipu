package com.kipu.app.feature.debts.presentation

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun debtDueDayLabel(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == -1L -> "Vencida hace 1 día"
        days < 0 -> "Vencida hace ${-days} días"
        days == 0L -> "Vence hoy"
        days == 1L -> "Vence mañana"
        else -> "Vence en $days días"
    }
}
