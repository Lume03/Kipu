package com.kipu.app.feature.statements

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BcpSavingsStatementParserTest {
    private fun row(y: Float, vararg cells: Pair<Float, String>) = cells.map { (x, text) -> StatementWord(text, x, y, x + text.length * 4, y + 6) }
    private fun pages(finalBalance: String = "105.00") = listOf(StatementPage(0, 595f, 842f,
        row(20f, 10f to "Estado de Cuenta de Ahorros Cuenta Digital BCP") +
        row(30f, 10f to "PEREZ DIAZ JUAN CARLOS", 350f to "CODIGO DE CUENTA", 500f to "MONEDA") +
        row(40f, 350f to "123-45678900-0-11", 500f to "SOLES") +
        row(50f, 350f to "DEL", 390f to "01/01/26", 460f to "AL", 490f to "31/01/26") +
        row(70f, 33f to "FECHA PROC.", 79f to "FECHA VALOR", 181f to "DESCRIPCION", 343f to "CARGOS / DEBE", 485f to "ABONOS / HABER") +
        row(80f, 80f to "SALDO ANTERIOR", 520f to "100.00") +
        row(90f, 33f to "01ENE", 79f to "30DIC", 140f to "Yape Juan Per", 390f to "10.00") +
        row(100f, 33f to "02ENE", 79f to "02ENE", 140f to "TRANSF.BCO.TEST", 520f to "15.00") +
        row(110f, 33f to "03ENE", 79f to "03ENE", 140f to "MANT CUENTA", 390f to "0.00") +
        row(130f, 390f to "10.00", 520f to "15.00") + row(137f, 140f to "TOTAL MOVIMIENTO") +
        row(150f, 520f to finalBalance) + row(157f, 140f to "SALDO")))
    @Test fun `parses columns dates and vertically offset totals`() {
        val parser = BcpSavingsStatementParser()
        val input = pages()
        assertTrue(parser.canParse(input))
        assertFalse(BcpVisaStatementParser().canParse(input))
        val s = (parser.parse(input) as StatementParseResult.Parsed).statement
        assertEquals(StatementSource.DEPOSIT_ACCOUNT, s.source)
        assertEquals("", s.cardLastFour)
        assertEquals("0011", s.accountReference)
        assertEquals(2, s.lines.size)
        assertEquals(listOf(StatementLineKind.ACCOUNT_DEBIT, StatementLineKind.ACCOUNT_CREDIT), s.lines.map { it.kind })
        assertEquals(LocalDate.of(2025, 12, 30), s.lines.first().occurredDate)
        assertTrue(s.lines.none { it.defaultSelected })
        assertTrue(s.lines.all { it.possibleOwnTransfer })
        assertTrue(s.reconciliation.getValue(Currency.PEN).matches)
    }
    @Test fun `ordinary account lines have no own transfer marker`() {
        val input = pages().map { page -> page.copy(words = page.words.map { word ->
            word.copy(text = when (word.text) { "Yape Juan Per" -> "TIENDA"; "TRANSF.BCO.TEST" -> "SUELDO"; else -> word.text })
        }) }
        val statement = (BcpSavingsStatementParser().parse(input) as StatementParseResult.Parsed).statement
        assertTrue(statement.lines.none { it.possibleOwnTransfer })
        assertTrue(statement.lines.all { it.defaultSelected })
    }

    @Test fun `visa headers do not match savings`() {
        val visa = listOf(StatementPage(0, 595f, 842f, row(20f, 10f to "Estado de Cuenta Tarjeta VISA", 400f to "BCP")))
        assertTrue(BcpVisaStatementParser().canParse(visa))
        assertFalse(BcpSavingsStatementParser().canParse(visa))
    }
    @Test fun `reports balance mismatch`() {
        val s = (BcpSavingsStatementParser().parse(pages("106.00")) as StatementParseResult.Parsed).statement
        assertFalse(s.reconciliation.getValue(Currency.PEN).matches)
    }
}
