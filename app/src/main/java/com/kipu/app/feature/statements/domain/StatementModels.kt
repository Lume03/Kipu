package com.kipu.app.feature.statements.domain

import android.net.Uri
import com.kipu.app.core.finance.domain.model.Currency
import java.time.LocalDate

data class StatementWord(val text: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

data class StatementPage(val index: Int, val width: Float, val height: Float, val words: List<StatementWord>)

sealed interface StatementReadResult {
    data object PasswordRequired : StatementReadResult
    data object WrongPassword : StatementReadResult
    data object NoExtractableText : StatementReadResult
    data object Unreadable : StatementReadResult
    data class Opened(val pages: List<StatementPage>) : StatementReadResult
}

interface StatementDocumentReader {
    suspend fun read(uri: Uri, password: CharArray? = null): StatementReadResult
}

enum class StatementLineKind {
    PURCHASE, INSTALLMENT_PURCHASE, CARD_PAYMENT, POINTS_REDEMPTION, REFUND,
    INTEREST, INSURANCE, FEE, INSTALLMENT_MONTHLY_CHARGE, UNKNOWN,
}

data class StatementLine(
    val kind: StatementLineKind,
    val processDate: LocalDate,
    val occurredDate: LocalDate,
    val description: String,
    val amountMinor: Long,
    val currency: Currency,
    val installmentNumber: Int? = null,
    val installmentCount: Int? = null,
    val installmentTotalMinor: Long? = null,
    val defaultSelected: Boolean,
    val fingerprint: String,
) {
    init {
        require(amountMinor > 0)
    }
}

data class StatementCurrencyTotals(
    val previousBalanceMinor: Long?,
    val subtotalMinor: Long?,
    val summaryChargesMinor: Long,
    val billedMinor: Long?,
)

data class StatementReconciliation(
    val subtotalDifferenceMinor: Long?,
    val billedDifferenceMinor: Long?,
) {
    val matches: Boolean get() = subtotalDifferenceMinor == 0L && billedDifferenceMinor == 0L
}

data class ParsedStatement(
    val issuer: String,
    val cardLastFour: String,
    val cycleStart: LocalDate,
    val cycleEnd: LocalDate,
    val dueDate: LocalDate?,
    val lines: List<StatementLine>,
    val totals: Map<Currency, StatementCurrencyTotals>,
    val reconciliation: Map<Currency, StatementReconciliation>,
    val fingerprint: String,
)

sealed interface StatementParseResult {
    data class Parsed(val statement: ParsedStatement) : StatementParseResult
    data object Unsupported : StatementParseResult
    data object InvalidStatement : StatementParseResult
}

interface StatementParser {
    fun canParse(pages: List<StatementPage>): Boolean
    fun parse(pages: List<StatementPage>): StatementParseResult
}
