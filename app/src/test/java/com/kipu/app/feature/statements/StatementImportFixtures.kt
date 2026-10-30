package com.kipu.app.feature.statements

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import java.time.LocalDate

internal val statementDate: LocalDate = LocalDate.of(2026, 9, 20)
internal fun importLine(
    fingerprint: String = "line", currency: Currency = Currency.PEN,
    kind: StatementLineKind = StatementLineKind.PURCHASE,
) = StatementLine(kind, statementDate, statementDate, "Synthetic purchase", 1000, currency,
    defaultSelected = true, fingerprint = fingerprint)

internal fun parsedStatement(lines: List<StatementLine> = listOf(importLine()), fingerprint: String = "statement") = ParsedStatement(
    "BCP", "1234", statementDate.minusDays(20), statementDate, statementDate.plusDays(10), lines,
    emptyMap(), mapOf(Currency.PEN to StatementReconciliation(0, 0)), fingerprint,
)

internal fun storedStatement(status: StatementImportStatus = StatementImportStatus.PENDING) = StatementImport(
    "import", "owner", "statement", "BCP", "1234", statementDate.minusDays(20), statementDate,
    statementDate.plusDays(10), true, null, null, status, 1000, 604801000,
)
