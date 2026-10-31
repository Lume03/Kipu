package com.kipu.app.testing

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.debts.domain.model.*
import java.time.Instant
import java.time.LocalDate

fun cardInstallmentFixture(
    id: String = "i1", due: LocalDate = LocalDate.parse("2026-10-15"),
    remaining: Long = 1100L, paid: Long = 0L, currency: Currency = Currency.PEN,
    settled: Instant? = null,
) = CardInstallment(id, CardId("61000000-0000-4000-8000-000000000010"), "Visa", "purchase", "Tienda", null,
    1, 1, due, due.minusDays(20), Money(1000, currency), Money(100, currency), Money(1100, currency),
    Money(paid, currency), Money(0, currency), Money(remaining, currency), PurchaseTerms.interestFree(),
    CardInstallmentStatus.PENDING, settled)

fun debtFixture(id: String = "debt", remaining: Long = 3000L, currency: String = "PEN",
    due: LocalDate? = LocalDate.parse("2026-10-15"), type: DebtObligationType = DebtObligationType.PAYABLE,
    status: DebtLifecycleStatus = DebtLifecycleStatus.ACTIVE,
) = DebtSummary(id, "owner", type, "Persona $id", 3000, remaining, currency,
    LocalDate.parse("2026-01-01"), due, null, null, status, "SYNCED", 1)
