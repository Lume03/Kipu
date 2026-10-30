package com.kipu.app.feature.statements

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BcpVisaStatementParserTest {
    private fun word(text: String, x: Float, y: Float) = StatementWord(text, x, y, x + text.length * 4, y + 6)
    private fun row(y: Float, vararg cells: Pair<Float, String>) = cells.map { word(it.second, it.first, y) }
    private fun header() = row(20f, 45f to "Estado de Cuenta Tarjeta VISA", 400f to "VíaBCP") +
        row(40f, 360f to "Número de tarjeta", 465f to "Del", 512f to "Al (día de cierre)") +
        row(52f, 360f to "4000-00XX-XXXX-9876", 465f to "20/12/25", 512f to "19/01/26") +
        row(70f, 465f to "Fecha límite de pago") + row(82f, 482f to "05/02/26")
    private fun columns(y: Float) = row(y, 48f to "Fecha de proceso", 82f to "Fecha de consumo", 270f to "Descripción", 410f to "Tipo de Operación", 471f to "Soles", 527f to "Dólares")
    private fun movement(y: Float, description: String, type: String, pen: String = "", usd: String = "") =
        row(y, 47f to "02Ene", 92f to "30Dic", 136f to description, 251f to "CIUDAD", 321f to "PE", 410f to type, 490f to pen, 550f to usd)
    private fun pages(extra: List<StatementWord> = emptyList(), subtotal: String = "115.00", total: String = "118.00") = listOf(
        StatementPage(0, 595f, 842f, header() + columns(110f) + row(125f, 136f to "SALDO ANTERIOR", 490f to "100.00", 550f to "0.00") +
            movement(140f, "TIENDA NUBE", "CONSUMO", "50.00", "7.00") + movement(150f, "PAGO BANCA MOVIL", "PAGO", "20.00-") +
            movement(160f, "PAGO CON PUNTOS PREMIO", "PAGO", "5.00-") + movement(170f, "TIENDA RETORNO", "DEVOLUCION", "10.00-") + extra),
        StatementPage(1, 595f, 842f, columns(110f) + row(135f, 136f to "INTERESES", 490f to "1.00") +
            row(145f, 136f to "SUB TOTAL", 490f to subtotal, 550f to "7.00") +
            row(155f, 136f to "GASTO MENSUAL POR SEGURO DE DESGRAVAMEN", 490f to "2.00") +
            row(165f, 136f to "MONTO TOTAL FACTURADO", 490f to total, 550f to "7.00") +
            row(185f, 136f to "DETALLE PLAN CUOTAS SOLES") + row(195f, 251f to "Compras", 291f to "Nro.Cuota") +
            row(210f, 47f to "03Ene", 92f to "29Dic", 136f to "TIENDA LUNA", 251f to "240.00", 291f to "01/03", 336f to "10.00", 390f to "80.00", 445f to "2.00", 490f to "82.00") +
            row(220f, 47f to "03Ene", 92f to "29Dic", 136f to "TIENDA SOL", 251f to "180.00", 291f to "02/03", 490f to "60.00") + row(230f, 136f to "TOTAL SOLES")),
        StatementPage(2, 595f, 842f, emptyList()),
    )
    private fun parse(pages: List<StatementPage>) = (BcpVisaStatementParser().parse(pages) as StatementParseResult.Parsed).statement

    @Test fun `parses two currencies and reconciles across pages`() {
        val result = parse(pages())
        assertEquals("9876", result.cardLastFour)
        assertEquals(LocalDate.of(2025, 12, 20), result.cycleStart)
        assertEquals(LocalDate.of(2026, 2, 5), result.dueDate)
        assertTrue(result.reconciliation.values.all { it.matches })
        assertEquals(11800L, result.totals.getValue(Currency.PEN).billedMinor)
        assertEquals(700L, result.totals.getValue(Currency.USD).billedMinor)
        val purchase = result.lines.first { it.kind == StatementLineKind.PURCHASE }
        assertEquals(LocalDate.of(2025, 12, 30), purchase.occurredDate)
        assertEquals(LocalDate.of(2026, 1, 2), purchase.processDate)
        assertEquals("TIENDA NUBE", purchase.description)
        assertTrue(result.lines.filter { it.kind in setOf(StatementLineKind.CARD_PAYMENT, StatementLineKind.POINTS_REDEMPTION) }.all { !it.defaultSelected && it.amountMinor > 0 })
        assertEquals(1, result.lines.count { it.kind == StatementLineKind.REFUND })
        val installment = result.lines.single { it.kind == StatementLineKind.INSTALLMENT_PURCHASE }
        assertEquals(24000L, installment.amountMinor)
        assertEquals(3, installment.installmentCount)
        assertEquals(24000L, installment.installmentTotalMinor)
        assertTrue(installment.defaultSelected)
        assertEquals(2, result.lines.count { it.kind in setOf(StatementLineKind.INTEREST, StatementLineKind.INSURANCE) })
    }

    @Test fun `monthly charge reconciles but is excluded from candidates`() {
        val result = parse(pages(movement(180f, "CUOTA DEL MES", "CARGO", "9.00"), "124.00", "127.00"))
        assertTrue(result.reconciliation.values.all { it.matches })
        assertFalse(result.lines.any { it.kind == StatementLineKind.INSTALLMENT_MONTHLY_CHARGE })
    }

    @Test fun `mismatches are exposed rather than silently accepted`() {
        val result = parse(pages(subtotal = "116.00", total = "119.00"))
        assertEquals(-100L, result.reconciliation.getValue(Currency.PEN).subtotalDifferenceMinor)
        assertFalse(result.reconciliation.getValue(Currency.PEN).matches)
    }

    @Test fun `fingerprints are stable and distinguish repeated transactions`() {
        val input = pages(movement(180f, "TIENDA NUBE", "CONSUMO", "50.00"), "165.00", "168.00")
        val first = parse(input)
        assertEquals(first.fingerprint, parse(input).fingerprint)
        assertEquals(first.lines.map { it.fingerprint }, parse(input).lines.map { it.fingerprint })
        assertEquals(first.lines.size, first.lines.map { it.fingerprint }.distinct().size)
    }

    @Test fun `uses header anchors when layout is translated and scaled`() {
        val translated = pages().map { page -> page.copy(width = 800f, words = page.words.map { it.copy(x0 = it.x0 * 1.1f + 15, x1 = it.x1 * 1.1f + 15) }) }
        assertTrue(parse(translated).reconciliation.values.all { it.matches })
    }

    @Test fun `unknown operations remain unselected and missing totals never reconcile`() {
        val result = parse(pages(movement(180f, "AJUSTE REVISION", "OTRO", "4.00")))
        assertFalse(result.lines.single { it.kind == StatementLineKind.UNKNOWN }.defaultSelected)
        val stripped = pages().map { it.copy(words = it.words.filterNot { w -> w.text.contains("MONTO TOTAL") }) }
        assertTrue(parse(stripped).reconciliation.values.none { it.matches })
    }

    @Test fun `requires both visa statement and issuer evidence`() {
        val parser = BcpVisaStatementParser()
        assertTrue(parser.canParse(pages()))
        assertFalse(parser.canParse(pages().map { it.copy(words = it.words.filterNot { w -> w.text.contains("BCP") }) }))
        assertEquals(StatementParseResult.Unsupported, parser.parse(emptyList()))
    }

    @Test fun `invalid header dates return a safe parse failure`() {
        val invalid = pages().map { page -> page.copy(words = page.words.map { if (it.text == "20/12/25") it.copy(text = "40/12/25") else it }) }
        assertEquals(StatementParseResult.InvalidStatement, BcpVisaStatementParser().parse(invalid))
    }

    @Test fun `parses thousands separators and summary fees`() {
        val extra = movement(180f, "COMPRA HORIZONTE", "CONSUMO", "1,234.56")
        val input = pages(extra, "1,349.56", "1,355.56").map { page ->
            if (page.index == 1) page.copy(words = page.words + row(160f, 136f to "COMISION ANUAL POR MEMBRESIA", 490f to "3.00")) else page
        }
        val result = parse(input)
        assertEquals(123456L, result.lines.single { it.description == "COMPRA HORIZONTE" }.amountMinor)
        assertEquals(1, result.lines.count { it.kind == StatementLineKind.FEE })
        assertTrue(result.reconciliation.values.all { it.matches })
    }
}
